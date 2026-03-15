@file:Suppress("unused")

package top.katton.api.event

import net.minecraft.world.entity.Mob
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent
import net.neoforged.neoforge.event.entity.living.LivingEvent
import net.neoforged.neoforge.event.entity.living.LivingFallEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit
import top.katton.util.setCancel

/**
 * Server-side living entity events for NeoForge platform.
 *
 * This object provides events related to living entity lifecycle including
 * damage, death, drops, falling, jumping, and mob conversion.
 */
@EventBusSubscriber(
    modid = top.katton.Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerLivingEntityEvent {

    @SubscribeEvent
    private fun onLivingHurt(e: LivingIncomingDamageEvent) {
        onLivingHurt(LivingHurtArg(e.entity, e.source, e.amount))
        setCancel(onLivingHurt, e)
    }

    @SubscribeEvent
    private fun onLivingDrops(e: LivingDropsEvent) {
        onLivingDrops(
            LivingDropsArg(
                e.entity,
                e.source,
                e.drops.map { it.item }
            )
        )
        setCancel(onLivingDrops, e)
    }

    @SubscribeEvent
    private fun onLivingFall(e: LivingFallEvent) {
        onLivingFall(
            LivingFallArg(
                e.entity,
                e.distance,
                e.damageMultiplier
            )
        )
        setCancel(onLivingFall, e)
    }

    @SubscribeEvent
    private fun onLivingJump(e: LivingEvent.LivingJumpEvent) {
        onLivingJump(LivingJumpArg(e.entity))
    }

    @SubscribeEvent
    private fun onMobConversion(e: LivingConversionEvent.Post) {
        onMobConversion(MobConversionArg(e.entity as Mob, e.outcome as Mob, null))
    }

    /**
     * Event triggered when a living entity is hurt.
     * Can be cancelled to prevent the damage.
     */
    val onLivingHurt = createCancellableUnit<LivingHurtArg>()

    /**
     * Event triggered to allow or deny damage to a living entity.
     *
     * @return true to allow the damage, false to cancel it.
     */
    @JvmField
    val onAllowDamage = createAll<AllowDamageArg>()

    /**
     * Event triggered after a living entity takes damage.
     */
    @JvmField
    val onAfterDamage = createUnit<AfterDamageArg>()

    /**
     * Event triggered to allow or deny death of a living entity.
     *
     * @return true to allow the death, false to cancel it.
     */
    @JvmField
    val onAllowDeath = createAll<AllowDeathArg>()

    /**
     * Event triggered after a living entity dies.
     */
    @JvmField
    val onAfterDeath = createUnit<AfterDeathArg>()

    /**
     * Event triggered when a living entity drops items upon death.
     * Can be cancelled to prevent drops.
     */
    val onLivingDrops = createCancellableUnit<LivingDropsArg>()

    /**
     * Event triggered when a living entity falls.
     * Can be cancelled to prevent fall damage processing.
     */
    val onLivingFall = createCancellableUnit<LivingFallArg>()

    /**
     * Event triggered when a living entity jumps.
     */
    val onLivingJump = createUnit<LivingJumpArg>()

    /**
     * Event triggered when a mob is converted to another type
     * (e.g., zombie villager curing, piglin zombification).
     */
    val onMobConversion = createUnit<MobConversionArg>()
}
