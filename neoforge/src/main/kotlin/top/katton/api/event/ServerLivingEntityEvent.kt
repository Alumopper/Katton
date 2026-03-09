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

    val onLivingHurt = createCancellableUnit<LivingHurtArg>()

    @JvmField
    val onAllowDamage = createAll<ServerLivingAllowDamageArg>()

    @JvmField
    val onAfterDamage = createUnit<ServerLivingAfterDamageArg>()

    @JvmField
    val onAllowDeath = createAll<ServerLivingAllowDeathArg>()

    @JvmField
    val onAfterDeath = createUnit<ServerLivingAfterDeathArg>()

    val onLivingDrops = createCancellableUnit<LivingDropsArg>()

    val onLivingFall = createCancellableUnit<LivingFallArg>()

    val onLivingJump = createUnit<LivingJumpArg>()

    val onMobConversion = createUnit<MobConversionArg>()
}