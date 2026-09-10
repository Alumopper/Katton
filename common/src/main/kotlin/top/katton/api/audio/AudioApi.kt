@file:Suppress("unused")

package top.katton.api.audio

import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import top.katton.engine.PackRuntime
import top.katton.pack.ScriptPackFileLimits
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration

/** %en Audio bytes are resolved on the client. %zh 音频内容在客户端解析。 */
@ConsistentCopyVisibility
data class AudioSource internal constructor(val kind: Kind, val path: String, val packId: String = "", val revision: String = "") {
    enum class Kind { SOUND, RESOURCE, PACK_FILE }
    init {
        require(path.length in 1..1024)
        if (kind == Kind.PACK_FILE) {
            require(ScriptPackFileLimits.isPortableRelativePath(path)) { "Invalid pack-relative audio path" }
            require(packId.length in 1..512 && revision.matches(Regex("[0-9a-f]{64}")))
        } else require(Identifier.tryParse(path) != null) { "Invalid audio identifier: $path" }
    }
    companion object {
        fun sound(id: String) = AudioSource(Kind.SOUND, id)
        fun resource(id: String) = AudioSource(Kind.RESOURCE, id)
        fun packFile(path: String): AudioSource {
            val owner = checkNotNull(ScriptExecutionContext.currentIdentity()) { "packFile requires a script pack context" }
            val pack = top.katton.engine.AudioPackContext.currentPack()?.takeIf { it.syncId == owner.syncId }
                ?: PackRuntime.effectivePacks(owner.environment, ScriptPackScope.entries.toSet())
                .firstOrNull { it.syncId == owner.syncId }
                ?: error("Script pack is unavailable: ${owner.syncId}")
            return AudioSource(Kind.PACK_FILE, path, pack.syncId, pack.hash)
        }
    }
}

/** %en Spatial audio is mono; null position/target means stereo background audio.
 * %zh 空间音频混为单声道；位置和实体均为空时播放立体声背景音乐。 */
data class AudioOptions(
    val volume: Float = 1f,
    val playbackRate: Float = 1f,
    val loop: Boolean = false,
    val category: SoundSource = SoundSource.MUSIC,
    val position: Vec3? = null,
    val entity: UUID? = null,
    val range: Float = 16f,
) {
    init {
        require(volume.isFinite() && volume in 0f..1f)
        requireRate(playbackRate)
        require(range.isFinite() && range > 0f && range <= 6e7f)
        require(position == null || listOf(position.x, position.y, position.z).all { it.isFinite() && kotlin.math.abs(it) <= 6e7 })
    }
    val spatial: Boolean get() = position != null || entity != null
}

internal fun requireRate(rate: Float) { require(rate.isFinite() && rate in 0.25f..4f) { "playbackRate must be finite and within 0.25..4" } }
enum class AudioState { PREPARING, PLAYING, PAUSED, STOPPED, ENDED, FAILED, CLOSED }

/** %en Position and duration use original media time, independent of speed.
 * %zh 进度和时长采用原始音频时间，不随倍速改变。 */
data class AudioSnapshot(val state: AudioState, val position: Duration, val duration: Duration?,
    val playbackRate: Float, val volume: Float, val loop: Boolean, val error: String? = null)

/** %en Rate changes also change pitch. Commands complete when applied, not when playback ends.
 * %zh 倍速与音高联动。异步结果表示控制已应用，不表示音频播放结束。 */
interface AudioHandle : AutoCloseable {
    val id: UUID
    val snapshot: AudioSnapshot
    fun pause(): CompletableFuture<Unit>
    fun resume(): CompletableFuture<Unit>
    fun seek(position: Duration): CompletableFuture<Unit>
    fun stop(): CompletableFuture<Unit>
    fun setPlaybackRate(rate: Float): CompletableFuture<Unit>
    fun setVolume(volume: Float): CompletableFuture<Unit>
    fun setLoop(loop: Boolean): CompletableFuture<Unit>
    fun fadeTo(volume: Float, duration: Duration): CompletableFuture<Unit>
    fun refresh(): CompletableFuture<AudioSnapshot>
}

/** Physical-client implementation installed by the loader; safe to load on dedicated servers. */
object AudioClientProvider {
    @Volatile var play: ((AudioSource, AudioOptions) -> AudioHandle)? = null
}

/** %en Play through the installed Katton client. %zh 使用 Katton 客户端播放器播放音频。 */
fun playClientAudio(source: AudioSource, options: AudioOptions = AudioOptions()): AudioHandle =
    checkNotNull(AudioClientProvider.play) { "Full audio playback requires a Fabric/NeoForge Katton client" }(source, options)
