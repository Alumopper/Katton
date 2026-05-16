package top.katton.api.event

import net.minecraft.server.MinecraftServer
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit

object ServerEvent {
    @JvmField val onServerStarting = createUnit<ServerArg>()
    @JvmField val onServerStarted = createUnit<ServerArg>()
    @JvmField val onServerStopping = createUnit<ServerArg>()
    @JvmField val onServerStopped = createUnit<ServerArg>()
    @JvmField val onSyncDatapackContents = createUnit<Any>()
    @JvmField val onStartDatapackReload = createUnit<Any>()
    @JvmField val onEndDatapackReload = createUnit<Any>()
    @JvmField val onBeforeSave = createUnit<Any>()
    @JvmField val onAfterSave = createUnit<Any>()
    @JvmField val onStartServerTick = createUnit<ServerTickArg>()
    @JvmField val onEndServerTick = createUnit<ServerTickArg>()
    @JvmField val onStartWorldTick = createUnit<WorldTickArg>()
    @JvmField val onEndWorldTick = createUnit<WorldTickArg>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onServerLoad(e: ServerLoadEvent) {
                val server: MinecraftServer = PaperNmsBridge.toNmsServer(plugin.server)
                when (e.type) {
                    ServerLoadEvent.LoadType.STARTUP -> onServerStarted(ServerArg(server))
                    ServerLoadEvent.LoadType.RELOAD -> {
                        onStartDatapackReload(server)
                        onEndDatapackReload(server)
                    }
                }
            }
        }, plugin)
    }
}




