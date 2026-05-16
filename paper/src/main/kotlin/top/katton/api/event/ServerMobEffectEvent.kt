package top.katton.api.event

import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.entity.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

object ServerMobEffectEvent {
    @JvmField val onAllowAdd = createUnit<Any>()
    @JvmField val onBeforeAdd = createUnit<Any>()
    @JvmField val onAfterAdd = createUnit<Any>()
    @JvmField val onAllowEarlyRemove = createUnit<Any>()
    @JvmField val onBeforeRemove = createUnit<Any>()
    @JvmField val onAfterRemove = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onPotionEffect(e: EntityPotionEffectEvent) {
                val entity = PaperNmsBridge.toNmsLivingEntity(e.entity as org.bukkit.entity.LivingEntity)
                when (e.action) {
                    EntityPotionEffectEvent.Action.ADDED, EntityPotionEffectEvent.Action.CHANGED -> { onBeforeAdd(entity); onAfterAdd(entity) }
                    EntityPotionEffectEvent.Action.REMOVED -> { onBeforeRemove(entity); onAfterRemove(entity) }
                    else -> {}
                }
            }
        }, plugin)
    }
}




