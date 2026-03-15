package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent
import top.katton.Katton
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * Server entity combat events for NeoForge platform.
 *
 * This object provides events related to entity combat including
 * critical hits, shield blocking, and entity kills.
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

    /**
     * Event triggered after an entity kills another entity.
     * This is a notification event that cannot be cancelled.
     */
    @JvmField
    val onAfterKilledOtherEntity = createUnit<AfterKilledOtherEntityArg>()

    // === Critical Hits ===

    /**
     * Event triggered when a critical hit is performed.
     */
    val onCriticalHit = createUnit<CriticalHitArg>()

    // === Shield Block ===

    /**
     * Event triggered when an entity blocks with a shield.
     * Can be used to modify the amount of damage blocked.
     *
     * @return The amount of damage that should be blocked.
     */
    val onShieldBlock = createAll<ShieldBlockArg>()
}
