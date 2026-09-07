import top.katton.api.*
import top.katton.api.scene.*
import top.katton.registry.registerCommand

// Install in an isolated server's world/kattonpacks/remote-probe directory.
@ServerScriptEntrypoint(ServerPhase.READY)
fun registerRemoteSceneProbe() {
    var remote: RemoteSceneHandle? = null
    registerCommand("sceneprobe") {
        literal("death") {
            executes {
                val player = it.source.server.playerList.players.first()
                player.health = 0f
                println("SCENE_REMOTE death ${player.uuid}")
                1
            }
        }
        literal("one") {
            executes {
                val player =
                    it.source.server.playerList.players.sortedBy { p -> p.uuid.toString() }.first()
                remote =
                    playPlayerScene(
                        player,
                        "scene_probe:long",
                        SceneContext(player.position(), player.uuid),
                        "probe-1",
                    )
                println("SCENE_REMOTE one ${player.uuid}")
                1
            }
        }
        literal("nearby") {
            executes {
                val player =
                    it.source.server.playerList.players.sortedBy { p -> p.uuid.toString() }.first()
                remote =
                    playNearbyScene(
                        player.level(),
                        player.position(),
                        32.0,
                        "scene_probe:long",
                        SceneContext(player.position(), player.uuid),
                        "probe-1",
                    )
                println("SCENE_REMOTE nearby ${player.uuid}")
                1
            }
        }
        literal("stop") {
            executes {
                remote?.cancel()
                println("SCENE_REMOTE stop")
                1
            }
        }
    }
}
