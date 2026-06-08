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
 * %en
 * Mob effect events for NeoForge platform.
 *
 * This object provides events related to mob effects (potions) including
 * adding, removing, expiring, and checking applicability of effects.
 *
 * %zh
 * NeoForge 平台的状态效果事件。
 * 此对象提供与状态效果（药水）相关的事件，包括添加、移除、过期以及检查效果是否适用。
 */
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerMobEffectEvent {

    @JvmStatic
    @SubscribeEvent
    private fun handleMobEffectApplicable(e: MobEffectEvent.Applicable) {
        onMobEffectApplicable(
            MobEffectApplicableArg(e.entity, e.effectInstance)
        )
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleMobEffectAdd(e: MobEffectEvent.Added) {
        onMobEffectAdd(
            MobEffectAddArg(e.entity, e.effectInstance, e.effectSource)
        )
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleMobEffectRemove(e: MobEffectEvent.Remove) {
        onMobEffectRemove(
            MobEffectRemoveArg(e.entity, e.effectInstance)
        )
        setCancel(onMobEffectRemove, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleMobEffectExpire(e: MobEffectEvent.Expired) {
        onMobEffectExpire(
            MobEffectExpireArg(e.entity, e.effectInstance)
        )
        setCancel(onMobEffectExpire, e)
    }

    /**
     * %en
     * Event triggered to check if a mob effect is applicable to an entity.
     *
     * %zh
     * 当检查某个状态效果是否可以应用到实体时触发。
     */
    val onMobEffectApplicable = createUnit<MobEffectApplicableArg>()

    /**
     * %en
     * Event triggered when a mob effect is added to an entity.
     *
     * %zh
     * 当向实体添加状态效果时触发。
     */
    val onMobEffectAdd = createUnit<MobEffectAddArg>()

    /**
     * %en
     * Event triggered when a mob effect is removed from an entity.
     * Can be cancelled to prevent removal.
     *
     * %zh
     * 当从实体移除状态效果时触发。
     * 可取消以阻止移除。
     */
    val onMobEffectRemove = createCancellableUnit<MobEffectRemoveArg>()

    /**
     * %en
     * Event triggered when a mob effect expires on an entity.
     * Can be cancelled to prevent expiration.
     *
     * %zh
     * 当实体身上的状态效果过期时触发。
     * 可取消以阻止过期。
     */
    val onMobEffectExpire = createCancellableUnit<MobEffectExpireArg>()

    /**
     * %en
     * Argument class for mob effect applicable events.
     *
     * %zh
     * 状态效果适用性检查事件的参数类。
     * @property entity
     * %en The living entity being checked
     * %zh 正在检查的生物实体。
     * @property effect
     * %en The effect instance being checked
     * %zh 正在检查的效果实例。
     */
    data class MobEffectApplicableArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance
    )

    /**
     * %en
     * Argument class for mob effect add events.
     *
     * %zh
     * 状态效果添加事件的参数类。
     * @property entity
     * %en The living entity receiving the effect
     * %zh 正在接收效果的生物实体。
     * @property effect
     * %en The effect instance being added
     * %zh 正在添加的效果实例。
     * @property source
     * %en The entity that caused the effect (can be null)
     * %zh 造成该效果的实体，可为 null。
     */
    data class MobEffectAddArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance,
        val source: Entity?
    )

    /**
     * %en
     * Argument class for mob effect remove events.
     *
     * %zh
     * 状态效果移除事件的参数类。
     * @property entity
     * %en The living entity losing the effect
     * %zh 失去该效果的生物实体。
     * @property effect
     * %en The effect instance being removed (can be null if removed by type)
     * %zh 正在移除的效果实例。如果是按类型移除，这里可以为 null。
     */
    data class MobEffectRemoveArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance?
    ): CancellableEventArg()

    /**
     * %en
     * Argument class for mob effect expire events.
     *
     * %zh
     * 状态效果过期事件的参数类。
     * @property entity
     * %en The living entity whose effect expired
     * %zh 效果过期的生物实体。
     * @property effect
     * %en The effect instance that expired (can be null)
     * %zh 已过期的效果实例，可为 null。
     */
    data class MobEffectExpireArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance?
    ): CancellableEventArg()

}
