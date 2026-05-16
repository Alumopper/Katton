package top.katton.api.event

import net.minecraft.world.entity.Mob
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

object ServerLivingEntityEvent {
    @JvmField
    val onLivingHurt = createCancellableUnit<LivingHurtArg>()

    @JvmField
    val onAllowDamage = createAll<AllowDamageArg>()

    @JvmField
    val onAfterDamage = createUnit<AfterDamageArg>()

    @JvmField
    val onAllowDeath = createAll<AllowDeathArg>()

    @JvmField
    val onAfterDeath = createUnit<AfterDeathArg>()

    @JvmField
    val onLivingFall = createCancellableUnit<LivingFallArg>()

    @JvmField
    val onMobConversion = createUnit<MobConversionArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun handleLivingDamage(event: EntityDamageEvent) {
                val living = event.entity as? org.bukkit.entity.LivingEntity ?: return
                val server = PaperNmsBridge.toNmsServer(plugin.server)
                val source = PaperNmsBridge.toNmsDamageSource(server, event.damageSource)

                val hurtArg = LivingHurtArg(
                    PaperNmsBridge.toNmsLivingEntity(living),
                    source,
                    event.damage.toFloat()
                )
                onLivingHurt(hurtArg)

                val allowDamage = onAllowDamage(
                    AllowDamageArg(
                        PaperNmsBridge.toNmsLivingEntity(living),
                        source,
                        event.damage.toFloat()
                    )
                ).getOrElse { true }

                if (hurtArg.isCancelled() || !allowDamage) {
                    event.isCancelled = true
                    return
                }

                if (event.cause == EntityDamageEvent.DamageCause.FALL) {
                    val fallArg = LivingFallArg(
                        PaperNmsBridge.toNmsLivingEntity(living),
                        living.fallDistance.toDouble(),
                        1.0f
                    )
                    onLivingFall(fallArg)
                    if (fallArg.isCancelled()) {
                        event.isCancelled = true
                    }
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun handleAfterDamage(event: EntityDamageEvent) {
                val living = event.entity as? org.bukkit.entity.LivingEntity ?: return
                onAfterDamage(
                    AfterDamageArg(
                        PaperNmsBridge.toNmsLivingEntity(living),
                        PaperNmsBridge.toNmsDamageSource(
                            PaperNmsBridge.toNmsServer(plugin.server),
                            event.damageSource
                        ),
                        event.damage.toFloat(),
                        event.finalDamage.toFloat(),
                        true
                    )
                )
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun handleAllowDeath(event: EntityDeathEvent) {
                val lastDamage = event.entity.lastDamageCause as? EntityDamageEvent
                val source = PaperNmsBridge.toNmsDamageSource(
                    PaperNmsBridge.toNmsServer(plugin.server),
                    event.damageSource
                )
                val allow = onAllowDeath(
                    AllowDeathArg(
                        PaperNmsBridge.toNmsLivingEntity(event.entity),
                        source,
                        lastDamage?.damage?.toFloat() ?: 0f
                    )
                ).getOrElse { true }
                if (!allow) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun handleAfterDeath(event: EntityDeathEvent) {
                onAfterDeath(
                    AfterDeathArg(
                        PaperNmsBridge.toNmsLivingEntity(event.entity),
                        PaperNmsBridge.toNmsDamageSource(
                            PaperNmsBridge.toNmsServer(plugin.server),
                            event.damageSource
                        )
                    )
                )
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun handleTransform(event: EntityTransformEvent) {
                val oldEntity = PaperNmsBridge.toNmsEntity(event.entity) as? Mob ?: return
                val newEntity = PaperNmsBridge.toNmsEntity(event.transformedEntity) as? Mob ?: return
                onMobConversion(MobConversionArg(oldEntity, newEntity, null))
            }
        }, plugin)
    }
}
