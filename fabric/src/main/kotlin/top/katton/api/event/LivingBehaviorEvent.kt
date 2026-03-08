package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.minecraft.world.entity.player.Player
import top.katton.bridger.EventResult
import top.katton.util.*

/**
 * Living entity behavior events (animal taming, baby spawning, elytra, sleep).
 */
@Suppress("unused")
object LivingBehaviorEvent {

    fun initialize() {
        EntityElytraEvents.ALLOW.register {
            onElytraAllow(EntityElytraAllowArg(it)).getOrElse { true }
        }

        EntityElytraEvents.CUSTOM.register {
            a, b ->
            onElytraCustom(EntityElytraCustomArg(a, b)).getOrElse { false }
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
            onAllowSettingSpawn(AllowSettingSpawnArg(a, b)).getOrElse { true }
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
    val onElytraAllow = createAll<EntityElytraAllowArg>()

    val onElytraCustom = createAll<EntityElytraCustomArg>()

    // === Sleep Events ===
    val onAllowSleeping = createFirstNotNullOfOrNull<AllowSleepingArg, Player.BedSleepingProblem?>()

    val onStartSleeping = createUnit<SleepingArg>()

    val onStopSleeping = createUnit<SleepingArg>()

    val onAllowBed = createReturnIfNot<AllowBedArg, EventResult>(EventResult.PASS)

    val onAllowNearbyMonsters = createReturnIfNot<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)

    val onAllowResettingTime = createAll<AllowResettingTimeArg>()

    val onModifySleepingDirection = create { events ->
        { arg: ModifySleepingDirectionArg ->
            var d = arg.direction
            events.forEach { e -> d = e(arg.copy(direction = d)) }
            d
        }
    }

    val onAllowSettingSpawn = createAll<AllowSettingSpawnArg>()

    val onSetBedOccupationState = createAny<SetBedOccupationStateArg>()

    val onModifyWakeUpPosition = create { events ->
        { arg: ModifyWakeUpPositionArg ->
            var p = arg.wakeUpPos
            events.forEach { e -> p = e(arg.copy(wakeUpPos = p)) }
            p
        }
    }

    @JvmField
    val onPlayerWakeUp = createUnit<PlayerWakeUpArg>()
}
