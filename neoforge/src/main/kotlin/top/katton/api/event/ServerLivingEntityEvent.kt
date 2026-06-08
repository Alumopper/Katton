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
 * %en
 * Server-side living entity events for NeoForge platform.
 *
 * This object provides events related to living entity lifecycle including
 * damage, death, drops, falling, jumping, and mob conversion.
 *
 * %zh
 * NeoForge 平台的服务端生物实体事件。
 * 此对象提供与生物实体生命周期相关的事件，包括受伤、死亡、掉落物、坠落、跳跃和生物转化。
 */
@EventBusSubscriber(
    modid = top.katton.Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerLivingEntityEvent {

    @JvmStatic
    @SubscribeEvent
    private fun onLivingHurt(e: LivingIncomingDamageEvent) {
        onLivingHurt(LivingHurtArg(e.entity, e.source, e.amount))
        setCancel(onLivingHurt, e)
    }

    @JvmStatic
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

    @JvmStatic
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

    @JvmStatic
    @SubscribeEvent
    private fun onLivingJump(e: LivingEvent.LivingJumpEvent) {
        onLivingJump(LivingJumpArg(e.entity))
    }

    @JvmStatic
    @SubscribeEvent
    private fun onMobConversion(e: LivingConversionEvent.Post) {
        onMobConversion(MobConversionArg(e.entity as Mob, e.outcome as Mob, null))
    }

    /**
     * %en
     * Event triggered when a living entity is hurt.
     * Can be cancelled to prevent the damage.
     *
     * %zh
     * 当生物实体受到伤害时触发。
     * 可取消以阻止伤害。
     */
    val onLivingHurt = createCancellableUnit<LivingHurtArg>()

    /**
     * %en
     * Event triggered to allow or deny damage to a living entity.
     *
     * %zh
     * 用于允许或拒绝对生物实体造成伤害。
     * @return
     * %en to allow the damage, false to cancel it.
     * %zh 返回 true 以允许伤害，返回 false 以取消伤害。
     */
    @JvmField
    val onAllowDamage = createAll<AllowDamageArg>()

    /**
     * %en
     * Event triggered after a living entity takes damage.
     *
     * %zh
     * 当生物实体受到伤害后触发。
     */
    @JvmField
    val onAfterDamage = createUnit<AfterDamageArg>()

    /**
     * %en
     * Event triggered to allow or deny death of a living entity.
     *
     * %zh
     * 用于允许或拒绝生物实体死亡。
     * @return
     * %en to allow the death, false to cancel it.
     * %zh 返回 true 以允许死亡，返回 false 以取消死亡。
     */
    @JvmField
    val onAllowDeath = createAll<AllowDeathArg>()

    /**
     * %en
     * Event triggered after a living entity dies.
     *
     * %zh
     * 当生物实体死亡后触发。
     */
    @JvmField
    val onAfterDeath = createUnit<AfterDeathArg>()

    /**
     * %en
     * Event triggered when a living entity drops items upon death.
     * Can be cancelled to prevent drops.
     *
     * %zh
     * 当生物实体死亡并掉落物品时触发。
     * 可取消以阻止掉落。
     */
    val onLivingDrops = createCancellableUnit<LivingDropsArg>()

    /**
     * %en
     * Event triggered when a living entity falls.
     * Can be cancelled to prevent fall damage processing.
     *
     * %zh
     * 当生物实体坠落时触发。
     * 可取消以阻止坠落伤害处理。
     */
    val onLivingFall = createCancellableUnit<LivingFallArg>()

    /**
     * %en
     * Event triggered when a living entity jumps.
     *
     * %zh
     * 当生物实体跳跃时触发。
     */
    val onLivingJump = createUnit<LivingJumpArg>()

    /**
     * %en
     * Event triggered when a mob is converted to another type
     * (e.g., zombie villager curing, piglin zombification).
     *
     * %zh
     * 当生物转换为其他类型时触发。
     * 例如僵尸村民治愈或猪灵僵尸化。
     */
    val onMobConversion = createUnit<MobConversionArg>()
}
