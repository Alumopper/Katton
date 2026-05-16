package top.katton.api.event

import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

object LivingUseItemEvent {
    @JvmField val onUseItemStart = createUnit<Any>()
    @JvmField val onUseItemTick = createUnit<Any>()
    @JvmField val onUseItemStop = createUnit<Any>()
    @JvmField val onUseItemFinish = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onConsume(e: PlayerItemConsumeEvent) {
                val p = PaperNmsBridge.toNmsPlayer(e.player)
                onUseItemStart(p); onUseItemFinish(p)
            }
        }, plugin)
    }
}




