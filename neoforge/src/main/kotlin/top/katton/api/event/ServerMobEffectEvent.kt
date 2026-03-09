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

    val onMobEffectApplicable = createUnit<MobEffectApplicableArg>()

    val onMobEffectAdd = createUnit<MobEffectAddArg>()

    val onMobEffectRemove = createCancellableUnit<MobEffectRemoveArg>()

    val onMobEffectExpire = createCancellableUnit<MobEffectExpireArg>()

    data class MobEffectApplicableArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance
    )

    data class MobEffectAddArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance,
        val source: Entity?
    )

    data class MobEffectRemoveArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance?
    ): CancellableEventArg()

    data class MobEffectExpireArg(
        val entity: LivingEntity,
        val effect: MobEffectInstance?
    ): CancellableEventArg()

}
