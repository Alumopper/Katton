import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import top.katton.Katton
import top.katton.api.*
import top.katton.api.audio.*
import kotlin.time.Duration.Companion.seconds

@ServerScriptEntrypoint(ServerPhase.READY)
fun remoteAudioProbe() {
    val server = checkNotNull(Katton.server)
    val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "Katton-RemoteAudioProbe").apply { isDaemon = true } }
    var remote: RemoteAudioHandle? = null
    var checking = false
    var completed = false
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180)
    fun finish(error: Throwable?) {
        if (completed) return
        completed = true
        if (error == null) println("AUDIO_REMOTE_PROBE_PASS spatial rate pause seek query stop close")
        else { println("AUDIO_REMOTE_PROBE_FAIL"); error.printStackTrace() }
        remote?.close(); timer.shutdown()
        server.halt(false)
    }
    timer.scheduleAtFixedRate({ server.execute {
        try {
            check(System.nanoTime() < deadline) { "Remote audio timeout" }
            if (completed) return@execute
            if (remote == null) {
                val player = server.playerList.players.firstOrNull() ?: return@execute
                remote = playPlayerAudio(player, AudioSource.sound("minecraft:music_disc.cat"),
                    AudioOptions(volume = 0f, entity = player.uuid, loop = true))
            }
            if (!checking) {
                checking = true
                remote!!.refresh().whenComplete { state, error -> server.execute {
                    if (error != null) finish(error)
                    else if (state.state == AudioState.FAILED) finish(IllegalStateException(state.error))
                    else if (state.state != AudioState.PLAYING) checking = false
                    else {
                        val handle = remote!!
                        handle.pause().thenCompose { handle.refresh() }.thenCompose {
                            check(it.state == AudioState.PAUSED)
                            handle.seek(1.seconds)
                        }.thenCompose { handle.setPlaybackRate(2f) }.thenCompose { handle.refresh() }.thenCompose {
                            check(it.playbackRate == 2f && it.position == 1.seconds && it.duration!! > 1.seconds)
                            handle.resume()
                        }.thenCompose { handle.stop() }.thenCompose { handle.refresh() }.thenAccept {
                            check(it.state == AudioState.STOPPED && it.position == kotlin.time.Duration.ZERO)
                        }.whenComplete { _, failure -> server.execute { finish(failure) } }
                    }
                } }
            }
        } catch (failure: Throwable) { finish(failure) }
    } }, 250, 250, TimeUnit.MILLISECONDS)
}
