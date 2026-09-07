import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import top.katton.api.*
import top.katton.api.scene.*
import top.katton.client.scene.ClientSceneManager
import top.katton.engine.ScriptReloadManager

// Install as a separate GLOBAL pack only in the isolated SceneValidation singleplayer client.
@ClientScriptEntrypoint(ClientPhase.READY)
fun runIntegratedSceneProbe() {
    val mc = Minecraft.getInstance()
    fun closeScreen() {
        // The test script is shared across both versions; the production adapter is internal.
        Class.forName("top.katton.compat.ClientCompatKt")
            .getMethod(
                "setClientScreen",
                Minecraft::class.java,
                net.minecraft.client.gui.screens.Screen::class.java,
            )
            .invoke(null, mc, null)
    }
    val source =
        File(
                mc.gameDirectory,
                "saves/SceneValidation/kattonpacks/reload-probe/ReloadScene.kt",
            )
            .toPath()
    val original = Files.readString(source)
    var stage = "start"
    var deadline = 0L
    var handle: SceneHandle? = null
    var pose: CameraPose? = null
    var previousLevel: net.minecraft.client.multiplayer.ClientLevel? = null
    val executor = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "Katton-SceneIntegrationProbe").apply { isDaemon = true }
    }
    fun finish(failure: Throwable? = null) {
        stage = "finished"
        Files.writeString(source, original)
        handle?.cancel()
        if (failure == null) println("SCENE_INTEGRATION PASS")
        else {
            println("SCENE_INTEGRATION FAIL $failure")
            failure.printStackTrace()
        }
        executor.shutdown()
        mc.stop()
    }
    fun play(id: String) =
        playClientScene(id, SceneContext(mc.player!!.position(), mc.player!!.uuid))
    fun reload(callback: () -> Unit) {
        ScriptReloadManager.reloadClientScriptsAsync {
            try {
                closeScreen()
                callback()
            } catch (failure: Throwable) {
                finish(failure)
            }
        }
    }
    executor.scheduleAtFixedRate(
        {
            mc.execute {
                try {
                    val now = System.nanoTime()
                    when (stage) {
                        "start" ->
                            if (System.getProperty("katton.sceneProbe.ready") == "true") {
                                check(mc.singleplayerServer != null)
                                handle = play("scene_probe:reload")
                                mc.pauseGame(false)
                                stage = "pause-settle"
                                deadline = now + 500_000_000L
                            }
                        "pause-settle" ->
                            if (now >= deadline) {
                                check(mc.isPaused)
                                pose = ClientSceneManager.sample(0f).camera
                                deadline = now + 1_000_000_000L
                                stage = "paused"
                            }
                        "paused" ->
                            if (now >= deadline) {
                                check(mc.isPaused && handle!!.isActive)
                                check(pose == ClientSceneManager.sample(0f).camera)
                                closeScreen()
                                deadline = now + 500_000_000L
                                stage = "resumed"
                            }
                        "resumed" ->
                            if (now >= deadline) {
                                check(!mc.isPaused && pose != ClientSceneManager.sample(0f).camera)
                                println("SCENE_INTEGRATION pause PASS")
                                Files.writeString(
                                    source,
                                    "deliberately invalid Kotlin for the precompile probe",
                                )
                                stage = "precompile"
                                reload {
                                    check(handle!!.isActive)
                                    check(ClientSceneManager.inputLocked())
                                    println("SCENE_INTEGRATION precompile preservation PASS")
                                    Files.writeString(
                                        source,
                                        original.replace(
                                            "// ACTIVATION_POINT",
                                            "error(\"expected activation failure\")",
                                        ),
                                    )
                                    stage = "activation"
                                    reload {
                                        check(!handle!!.isActive)
                                        check(handle!!.endReason == EffectEndReason.RELOAD)
                                        check(!ClientSceneManager.inputLocked())
                                        check(ClientSceneManager.stats().getValue("scenes") == 0)
                                        val restored = play("scene_probe:reload")
                                        check(restored.isActive)
                                        restored.cancel()
                                        Files.writeString(source, original)
                                        println("SCENE_INTEGRATION activation rollback PASS")
                                        stage = "death-start"
                                    }
                                }
                            }
                        "death-start" -> {
                            handle = play("scene_probe:long")
                            val server = mc.singleplayerServer!!
                            server.execute { server.playerList.players.single().health = 0f }
                            deadline = now + 10_000_000_000L
                            stage = "dead"
                        }
                        "dead" -> {
                            if (mc.player?.isAlive == false && !handle!!.isActive) {
                                check(!ClientSceneManager.inputLocked())
                                println("SCENE_INTEGRATION death PASS")
                                mc.player!!.respawn()
                                deadline = now + 10_000_000_000L
                                stage = "respawn"
                            } else check(now < deadline) { "Timed out waiting for death cleanup" }
                        }
                        "respawn" -> {
                            if (mc.player?.isAlive == true) {
                                handle = play("scene_probe:long")
                                previousLevel = mc.level
                                val server = mc.singleplayerServer!!
                                server.execute {
                                    server.commands.performPrefixedCommand(
                                        server.createCommandSourceStack(),
                                        "execute in minecraft:the_nether run tp @a 0 80 0",
                                    )
                                }
                                deadline = now + 20_000_000_000L
                                stage = "dimension"
                            } else check(now < deadline) { "Timed out waiting for respawn" }
                        }
                        "dimension" -> {
                            if (
                                mc.level != null && mc.level !== previousLevel && !handle!!.isActive
                            ) {
                                check(!ClientSceneManager.inputLocked())
                                println("SCENE_INTEGRATION dimension PASS")
                                handle = play("scene_probe:long")
                                mc.connection!!
                                    .connection
                                    .disconnect(Component.literal("Scene integration disconnect"))
                                deadline = now + 20_000_000_000L
                                stage = "disconnected"
                            } else
                                check(now < deadline) { "Timed out waiting for dimension cleanup" }
                        }
                        "disconnected" -> {
                            if (mc.level == null) {
                                check(!handle!!.isActive && !ClientSceneManager.inputLocked())
                                check(ClientSceneManager.stats().getValue("effects") == 0)
                                check(ClientSceneManager.stats().getValue("buffers") == 0)
                                println("SCENE_INTEGRATION disconnect PASS")
                                finish()
                            } else
                                check(now < deadline) { "Timed out waiting for disconnect cleanup" }
                        }
                    }
                } catch (failure: Throwable) {
                    finish(failure)
                }
            }
        },
        0,
        50,
        TimeUnit.MILLISECONDS,
    )
}
