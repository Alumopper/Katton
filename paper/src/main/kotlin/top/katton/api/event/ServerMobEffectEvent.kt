package top.katton.api.event

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createAll
import top.katton.util.createUnit

object ServerMobEffectEvent {
    @JvmField
    val onAllowAdd = createAll<MobEffectAllowAddArg>()

    @JvmField
    val onBeforeAdd = createUnit<MobEffectAddArg>()

    @JvmField
    val onAfterAdd = createUnit<MobEffectAddArg>()

    @JvmField
    val onAllowEarlyRemove = createAll<MobEffectAllowEarlyRemoveArg>()

    @JvmField
    val onBeforeRemove = createUnit<MobEffectBeforeRemoveArg>()

    @JvmField
    val onAfterRemove = createUnit<MobEffectAfterRemoveArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onPotionEffect(event: EntityPotionEffectEvent) {
                val living = event.entity as? org.bukkit.entity.LivingEntity ?: return
                val entity = PaperNmsBridge.toNmsLivingEntity(living)
                val effect = event.newEffect ?: event.oldEffect ?: return

                when (event.action) {
                    EntityPotionEffectEvent.Action.ADDED,
                    EntityPotionEffectEvent.Action.CHANGED -> {
                        val allow = onAllowAdd(MobEffectAllowAddArg(entity, effect)).getOrElse { true }
                        if (!allow) {
                            event.isCancelled = true
                            return
                        }
                        onBeforeAdd(MobEffectAddArg(entity, effect))
                    }

                    EntityPotionEffectEvent.Action.CLEARED,
                    EntityPotionEffectEvent.Action.REMOVED -> {
                        val allow = onAllowEarlyRemove(MobEffectAllowEarlyRemoveArg(entity, effect)).getOrElse { true }
                        if (!allow) {
                            event.isCancelled = true
                            return
                        }
                        onBeforeRemove(MobEffectBeforeRemoveArg(entity, effect))
                    }
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onPotionEffectAfter(event: EntityPotionEffectEvent) {
                val living = event.entity as? org.bukkit.entity.LivingEntity ?: return
                val entity = PaperNmsBridge.toNmsLivingEntity(living)
                val effect = event.newEffect ?: event.oldEffect ?: return

                when (event.action) {
                    EntityPotionEffectEvent.Action.ADDED,
                    EntityPotionEffectEvent.Action.CHANGED -> onAfterAdd(MobEffectAddArg(entity, effect))
                    EntityPotionEffectEvent.Action.CLEARED,
                    EntityPotionEffectEvent.Action.REMOVED -> onAfterRemove(MobEffectAfterRemoveArg(entity, effect))
                }
            }
        }, plugin)
    }
}
