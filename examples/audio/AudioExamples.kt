import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import top.katton.api.audio.*
import kotlin.time.Duration.Companion.seconds

// Call these functions from your own managed event/entrypoint. No audio starts on installation.
// 在脚本入口或受管事件回调中调用；安装示例包不会自动播放。
fun backgroundMusic(): AudioHandle = playClientAudio(
    AudioSource.sound("minecraft:music_disc.cat"),
    AudioOptions(volume = 0.5f, playbackRate = 1.5f, loop = true),
)

// Put an MP3/WAV/Vorbis/FLAC file at this path; its suffix is arbitrary.
fun customMusic(): AudioHandle = playClientAudio(AudioSource.packFile("custom/music.blob"))

fun spatialMusic(position: Vec3): AudioHandle = playClientAudio(
    AudioSource.sound("minecraft:music_disc.cat"), AudioOptions(position = position, range = 24f),
)

fun followPlayer(player: ServerPlayer): RemoteAudioHandle = playPlayerAudio(
    player, AudioSource.sound("minecraft:music_disc.cat"), AudioOptions(entity = player.uuid),
)

fun controlMusic(handle: AudioHandle) = handle.pause()
    .thenCompose { handle.seek(30.seconds) }
    .thenCompose { handle.setPlaybackRate(2f) }
    .thenCompose { handle.resume() }
    .thenCompose { handle.fadeTo(0.2f, 3.seconds) }

// Paper/Folia: vanilla/resource-pack sound IDs only. No Katton client required.
fun paperSound(player: ServerPlayer) = playBasicSound(player, "minecraft:entity.experience_orb.pickup")
fun paperStop(player: ServerPlayer) = stopBasicSound(player, "minecraft:entity.experience_orb.pickup")
