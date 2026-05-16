package top.katton.api.event

import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.block.Action; import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

object ItemEvent {
    @JvmField val onUseOn = createUnit<Any>()
    @JvmField val onUse = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onInteract(e: PlayerInteractEvent) {
                val p = PaperNmsBridge.toNmsPlayer(e.player)
                when (e.action) {
                    Action.RIGHT_CLICK_BLOCK -> onUseOn(p)
                    Action.RIGHT_CLICK_AIR -> onUse(p)
                    else -> {}
                }
            }
        }, plugin)
    }
}




