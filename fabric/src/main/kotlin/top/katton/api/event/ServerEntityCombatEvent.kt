package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * Server entity combat events for Fabric platform.
 *
 * This object provides events related to entity combat including
 * entity kills, critical hits, and shield blocking.
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
     * Event triggered after an entity kills another entity.
     * This is a notification event that cannot be cancelled.
     */
    val onAfterKilledOtherEntity = createUnit<AfterKilledOtherEntityArg>()

    // === Shield Block ===

    /**
     * Event triggered when an entity blocks with a shield.
     * Can be used to modify the amount of damage blocked.
     *
     * @return The amount of damage that should be blocked.
     */
    @JvmField
    val onShieldBlock = createAll<ShieldBlockArg>()
}
