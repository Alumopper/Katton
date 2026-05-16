package top.katton.api.event

import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

object ServerMessageEvent {
    @JvmField val onAllowChatMessage = createUnit<Any>()
    @JvmField val onAllowGameMessage = createUnit<Any>()
    @JvmField val onAllowCommandMessage = createUnit<Any>()
    @JvmField val onChatMessage = createUnit<Any>()
    @JvmField val onGameMessage = createUnit<Any>()
    @JvmField val onCommandMessage = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onChat(e: AsyncPlayerChatEvent) {
                val p = PaperNmsBridge.toNmsPlayer(e.player)
                onAllowChatMessage(p); onChatMessage(p)
            }
        }, plugin)
    }
}




