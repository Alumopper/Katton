package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * Server-side living entity events for Fabric platform.
 *
 * This object provides events related to living entity lifecycle including
 * damage, death, and mob conversion events.
 */
@Suppress("unused")
object ServerLivingEntityEvent {

    fun initialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { a, b, c ->
            onAllowDamage(AllowDamageArg(a, b, c)).getOrElse { true }
        }

        ServerLivingEntityEvents.AFTER_DAMAGE.register { a, b, c, d, e ->
            onAfterDamage(AfterDamageArg(a, b, c, d, e))
        }

        ServerLivingEntityEvents.ALLOW_DEATH.register { a, b, c ->
            onAllowDeath(AllowDeathArg(a, b, c)).getOrElse { true }
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { a, b ->
            onAfterDeath(AfterDeathArg(a, b))
        }

        ServerLivingEntityEvents.MOB_CONVERSION.register { a, b, c ->
            onMobConversion(MobConversionArg(a, b, c))
        }
    }

    /**
     * Event triggered when a living entity is hurt.
     * Can be cancelled to prevent the damage.
     */
    @JvmField
    val onLivingHurt = createCancellableUnit<LivingHurtArg>()

    /**
     * Event triggered to allow or deny damage to a living entity.
     *
     * @return true to allow the damage, false to cancel it.
     */
    val onAllowDamage = createAll<AllowDamageArg>()

    /**
     * Event triggered after a living entity takes damage.
     */
    val onAfterDamage = createUnit<AfterDamageArg>()

    /**
     * Event triggered to allow or deny death of a living entity.
     *
     * @return true to allow the death, false to cancel it.
     */
    val onAllowDeath = createAll<AllowDeathArg>()

    /**
     * Event triggered after a living entity dies.
     */
    val onAfterDeath = createUnit<AfterDeathArg>()

    /**
     * Event triggered when a living entity falls.
     * Can be cancelled to prevent fall damage processing.
     */
    @JvmField
    val onLivingFall = createCancellableUnit<LivingFallArg>()

    /**
     * Event triggered when a mob is converted to another type
     * (e.g., zombie villager curing, piglin zombification).
     */
    val onMobConversion = createUnit<MobConversionArg>()
}
