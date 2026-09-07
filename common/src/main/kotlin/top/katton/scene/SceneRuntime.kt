package top.katton.scene

import java.util.UUID
import kotlin.math.*
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.phys.Vec3
import top.katton.api.scene.*
import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext

/** No Minecraft client classes: deterministic runtime can be exercised on a headless JVM. */
interface SceneBackend {
    fun resolve(anchor: EffectAnchor, context: SceneContext, partialTick: Float): Vec3?

    fun particle(type: ParticleOptions, position: Vec3, velocity: Vec3)

    fun diagnostic(message: String, failure: Throwable? = null)
}

data class SceneOwner(
    val owner: String?,
    val scope: ScriptPackScope?,
    val environment: ScriptEnvironment?,
    val revision: String? = null,
) {
    fun <T> invoke(action: () -> T): T =
        ScriptExecutionContext.withEnvironment(environment) {
            ScriptExecutionContext.withScope(scope) {
                ScriptExecutionContext.withOwner(owner, action)
            }
        }

    companion object {
        fun capture() =
            SceneOwner(
                ScriptExecutionContext.currentScriptOwner(),
                ScriptExecutionContext.currentScriptScope(),
                ScriptExecutionContext.currentScriptEnvironment(),
                ScriptExecutionContext.currentScriptRevision(),
            )
    }
}

data class SampledGeometry(
    val effect: SceneEffect,
    val origin: Vec3,
    val end: Vec3?,
    val trail: List<Vec3>,
    val age: Double,
)

data class SceneFrame(
    val camera: CameraPose?,
    val fov: Float?,
    val shakeYaw: Float,
    val shakePitch: Float,
    val shakeRoll: Float,
    val geometry: List<SampledGeometry>,
)

