package top.katton.api.event

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createAll
import top.katton.util.createUnit

object ServerEntityCombatEvent {
    @JvmField
    val onAfterKilledOtherEntity = createUnit<AfterKilledOtherEntityArg>()

    @JvmField
    val onShieldBlock = createAll<ShieldBlockArg>()

    @JvmField
    val onCriticalHit = createUnit<CriticalHitArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onEntityDeath(event: EntityDeathEvent) {
                val lastDamage = event.entity.lastDamageCause ?: return
                val source = PaperNmsBridge.toNmsDamageSource(
                    PaperNmsBridge.toNmsServer(plugin.server),
                    lastDamage.damageSource
                )
                val killer = source.entity ?: source.directEntity ?: return
                onAfterKilledOtherEntity(
                    AfterKilledOtherEntityArg(
                        PaperNmsBridge.toNmsLevel(event.entity.world),
                        killer,
                        PaperNmsBridge.toNmsLivingEntity(event.entity),
                        source
                    )
                )
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onDamageByEntity(event: EntityDamageByEntityEvent) {
                if (event.damager is org.bukkit.entity.Player) {
                    onCriticalHit(
                        CriticalHitArg(
                            PaperNmsBridge.toNmsPlayer(event.damager as org.bukkit.entity.Player),
                            PaperNmsBridge.toNmsEntity(event.entity),
                            event.isCritical
                        )
                    )
                }

                if (event.entity !is org.bukkit.entity.LivingEntity) {
                    return
                }

                if (!event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
                    return
                }

                val blockedDamage = -event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING).toFloat()
                if (blockedDamage <= 0f) {
                    return
                }

                onShieldBlock(
                    ShieldBlockArg(
                        PaperNmsBridge.toNmsLivingEntity(event.entity as org.bukkit.entity.LivingEntity),
                        PaperNmsBridge.toNmsDamageSource(
                            PaperNmsBridge.toNmsServer(plugin.server),
                            event.damageSource
                        ),
                        blockedDamage,
                        true
                    )
                )
            }
        }, plugin)
    }
}
