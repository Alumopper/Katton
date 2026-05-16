package top.katton.api.event

import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.player.*
import org.bukkit.event.block.Action; import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

object PlayerEvent {
    @JvmField val onUseItemOn = createUnit<Any>()
    @JvmField val onUseWithoutItem = createUnit<Any>()
    @JvmField val onAttackBlock = createUnit<Any>()
    @JvmField val onAttackEntity = createUnit<Any>()
    @JvmField val onBlockInteract = createUnit<Any>()
    @JvmField val onEntityInteract = createUnit<Any>()
    @JvmField val onItemInteract = createUnit<Any>()
    @JvmField val onDestroyItem = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onInteract(e: PlayerInteractEvent) {
                val p = PaperNmsBridge.toNmsPlayer(e.player)
                when (e.action) {
                    Action.RIGHT_CLICK_BLOCK -> onUseItemOn(p)
                    Action.RIGHT_CLICK_AIR -> onItemInteract(p)
                    Action.LEFT_CLICK_BLOCK -> onAttackBlock(p)
                    else -> {}
                }
            }
            @EventHandler fun onItemBreak(e: PlayerItemBreakEvent) {
                onDestroyItem(PaperNmsBridge.toNmsPlayer(e.player))
            }
        }, plugin)
    }
}




