package top.katton.api.event

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

object LivingUseItemEvent {
    @JvmField
    val onUseItemStart = createCancellableUnit<LivingUseItemStartArg>()

//    @JvmField
//    val onUseItemTick = createCancellableUnit<LivingUseItemTickArg>()
//
//    @JvmField
//    val onUseItemStop = createCancellableUnit<LivingUseItemStopArg>()

    @JvmField
    val onUseItemFinish = createUnit<LivingUseItemFinishArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onConsume(event: PlayerItemConsumeEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val item = PaperNmsBridge.toNmsItemStack(event.item) ?: return
                val hand = PaperNmsBridge.toNmsInteractionHand(event.hand)

                val startArg = LivingUseItemStartArg(player, item, hand, 0)
                onUseItemStart(startArg)
                if (startArg.isCancelled()) {
                    event.isCancelled = true
                    return
                }

                val finishArg = LivingUseItemFinishArg(player, item, 0, item.copy())
                onUseItemFinish(finishArg)
                PaperNmsBridge.toBukkitItemStack(finishArg.result)?.let(event::setReplacement)
            }
        }, plugin)
    }
}
