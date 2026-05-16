package top.katton.api.event

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import com.destroystokyo.paper.event.server.ServerTickStartEvent
import io.papermc.paper.event.server.ServerResourcesReloadedEvent
import net.minecraft.server.level.ServerLevel
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldSaveEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createUnit

object ServerEvent {
    @JvmField
    val onServerStarting = createUnit<ServerArg>()

    @JvmField
    val onServerStarted = createUnit<ServerArg>()

    @JvmField
    val onServerStopping = createUnit<ServerArg>()

    @JvmField
    val onServerStopped = createUnit<ServerArg>()

    @JvmField
    val onSyncDatapackContents = createUnit<SyncDatapackContentsArg>()

    @JvmField
    val onStartDatapackReload = createUnit<StartDatapackReloadArg>()

    @JvmField
    val onEndDatapackReload = createUnit<EndDatapackReloadArg>()

    @JvmField
    val onBeforeSave = createUnit<ServerSaveArg>()

    @JvmField
    val onAfterSave = createUnit<ServerSaveArg>()

    @JvmField
    val onStartServerTick = createUnit<ServerTickArg>()

    @JvmField
    val onEndServerTick = createUnit<ServerTickArg>()

    @JvmField
    val onStartWorldTick = createUnit<WorldTickArg>()

    @JvmField
    val onEndWorldTick = createUnit<WorldTickArg>()

    @JvmField
    val onLevelLoad = createUnit<ServerLevelArg>()

    @JvmField
    val onLevelUnload = createUnit<ServerLevelArg>()

    @JvmField
    val onLevelSave = createUnit<ServerLevelArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler
            fun onServerLoad(event: ServerLoadEvent) {
                if (event.type == ServerLoadEvent.LoadType.STARTUP) {
                    onServerStarted(ServerArg(PaperNmsBridge.toNmsServer(plugin.server)))
                }
            }

            @EventHandler
            fun onReloaded(event: ServerResourcesReloadedEvent) {
                val server = PaperNmsBridge.toNmsServer(plugin.server)
                val resourceManager = PaperNmsBridge.findResourceManager(server) ?: return
                onStartDatapackReload(StartDatapackReloadArg(server, resourceManager))
                onEndDatapackReload(EndDatapackReloadArg(server, resourceManager, true))
            }

            @EventHandler
            fun onServerTickStart(event: ServerTickStartEvent) {
                val server = PaperNmsBridge.toNmsServer(plugin.server)
                onStartServerTick(ServerTickArg(server))
                server.allLevels.forEach { onStartWorldTick(WorldTickArg(it)) }
            }

            @EventHandler
            fun onServerTickEnd(event: ServerTickEndEvent) {
                val server = PaperNmsBridge.toNmsServer(plugin.server)
                server.allLevels.forEach { onEndWorldTick(WorldTickArg(it)) }
                onEndServerTick(ServerTickArg(server))
            }

            @EventHandler
            fun onWorldLoad(event: WorldLoadEvent) {
                onLevelLoad(ServerLevelArg(PaperNmsBridge.toNmsLevel(event.world)))
            }

            @EventHandler
            fun onWorldUnload(event: WorldUnloadEvent) {
                onLevelUnload(ServerLevelArg(PaperNmsBridge.toNmsLevel(event.world)))
            }

            @EventHandler
            fun onWorldSave(event: WorldSaveEvent) {
                val server = PaperNmsBridge.toNmsServer(plugin.server)
                onBeforeSave(ServerSaveArg(server, false, false))
                onLevelSave(ServerLevelArg(PaperNmsBridge.toNmsLevel(event.world)))
                onAfterSave(ServerSaveArg(server, false, false))
            }
        }, plugin)
    }

    @JvmInline
    value class ServerLevelArg(val level: ServerLevel)
}
