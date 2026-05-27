package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.minecraft.world.entity.player.Player
import top.katton.bridger.EventResult
import top.katton.util.*

/**
 * Living entity behavior events for Fabric platform.
 *
 * This object provides events related to living entity behaviors including
 * elytra flight, sleeping, animal taming, and baby spawning.
 */
@Suppress("unused")
object LivingBehaviorEvent {

    fun initialize() {
        EntityElytraEvents.ALLOW.register {
            onElytraAllow(ElytraAllowArg(it)).getOrElse { true }
        }

        EntityElytraEvents.CUSTOM.register {
            a, b ->
            onElytraCustom(ElytraCustomArg(a, b)).getOrElse { false }
        }

        EntitySleepEvents.ALLOW_SLEEPING.register {
            a, b ->
            onAllowSleeping(AllowSleepingArg(a, b)).getOrNull()
        }

        EntitySleepEvents.START_SLEEPING.register {
            a, b ->
            onStartSleeping(SleepingArg(a, b))
        }

        EntitySleepEvents.STOP_SLEEPING.register {
            a, b ->
            onStopSleeping(SleepingArg(a, b))
        }

        EntitySleepEvents.ALLOW_BED.register {
            a, b, c, d ->
            onAllowBed(AllowBedArg(a, b, c, d))
                .getOrElse { EventResult.PASS }.toFabric()
        }

        EntitySleepEvents.ALLOW_NEARBY_MONSTERS.register {
            a, b, c ->
            onAllowNearbyMonsters(AllowNearbyMonstersArg(a, b, c))
                .getOrElse { EventResult.PASS }.toFabric()
        }

        EntitySleepEvents.ALLOW_RESETTING_TIME.register {
            onAllowResettingTime(AllowResettingTimeArg(it)).getOrElse { true }
        }

        EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register {
            a, b, c ->
            onModifySleepingDirection(ModifySleepingDirectionArg(a, b, c)).getOrElse { c }
        }

        EntitySleepEvents.ALLOW_SETTING_SPAWN.register {
            a, b ->
            onAllowSettingSpawn(AllowSleepingArg(a, b)).getOrElse { true }
        }

        EntitySleepEvents.SET_BED_OCCUPATION_STATE.register {
            a, b, c, d ->
            onSetBedOccupationState(SetBedOccupationStateArg(a, b, c, d)).getOrElse { false }
        }

        EntitySleepEvents.MODIFY_WAKE_UP_POSITION.register {
            a, b, c, d ->
            onModifyWakeUpPosition(ModifyWakeUpPositionArg(a, b, c, d)).getOrElse { d }
        }

    }

    // === Elytra Events ===

    /**
     * Event triggered to check if an entity is allowed to use elytra.
     *
     * @return true to allow elytra usage, false to deny.
     */
    val onElytraAllow = createAll<ElytraAllowArg>()

    /**
     * Event triggered to provide custom elytra flight behavior.
     *
     * @return true if custom behavior is applied, false to use default.
     */
    val onElytraCustom = createAll<ElytraCustomArg>()

    // === Sleep Events ===

    /**
     * Event triggered to check if a player is allowed to sleep.
     *
     * @return BedSleepingProblem if sleep is denied, null to allow.
     */
    val onAllowSleeping = createFirstNotNullOfOrNull<AllowSleepingArg, Player.BedSleepingProblem?>()

    /**
     * Event triggered when a player starts sleeping.
     */
    val onStartSleeping = createUnit<SleepingArg>()

    /**
     * Event triggered when a player stops sleeping.
     */
    val onStopSleeping = createUnit<SleepingArg>()

    /**
     * Event triggered to check if a player is allowed to use a bed.
     *
     * @return EventResult indicating the result of the check.
     */
    val onAllowBed = createReturnIfNot<AllowBedArg, EventResult>(EventResult.PASS)

    /**
     * Event triggered to check if nearby monsters prevent sleeping.
     *
     * @return EventResult indicating whether monsters should prevent sleep.
     */
    val onAllowNearbyMonsters = createReturnIfNot<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)

    /**
     * Event triggered to check if time should reset after sleeping.
     *
     * @return true to allow time reset, false to prevent it.
     */
    val onAllowResettingTime = createAll<AllowResettingTimeArg>()

    /**
     * Event triggered to modify the sleeping direction when entering a bed.
     *
     * @return The modified direction for the player to face.
     */
    val onModifySleepingDirection = create { events ->
        { arg: ModifySleepingDirectionArg ->
            var d = arg.direction
            events.forEach { e -> d = e(arg.copy(direction = d)) }
            d
        }
    }

    /**
     * Event triggered to check if spawn point should be set when sleeping.
     *
     * @return true to allow setting spawn, false to prevent it.
     */
    val onAllowSettingSpawn = createAll<AllowSleepingArg>()

    /**
     * Event triggered to set the bed occupation state.
     *
     * @return true if the state was handled, false for default behavior.
     */
    val onSetBedOccupationState = createAny<SetBedOccupationStateArg>()

    /**
     * Event triggered to modify the player's wake-up position.
     *
     * @return The modified Vec3 wake-up position.
     */
    val onModifyWakeUpPosition = create { events ->
        { arg: ModifyWakeUpPositionArg ->
            var p = arg.wakeUpPos
            events.forEach { e -> p = e(arg.copy(wakeUpPos = p)) }
            p
        }
    }

    /**
     * Event triggered when a player wakes up from sleeping.
     */
    @JvmField
    val onPlayerWakeUp = createUnit<PlayerWakeUpArg>()
}
