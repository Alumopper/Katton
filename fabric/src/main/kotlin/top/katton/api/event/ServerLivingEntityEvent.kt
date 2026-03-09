package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * Server-side living entity events (allow damage, after damage, allow death, after death, conversion).
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

    @JvmField
    val onLivingHurt = createCancellableUnit<LivingHurtArg>()

    val onAllowDamage = createAll<AllowDamageArg>()

    val onAfterDamage = createUnit<AfterDamageArg>()

    val onAllowDeath = createAll<AllowDeathArg>()

    val onAfterDeath = createUnit<AfterDeathArg>()

    @JvmField
    val onLivingFall = createCancellableUnit<LivingFallArg>()

    val onMobConversion = createUnit<MobConversionArg>()
}