class SceneRuntime(
    private val backend: SceneBackend,
    var budgets: EffectBudgets = EffectBudgets(),
) {
    private val plays = linkedMapOf<UUID, Playback>()
    private var particleBudget = 0
    private var clock = 0L
    private var lastWarning = -100L
    private var clearing = false
    val activeScenes
        get() = plays.size

    val activeEffects
        get() = plays.values.sumOf { it.effects.size }

    val inputLocked
        get() =
            plays.values.any { p ->
                p.effects.any { (_, a) -> cameraOptions(a.effect)?.lockInput == true }
            }

    private data class Active(
        val effect: SceneEffect,
        val start: Int,
        val serial: Long,
        val trail: ArrayDeque<Vec3> = ArrayDeque(),
    )

    private inner class Playback(
        val id: UUID,
        val definition: SceneDefinition,
        val context: SceneContext,
        val owner: SceneOwner,
    ) : SceneHandle {
        var elapsed = 0
        var iteration = 0
        val effects = linkedMapOf<Int, Active>()
        val callbacks = mutableListOf<(EffectEndReason) -> Unit>()
        override var endReason: EffectEndReason? = null
        override val isActive
            get() = endReason == null

        override var isPaused = false

        override fun cancel() {
            end(this, EffectEndReason.CANCELLED)
        }

        override fun pause() {
            if (isActive) isPaused = true
        }

        override fun resume() {
            if (isActive) isPaused = false
        }

        override fun onEnd(callback: (EffectEndReason) -> Unit): EffectHandle {
            if (isActive) callbacks += callback else invokeCallback(this, callback, endReason!!)
            return this
        }
    }

    fun play(
        definition: SceneDefinition,
        context: SceneContext = SceneContext(),
        owner: SceneOwner = SceneOwner.capture(),
        id: UUID = UUID.randomUUID(),
    ): SceneHandle {
        val play = Playback(id, definition, context, owner)
        if (clearing || plays.containsKey(id) || plays.size >= budgets.activeEffects ||
            definition.tracks.count { it.startTick == 0 } > budgets.activeEffects - activeEffects) {
            play.endReason = EffectEndReason.REJECTED
            warn("Scene rejected: duplicate id, cleanup in progress, or scene budget exceeded")
            return play
        }
        plays[id] = play
        startTracks(play)
        return play
    }

    fun stop(id: UUID) {
        plays[id]?.cancel()
    }

    fun contains(id: UUID) = plays.containsKey(id)

    fun clear(
        reason: EffectEndReason = EffectEndReason.WORLD_CHANGED,
        predicate: (SceneOwner) -> Boolean = { true },
    ) {
        val wasClearing = clearing
        clearing = true
        try {
            plays.values.toList().filter { predicate(it.owner) }.forEach { end(it, reason) }
        } finally {
            clearing = wasClearing
        }
    }

    fun skipCamera(): Boolean {
        val play =
            plays.values.lastOrNull { p ->
                p.effects.values.any { cameraOptions(it.effect) != null }
            } ?: return false
        play.cancel()
        return true
    }

    fun tick() {
        clock++
        particleBudget = budgets.particlesPerTick
        for (play in plays.values.toList()) {
            if (!play.isActive || play.isPaused) continue
            for (active in play.effects.values.toList()) {
                if (!play.isActive) break
                try {
                    tickEffect(play, active)
                } catch (failure: Throwable) {
                    fail(play, failure)
                }
            }
            if (!play.isActive) continue
            play.elapsed++
            play.effects.entries.removeIf { (_, a) ->
                play.elapsed - a.start >= a.effect.durationTicks
            }
            if (play.elapsed >= play.definition.durationTicks) {
                play.iteration++
                if (play.iteration >= play.definition.repeats) {
                    end(play, EffectEndReason.COMPLETED)
                    continue
                }
                play.elapsed = 0
            }
            startTracks(play)
        }
    }

    private fun startTracks(play: Playback) {
        for ((index, track) in play.definition.tracks.withIndex()) {
            if (!play.isActive) break
            if (track.startTick != play.elapsed) continue
            if (activeEffects >= budgets.activeEffects) {
                warn("Active effect budget exceeded")
                // Reject this effect, but preserve the timeline's clock and existing children.
                continue
            }
            try {
                val effect = play.owner.invoke { track.factory(play.context) }.frozen()
                require(effect.durationTicks == track.durationTicks) {
                    "Track duration differs from its effect duration"
                }
                // Replacing a cutscene cancels its previous owner, so its children cannot leak.
                val slot = slot(effect)
                if (slot != null) {
                    plays.values.toList().forEach { other ->
                        if (other.effects.values.any { slot(it.effect) == slot }) {
                            if (other !== play) end(other, EffectEndReason.REPLACED)
                            else other.effects.entries.removeIf { slot(it.value.effect) == slot }
                        }
                    }
                }
                if (play.isActive && activeEffects < budgets.activeEffects)
                    play.effects[index] = Active(effect, play.elapsed, index.toLong())
                else if (play.isActive) warn("Active effect budget exceeded by a reentrant factory")
            } catch (failure: Throwable) {
                fail(play, failure)
            }
        }
    }

    private fun tickEffect(play: Playback, active: Active) {
        when (val effect = active.effect) {
            is ParticleEmitter -> {
                val origin = resolve(play, effect.anchor, 1f) ?: return
                val count = minOf(effect.countPerTick, particleBudget)
                if (count < effect.countPerTick) warn("Particle budget exceeded")
                particleBudget -= count
                val random =
                    java.util.Random(
                        play.context.seed xor
                            (play.iteration.toLong() shl 32) xor
                            play.elapsed.toLong() xor
                            active.serial
                    )
                repeat(count) { i ->
                    backend.particle(
                        effect.particle,
                        origin.add(
                            SceneMath.particlePoint(effect.shape, i, effect.countPerTick, random)
                        ),
                        effect.velocity,
                    )
                }
            }
            is TrailEffect -> {
                val position = resolve(play, effect.anchor, 1f) ?: return
                val last = active.trail.lastOrNull()
                if (
                    last != null &&
                        last.distanceToSqr(position) >
                            effect.teleportDistance * effect.teleportDistance
                )
                    active.trail.clear()
                active.trail.addLast(position)
                while (active.trail.size > effect.maxPoints) active.trail.removeFirst()
            }
            else -> Unit
        }
    }

    fun frame(partialTick: Float): SceneFrame {
        var camera: CameraPose? = null
        var fov: Float? = null
        var sy = 0f
        var sp = 0f
        var sr = 0f
        val geometry = mutableListOf<SampledGeometry>()
        for (play in plays.values.toList()) {
            if (!play.isActive) continue
            var localCamera: CameraPose? = null
            var localFov: Float? = null
            var ly = 0f
            var lp = 0f
            var lr = 0f
            val localGeometry = mutableListOf<SampledGeometry>()
            try {
                for (active in play.effects.values.toList()) {
                    if (!play.isActive) break
                    val age =
                        (play.elapsed - active.start).toDouble() +
                            if (play.isPaused) 0.0 else partialTick.coerceIn(0f, 1f).toDouble()
                    when (val effect = active.effect) {
                        is CameraPath -> {
                            var pose = SceneMath.camera(effect, age)
                            effect.options.lookAt?.let { target ->
                                resolve(play, target, partialTick)?.let {
                                    pose = SceneMath.lookAt(pose, it)
                                }
                            }
                            localCamera = pose
                        }
                        is CameraFollow -> {
                            val p = resolve(play, effect.anchor, partialTick) ?: continue
                            var pose = CameraPose(p, effect.yaw, effect.pitch)
                            effect.options.lookAt?.let { target ->
                                resolve(play, target, partialTick)?.let {
                                    pose = SceneMath.lookAt(pose, it)
                                }
                            }
                            localCamera = pose
                        }
                        is CameraFov ->
                            localFov =
                                (effect.from +
                                        (effect.to - effect.from) *
                                            effect.easing.sample(age / effect.durationTicks))
                                    .toFloat()
                        is CameraShake -> {
                            val strength =
                                effect.strength *
                                    (1 - age / effect.durationTicks).coerceIn(0.0, 1.0)
                            val phase =
                                age * effect.frequency * PI * 2 +
                                    (play.context.seed % 10000) +
                                    active.serial
                            ly += (sin(phase) * strength).toFloat()
                            lp += (sin(phase * 1.31) * strength).toFloat()
                            lr += (sin(phase * 0.73) * strength * 0.5).toFloat()
                        }
                        is GeometryEffect ->
                            resolve(play, effect.anchor, partialTick)?.let {
                                localGeometry += SampledGeometry(effect, it, null, emptyList(), age)
                            }
                        is BeamEffect -> {
                            val a = resolve(play, effect.from, partialTick) ?: continue
                            val b = resolve(play, effect.to, partialTick) ?: continue
                            localGeometry += SampledGeometry(effect, a, b, emptyList(), age)
                        }
                        is TrailEffect -> {
                            val p = resolve(play, effect.anchor, partialTick) ?: continue
                            val points = active.trail.toMutableList()
                            if (
                                points.lastOrNull()?.distanceToSqr(p)?.let {
                                    it > effect.teleportDistance * effect.teleportDistance
                                } == true
                            )
                                points.clear()
                            // The newest tick sample is the current endpoint, not an extra
                            // historical point. Replace it to avoid a backwards segment.
                            if (points.isEmpty()) points += p else points[points.lastIndex] = p
                            localGeometry += SampledGeometry(effect, p, null, points.toList(), age)
                        }
                        is ParticleEmitter -> Unit
                    }
                }
            } catch (failure: Throwable) {
                fail(play, failure)
            }
            if (play.isActive) {
                localCamera?.let { camera = it }
                localFov?.let { fov = it }
                sy += ly
                sp += lp
                sr += lr
                geometry += localGeometry
            }
        }
        return SceneFrame(
            camera,
            fov ?: camera?.fov,
            sy.coerceIn(-45f, 45f),
            sp.coerceIn(-45f, 45f),
            sr.coerceIn(-45f, 45f),
            geometry.toList(),
        )
    }

    private fun resolve(play: Playback, anchor: EffectAnchor, partial: Float): Vec3? {
        val result = backend.resolve(anchor, play.context, if (play.isPaused) 0f else partial)
        if (result == null) end(play, EffectEndReason.TARGET_LOST)
        return result
    }

    private fun end(play: Playback, reason: EffectEndReason) {
        if (!play.isActive) return
        play.endReason = reason
        if (plays[play.id] === play) plays.remove(play.id)
        play.effects.clear()
        val callbacks = play.callbacks.toList()
        play.callbacks.clear()
        callbacks.forEach { invokeCallback(play, it, reason) }
    }

    private fun invokeCallback(
        play: Playback,
        callback: (EffectEndReason) -> Unit,
        reason: EffectEndReason,
    ) {
        try {
            play.owner.invoke { callback(reason) }
        } catch (failure: Throwable) {
            backend.diagnostic("Scene callback failed for ${play.owner.owner}", failure)
        }
    }

    private fun fail(play: Playback, failure: Throwable) {
        backend.diagnostic("Scene failed for ${play.owner.owner}", failure)
        end(play, EffectEndReason.ERROR)
    }

    private fun warn(message: String) {
        if (clock - lastWarning >= 100) {
            lastWarning = clock
            backend.diagnostic(message)
        }
    }

    private fun slot(effect: SceneEffect): String? =
        when (effect) {
            is CameraPath,
            is CameraFollow -> "camera"
            is CameraFov -> "fov"
            else -> null
        }

    private fun cameraOptions(effect: SceneEffect): CameraOptions? =
        when (effect) {
            is CameraPath -> effect.options
            is CameraFollow -> effect.options
            else -> null
        }
}

private fun SceneEffect.frozen(): SceneEffect =
    when (this) {
        is CameraPath -> copy(keyframes = keyframes.toList())
        is GeometryEffect ->
            copy(
                geometry =
                    when (val g = geometry) {
                        is EffectGeometry.Curve -> g.copy(points = g.points.toList())
                        is EffectGeometry.Face -> g.copy(points = g.points.toList())
                        else -> g
                    }
            )
        else -> this
    }
