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
 * %en
 * Server entity combat events for NeoForge platform.
 *
 * This object provides events related to entity combat including
 * critical hits, shield blocking, and entity kills.
 *
 * %zh
 * NeoForge 平台的服务端实体战斗事件。
 * 此对象提供与实体战斗相关的事件，包括暴击、盾牌格挡和击杀。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerEntityCombatEvent {

    @JvmStatic
    @SubscribeEvent
    private fun handleCriticalHit(e: CriticalHitEvent) {
        onCriticalHit(CriticalHitArg(e.entity, e.target, e.isVanillaCritical))
    }

    @JvmStatic
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
     * %en
     * Event triggered after an entity kills another entity.
     * This is a notification event that cannot be cancelled.
     *
     * %zh
     * 当一个实体击杀另一个实体后触发。
     * 这是一个通知事件，不能取消。
     */
    @JvmField
    val onAfterKilledOtherEntity = createUnit<AfterKilledOtherEntityArg>()

    // === Critical Hits ===

    /**
     * %en
     * Event triggered when a critical hit is performed.
     *
     * %zh
     * 当发生暴击时触发。
     */
    val onCriticalHit = createUnit<CriticalHitArg>()

    // === Shield Block ===

    /**
     * %en
     * Event triggered when an entity blocks with a shield.
     * Can be used to modify the amount of damage blocked.
     *
     * %zh
     * 当实体使用盾牌格挡时触发。
     * 可用于修改格挡的伤害量。
     * @return
     * %en amount of damage that should be blocked.
     * %zh 应当被格挡的伤害量。
     */
    val onShieldBlock = createAll<ShieldBlockArg>()
}
