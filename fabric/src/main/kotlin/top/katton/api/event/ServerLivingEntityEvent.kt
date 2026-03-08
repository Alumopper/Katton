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
            onAllowDamage(ServerLivingAllowDamageArg(a, b, c)).getOrElse { true }
        }

        ServerLivingEntityEvents.AFTER_DAMAGE.register { a, b, c, d, e ->
            onAfterDamage(ServerLivingAfterDamageArg(a, b, c, d, e))
        }

        ServerLivingEntityEvents.ALLOW_DEATH.register { a, b, c ->
            onAllowDeath(ServerLivingAllowDeathArg(a, b, c)).getOrElse { true }
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { a, b ->
            onAfterDeath(ServerLivingAfterDeathArg(a, b))
        }

        ServerLivingEntityEvents.MOB_CONVERSION.register { a, b, c ->
            onMobConversion(MobConversionArg(a, b, c))
        }
    }

    @JvmField
    val onLivingHurt = createCancellableUnit<LivingHurtArg>()

    val onAllowDamage = createAll<ServerLivingAllowDamageArg>()

    val onAfterDamage = createUnit<ServerLivingAfterDamageArg>()

    val onAllowDeath = createAll<ServerLivingAllowDeathArg>()

    val onAfterDeath = createUnit<ServerLivingAfterDeathArg>()

    @JvmField
    val onLivingFall = createCancellableUnit<LivingFallArg>()

    val onMobConversion = createUnit<MobConversionArg>()
}