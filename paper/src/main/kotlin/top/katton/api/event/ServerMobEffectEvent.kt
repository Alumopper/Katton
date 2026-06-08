package top.katton.api.event

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * %en
 * Server mob effect events for Paper (Bukkit) platform.
 *
 * This object provides events related to potion effect addition,
 * removal, and modification on mobs.
 *
 * %zh
 * Paper (Bukkit) 平台的服务器生物效果事件。
 *
 * 此对象提供与生物身上的药水效果添加、移除和修改相关的事件。
 */
@Suppress("unused")
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
