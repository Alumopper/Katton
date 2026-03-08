package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * Server entity combat events (killed other entity, critical hits, shield blocking).
 */
@Suppress("unused")
object ServerEntityCombatEvent {

    fun initialize() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register { a, b, c, d ->
            onAfterKilledOtherEntity(AfterKilledOtherEntityArg(a, b, c, d))
        }
    }

    // === Entity Combat ===
    val onAfterKilledOtherEntity = createUnit<AfterKilledOtherEntityArg>()

    // === Shield Block ===
    @JvmField
    val onShieldBlock = createAll<ShieldBlockArg>()
}