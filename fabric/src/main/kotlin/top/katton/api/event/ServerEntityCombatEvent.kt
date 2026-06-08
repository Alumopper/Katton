package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * %en
 * Server entity combat events for Fabric platform.
 *
 * This object provides events related to entity combat including
 * entity kills, critical hits, and shield blocking.
 *
 * %zh
 * Fabric 平台的服务端实体战斗事件。
 * 此对象提供与实体战斗相关的事件，包括击杀、暴击和盾牌格挡。
 */
@Suppress("unused")
object ServerEntityCombatEvent {

    fun initialize() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register { a, b, c, d ->
            onAfterKilledOtherEntity(AfterKilledOtherEntityArg(a, b, c, d))
        }
    }

    // === Entity Combat ===

    /**
     * %en
     * Event triggered after an entity kills another entity.
     * This is a notification event that cannot be cancelled.
     *
     * %zh
     * 当实体击杀另一个实体之后触发。
     * 这是一个仅通知事件，不能取消。
     */
    val onAfterKilledOtherEntity = createUnit<AfterKilledOtherEntityArg>()

    // === Shield Block ===

    /**
     * %en
     * Event triggered when an entity blocks with a shield.
     * Can be used to modify the amount of damage blocked.
     *
     * %zh
     * 当实体使用盾牌格挡时触发。
     * 可用于修改被格挡的伤害量。
     * @return
     * %en amount of damage that should be blocked.
     * %zh 返回应被格挡的伤害量。
     */
    @JvmField
    val onShieldBlock = createAll<ShieldBlockArg>()
}
