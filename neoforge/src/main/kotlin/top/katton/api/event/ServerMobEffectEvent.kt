@file:Suppress("unused")

package top.katton.api.event

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.MobEffectEvent
import top.katton.Katton
import top.katton.util.CancellableEventArg
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit
import top.katton.util.setCancel

/**
 * Mob effect events for NeoForge platform.
 *
 * This object provides events related to mob effects (potions) including
 * adding, removing, expiring, and checking applicability of effects.
 */
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerMobEffectEvent {

    @SubscribeEvent
    private fun handleMobEffectApplicable(e: MobEffectEvent.Applicable) {
        onMobEffectApplicable(
            MobEffectApplicableArg(e.entity, e.effectInstance)
        )
    }

    @SubscribeEvent
    private fun handleMobEffectAdd(e: MobEffectEvent.Added) {
        onMobEffectAdd(
            MobEffectAddArg(e.entity, e.effectInstance, e.effectSource)
        )
    }

    @SubscribeEvent
    private fun handleMobEffectRemove(e: MobEffectEvent.Remove) {
        onMobEffectRemove(
            MobEffectRemoveArg(e.entity, e.effectInstance)
        )
        setCancel(onMobEffectRemove, e)
    }

    @SubscribeEvent
    private fun handleMobEffectExpire(e: MobEffectEvent.Expired) {
        onMobEffectExpire(
            MobEffectExpireArg(e.entity, e.effectInstance)
        )
        setCancel(onMobEffectExpire, e)
    }

    /**
     * Event triggered to check if a mob effect is applicable to an entity.
     */
    val onMobEffectApplicable = createUnit<MobEffectApplicableArg>()

    /**
     * Event triggered when a mob effect is added to an entity.
     */
    val onMobEffectAdd = createUnit<MobEffectAddArg>()

    /**
     * Event triggered when a mob effect is removed from an entity.
     * Can be cancelled to prevent removal.
     */
    val onMobEffectRemove = createCancellableUnit<MobEffectRemoveArg>()

    /**
     * Event triggered when a mob effect expires on an entity.
     * Can be cancelled to prevent expiration.
     */
    val onMobEffectExpire = createCancellableUnit<MobEffectExpireArg>()

    /**
     * Argument class for mob effect applicable events.
     *
     * @property entity The living entity being checked
     * @property effect The effect instance being checked
     */
    data class MobEffectApplicableArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance
    )

    /**
     * Argument class for mob effect add events.
     *
     * @property entity The living entity receiving the effect
     * @property effect The effect instance being added
     * @property source The entity that caused the effect (can be null)
     */
    data class MobEffectAddArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance,
        val source: Entity?
    )

    /**
     * Argument class for mob effect remove events.
     *
     * @property entity The living entity losing the effect
     * @property effect The effect instance being removed (can be null if removed by type)
     */
    data class MobEffectRemoveArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance?
    ): CancellableEventArg()

    /**
     * Argument class for mob effect expire events.
     *
     * @property entity The living entity whose effect expired
     * @property effect The effect instance that expired (can be null)
     */
    data class MobEffectExpireArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance?
    ): CancellableEventArg()

}
