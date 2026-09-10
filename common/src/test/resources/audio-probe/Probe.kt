import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.minecraft.client.Minecraft
import top.katton.api.*
import top.katton.api.audio.*
import top.katton.engine.ManagedResources
import top.katton.util.ScriptExecutionContext
import kotlin.time.Duration.Companion.seconds

// Install in an isolated GLOBAL pack with sine.wav copied to music.blob.
@ClientScriptEntrypoint(ClientPhase.READY)
fun audioIntegrationProbe() {
    val mc = Minecraft.getInstance()
    val owner = ScriptExecutionContext.currentScriptOwner()!!
    val music = playClientAudio(AudioSource.packFile("music.blob"), AudioOptions(volume = 0f, loop = true))
    val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "Katton-AudioProbe").apply { isDaemon = true } }
    var stage = 0
    var since = System.nanoTime()
    var position = 0.0
    var saved = emptyList<ManagedResources.Record>()
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
    fun next(value: Int) { stage = value; since = System.nanoTime(); position = music.snapshot.position.inWholeNanoseconds / 1e9 }
    fun progressed() = (music.snapshot.position.inWholeNanoseconds / 1e9 - position + 2.0) % 2.0
    timer.scheduleAtFixedRate({ mc.execute {
        try {
            check(System.nanoTime() < deadline) { "Timeout at stage $stage: ${music.snapshot}" }
            check(music.snapshot.state != AudioState.FAILED) { music.snapshot.toString() }
            val elapsed = (System.nanoTime() - since) / 1e9
            when (stage) {
                0 -> if (music.snapshot.state == AudioState.PLAYING) next(1)
                1 -> if (elapsed > 0.4) {
                    check(progressed() in 0.15..0.85) { "Normal clock: ${progressed()}" }
                    music.setPlaybackRate(2f).thenRun { mc.execute { next(2) } }; stage = -1
                }
                2 -> if (elapsed > 0.4) {
                    check(progressed() in 0.4..1.3) { "Double clock: ${progressed()}" }
                    music.pause().thenRun { mc.execute { next(3) } }; stage = -1
                }
                3 -> if (elapsed > 0.4) {
                    check(music.snapshot.state == AudioState.PAUSED)
                    check(progressed() < 0.15) { "Pause advanced: ${progressed()}" }
                    music.setPlaybackRate(0.5f).thenCompose { music.seek(1.seconds) }
                        .thenRun { mc.execute { next(4) } }; stage = -1
                }
                4 -> if (elapsed > 0.2) {
                    check(music.snapshot.position == 1.seconds)
                    music.resume().thenRun { mc.execute { next(5) } }; stage = -1
                }
                5 -> if (elapsed > 0.5) {
                    check(progressed() in 0.08..0.6) { "Half clock: ${progressed()}" }
                    mc.soundManager.reload()
                    next(6)
                }
                6 -> if (elapsed > 0.5 && music.snapshot.state == AudioState.PLAYING) {
                    check(music.snapshot.playbackRate == 0.5f)
                    saved = ManagedResources.detach(listOf(owner))
                    next(7)
                }
                7 -> if (elapsed > 0.4) {
                    check(music.snapshot.state == AudioState.PAUSED)
                    ManagedResources.restore(saved)
                    next(8)
                }
                8 -> if (elapsed > 0.4 && music.snapshot.state == AudioState.PLAYING) {
                    music.stop().thenRun { mc.execute { next(9) } }; stage = -1
                }
                9 -> if (elapsed > 0.2) {
                    check(music.snapshot.state == AudioState.STOPPED && music.snapshot.position == kotlin.time.Duration.ZERO)
                    music.resume().thenRun { mc.execute { next(10) } }; stage = -1
                }
                10 -> if (elapsed > 0.4 && music.snapshot.state == AudioState.PLAYING) {
                    ManagedResources.discard(ManagedResources.detach(listOf(owner)))
                    next(11)
                }
                11 -> if (elapsed > 0.2) {
                    check(music.snapshot.state == AudioState.CLOSED)
                    println("AUDIO_CLIENT_PROBE_PASS rate pause seek reload rollback stop close")
                    timer.shutdown(); mc.stop()
                }
            }
        } catch (failure: Throwable) {
            println("AUDIO_CLIENT_PROBE_FAIL stage=$stage snapshot=${music.snapshot}")
            failure.printStackTrace()
            music.close(); timer.shutdown(); mc.stop()
        }
    } }, 100, 100, TimeUnit.MILLISECONDS)
}
