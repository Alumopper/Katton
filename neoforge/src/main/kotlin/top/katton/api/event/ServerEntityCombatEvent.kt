package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent
import top.katton.Katton
import top.katton.util.DelegateEvent

/**
 * Server entity combat events (critical hits, shield blocking, and entity combat).
 * Note: EntityCombat AFTER_KILLED event is placeholder for NeoForge.
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerEntityCombatEvent {

    @SubscribeEvent
    private fun handleCriticalHit(e: CriticalHitEvent) {
        onCriticalHit(CriticalHitArg(e.entity, e.target, e.isVanillaCritical))
    }

    @SubscribeEvent
    private fun handleShieldBlock(e: LivingShieldBlockEvent) {
        val result = onShieldBlock(ShieldBlockArg(e.entity, e.damageSource, e.blockedDamage, e.originalBlock))
        val blocked = result.getOrNull()
        if (blocked != null) {
            e.blocked = blocked
        }
    }

    // === Entity Combat ===
    @JvmField
    val onAfterKilledOtherEntity = createUnitEvent<AfterKilledOtherEntityArg>()

    // === Critical Hits ===
    val onCriticalHit = createUnitEvent<CriticalHitArg>()

    // === Shield Block ===
    val onShieldBlock = createAllEvent<ShieldBlockArg>()

    private fun <T> createUnitEvent() = DelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }

    private fun <T> createAllEvent() = DelegateEvent<T, Boolean> { events ->
        { arg -> events.all { handler -> handler(arg) } }
    }
}
