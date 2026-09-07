import com.mojang.blaze3d.pipeline.RenderTarget
import java.util.concurrent.CompletableFuture
import net.minecraft.client.CameraType
import net.minecraft.client.Screenshot
import net.minecraft.client.input.KeyEvent
import top.katton.api.*
import top.katton.api.scene.*
import top.katton.client.scene.ClientSceneManager

// Install only in an isolated verification client, alongside examples/client-scenes.
@ClientScriptEntrypoint(ClientPhase.READY)
fun runSceneProbe() {
    val mc = net.minecraft.client.Minecraft.getInstance()
    var previousTick = Long.MIN_VALUE
    var tick = 0
    var reload: CompletableFuture<*>? = null
    var local: EffectHandle? = null
    var cycle: EffectHandle? = null
    val savedFov = mc.options.fov().get()
    var peerMode = java.io.File(mc.gameDirectory, "scene-probe-peer").exists()
    var peerStatus = ""
    registerClientScene("scene_probe:long", "probe-1") {
        effect(600) { context ->
            CameraPath(
                listOf(
                    CameraKeyframe(0, CameraPose(context.origin.add(0.0, 3.0, 5.0))),
                    CameraKeyframe(600, CameraPose(context.origin.add(0.0, 3.0, 5.0))),
                ),
                CameraOptions(lookAt = EffectAnchor.Origin()),
            )
        }
    }
    fun command(name: String) {
        mc.player!!.connection.sendCommand("kattonscene $name")
    }
    fun screenshot(name: String) {
        val getter =
            mc.javaClass.methods.firstOrNull {
                it.name == "getMainRenderTarget" && it.parameterCount == 0
            }
        val target =
            if (getter != null) getter.invoke(mc)
            else mc.gameRenderer.javaClass.getMethod("mainRenderTarget").invoke(mc.gameRenderer)
        Screenshot.grab(mc.gameDirectory, "scene-$name.png", target as RenderTarget, 1) {
            println("SCENE_PROBE screenshot $name: $it")
        }
    }
    val executor =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor {
            Thread(it, "Katton-SceneProbe").apply { isDaemon = true }
        }
    executor.scheduleAtFixedRate(
        {
            mc.execute {
                if (peerMode) {
                    val status =
                        "${mc.player?.uuid} ${ClientSceneManager.stats()} locked=${ClientSceneManager.inputLocked()} alive=${mc.player?.isAlive} dimension=${mc.level?.dimension()?.identifier()}"
                    if (status != peerStatus) {
                        println("SCENE_PEER $status")
                        peerStatus = status
                    }
                    val skip = java.io.File(mc.gameDirectory, "scene-probe-skip")
                    if (skip.exists()) {
                        check(ClientSceneManager.skipCamera())
                        check(skip.delete())
                    }
                    val respawn = java.io.File(mc.gameDirectory, "scene-probe-respawn")
                    if (respawn.exists()) {
                        mc.player?.respawn()
                        check(respawn.delete())
                    }
                    if (java.io.File(mc.gameDirectory, "scene-probe-exit").exists()) {
                        executor.shutdown()
                        mc.stop()
                    }
                    return@execute
                }
                val now = mc.level?.gameTime ?: return@execute
                if (now == previousTick) return@execute
                previousTick = now
                tick++
                try {
                    if (tick in 440..639) {
                        if (tick % 2 == 0) {
                            cycle =
                                playClientEffect(
                                    GeometryEffect(
                                        EffectGeometry.Sphere(0.5),
                                        EffectAnchor.Position(
                                            mc.player!!.position().add(0.0, 2.0, 2.0)
                                        ),
                                    )
                                )
                        } else cycle?.cancel()
                    }
                    if (tick in 175..185) {
                        mc.player!!.move(
                            net.minecraft.world.entity.MoverType.SELF,
                            net.minecraft.world.phys.Vec3(0.1, 0.0, 0.0),
                        )
                    }
                    when (tick) {
                        20 -> {
                            mc.options.cameraType = CameraType.FIRST_PERSON
                            mc.player!!.yRot = 0f
                            mc.player!!.xRot = 35f
                            command("impact")
                            transitionCameraFov(75f, 50f, 20)
                        }
                        28 -> {
                            check(mc.options.fov().get() == savedFov)
                            check(ClientSceneManager.fov(75f) < 75f)
                            screenshot("impact")
                        }
                        40 -> {
                            mc.options.cameraType = CameraType.THIRD_PERSON_BACK
                            mc.player!!.xRot = 10f
                        }
                        50 -> command("geometry")
                        80 -> {
                            screenshot("geometry")
                            println("SCENE_PROBE geometry ${ClientSceneManager.stats()}")
                            check(ClientSceneManager.stats().getValue("effects") > 0)
                            check(ClientSceneManager.stats().getValue("buffers") > 0)
                        }
                        85 -> {
                            for (through in listOf(false, true)) {
                                playClientEffect(GeometryEffect(
                                    EffectGeometry.Box(),
                                    EffectAnchor.Position(mc.player!!.position().add(
                                        if (through) -1.5 else 1.5, -1.0, 3.0)),
                                    30,
                                    EffectMaterial(color = 0x88FF55FF.toInt(), throughWalls = through)))
                            }
                        }
                        95 -> screenshot("alpha-depth")
                        150 -> mc.options.cameraType = CameraType.FIRST_PERSON
                        170 -> command("trail")
                        190 -> screenshot("trail")
                        390 -> command("cutscene")
                        420 -> {
                            screenshot("cutscene")
                            check(ClientSceneManager.inputLocked())
                            val keyPress =
                                mc.keyboardHandler.javaClass.getDeclaredMethod(
                                    "keyPress",
                                    java.lang.Long.TYPE,
                                    java.lang.Integer.TYPE,
                                    KeyEvent::class.java,
                                )
                            keyPress.isAccessible = true
                            keyPress.invoke(mc.keyboardHandler, 0L, 1, KeyEvent(256, 0, 0))
                            check(!ClientSceneManager.inputLocked())
                        }
                        640 -> {
                            println("SCENE_PROBE cycles100 ${ClientSceneManager.stats()}")
                        }
                        650 -> {
                            check(ClientSceneManager.stats()["effects"] == 0)
                            check(ClientSceneManager.stats()["buffers"] == 0)
                            local =
                                playClientEffect(
                                    GeometryEffect(
                                        EffectGeometry.Ring(2.0),
                                        EffectAnchor.Position(mc.player!!.position()),
                                        durationTicks = 200,
                                    )
                                )
                            reload = mc.reloadResourcePacks()
                        }
                        700 -> {
                            check(reload?.isCompletedExceptionally != true)
                            local?.cancel()
                        }
                        740 -> {
                            check(!ClientSceneManager.inputLocked())
                            check(ClientSceneManager.stats()["effects"] == 0)
                            check(ClientSceneManager.stats()["buffers"] == 0)
                            println("SCENE_PROBE PASS ${ClientSceneManager.stats()}")
                            System.setProperty("katton.sceneProbe.ready", "true")
                            if (java.io.File(mc.gameDirectory, "scene-probe-keep-open").exists()) {
                                peerMode = true
                            } else {
                                executor.shutdown()
                                mc.stop()
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    println("SCENE_PROBE FAIL tick=$tick: $failure")
                    failure.printStackTrace()
                    executor.shutdown()
                    mc.stop()
                }
            }
        },
        0,
        50,
        java.util.concurrent.TimeUnit.MILLISECONDS,
    )
}
