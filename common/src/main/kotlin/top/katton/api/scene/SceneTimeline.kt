package top.katton.api.scene

/**
 * %en
 * A reusable scene definition. Factories run once when their track starts.
 * %zh
 * 可复用演出定义；每条轨道开始时只执行一次工厂。
 */
@ConsistentCopyVisibility
data class SceneTrack
internal constructor(
    val startTick: Int,
    val durationTicks: Int,
    internal val factory: (SceneContext) -> SceneEffect,
)

class SceneDefinition
internal constructor(val tracks: List<SceneTrack>, val durationTicks: Int, val repeats: Int)

/**
 * %en
 * Sequential by default; use parallel or at for overlapping tracks.
 * %zh
 * 默认顺序执行；parallel 和 at 可创建重叠轨道。
 */
class SceneBuilder internal constructor() {
    private val tracks = mutableListOf<SceneTrack>()
    private var cursor = 0
    private var length = 0
    var repeats: Int = 1
        set(value) {
            require(value in 1..1000)
            field = value
        }

    fun effect(effect: SceneEffect) = effect(effect.durationTicks) { effect }

    fun effect(durationTicks: Int, factory: (SceneContext) -> SceneEffect) {
        durationTicks.requireDuration()
        require(tracks.size < 4096 && cursor.toLong() + durationTicks <= MAX_SCENE_TICKS)
        tracks += SceneTrack(cursor, durationTicks, factory)
        cursor += durationTicks
        length = maxOf(length, cursor)
    }

    fun waitTicks(ticks: Int) {
        require(ticks >= 0 && cursor.toLong() + ticks <= MAX_SCENE_TICKS)
        cursor += ticks
        length = maxOf(length, cursor)
    }

    fun at(tick: Int, block: SceneBuilder.() -> Unit) {
        require(tick in 0..MAX_SCENE_TICKS)
        val child = SceneBuilder().apply(block).build()
        require(
            child.repeats == 1 &&
                tracks.size + child.tracks.size <= 4096 &&
                tick.toLong() + child.durationTicks <= MAX_SCENE_TICKS
        )
        tracks += child.tracks.map { it.copy(startTick = it.startTick + tick) }
        length = maxOf(length, tick + child.durationTicks)
    }

    fun sequential(block: SceneBuilder.() -> Unit) {
        val start = cursor
        val child = SceneBuilder().apply(block).build()
        require(child.repeats == 1) { "Set repeats on the root scene, not on nested blocks" }
        at(start) {
            child.tracks.forEach { track ->
                at(track.startTick) { effect(track.durationTicks, track.factory) }
            }
            waitTicks(child.durationTicks)
        }
        cursor = start + child.durationTicks
    }

    /** Each direct effect starts together; nested sequential blocks form parallel branches. */
    fun parallel(block: ParallelSceneBuilder.() -> Unit) {
        val group = ParallelSceneBuilder().apply(block)
        val start = cursor
        var groupLength = 0
        group.branches.forEach { branch ->
            val child = SceneBuilder().apply(branch).build()
            require(child.repeats == 1) { "Set repeats on the root scene, not on nested blocks" }
            require(
                tracks.size + child.tracks.size <= 4096 &&
                    start.toLong() + child.durationTicks <= MAX_SCENE_TICKS
            )
            tracks += child.tracks.map { it.copy(startTick = it.startTick + start) }
            groupLength = maxOf(groupLength, child.durationTicks)
        }
        cursor = start + groupLength
        length = maxOf(length, cursor)
    }

    internal fun build(): SceneDefinition {
        require(length in 1..MAX_SCENE_TICKS && length.toLong() * repeats <= MAX_SCENE_TICKS)
        return SceneDefinition(tracks.sortedBy { it.startTick }.toList(), length, repeats)
    }
}

class ParallelSceneBuilder internal constructor() {
    internal val branches = mutableListOf<SceneBuilder.() -> Unit>()

    fun effect(effect: SceneEffect) {
        branches += { effect(effect) }
    }

    fun effect(durationTicks: Int, factory: (SceneContext) -> SceneEffect) {
        branches += { effect(durationTicks, factory) }
    }

    fun sequential(block: SceneBuilder.() -> Unit) {
        branches += block
    }
}

fun clientScene(block: SceneBuilder.() -> Unit): SceneDefinition =
    SceneBuilder().apply(block).build()

/**
 * %en
 * Local handle. All operations must run on the client thread.
 * %zh
 * 本地句柄；所有操作必须在客户端线程执行。
 */
interface EffectHandle {
    val isActive: Boolean
    val endReason: EffectEndReason?

    fun cancel()

    fun onEnd(callback: (EffectEndReason) -> Unit): EffectHandle
}

interface SceneHandle : EffectHandle {
    val isPaused: Boolean

    fun pause()

    fun resume()
}
