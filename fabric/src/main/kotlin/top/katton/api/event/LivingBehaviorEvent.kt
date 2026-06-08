package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.minecraft.world.entity.player.Player
import top.katton.bridger.EventResult
import top.katton.util.*

/**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
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
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onElytraAllow = createAll<ElytraAllowArg>()

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onElytraCustom = createAll<ElytraCustomArg>()

    // === Sleep Events ===

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onAllowSleeping = createFirstNotNullOfOrNull<AllowSleepingArg, Player.BedSleepingProblem?>()

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onStartSleeping = createUnit<SleepingArg>()

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onStopSleeping = createUnit<SleepingArg>()

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onAllowBed = createReturnIfNot<AllowBedArg, EventResult>(EventResult.PASS)

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onAllowNearbyMonsters = createReturnIfNot<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onAllowResettingTime = createAll<AllowResettingTimeArg>()

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onModifySleepingDirection = create { events ->
        { arg: ModifySleepingDirectionArg ->
            var d = arg.direction
            events.forEach { e -> d = e(arg.copy(direction = d)) }
            d
        }
    }

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onAllowSettingSpawn = createAll<AllowSleepingArg>()

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onSetBedOccupationState = createAny<SetBedOccupationStateArg>()

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    val onModifyWakeUpPosition = create { events ->
        { arg: ModifyWakeUpPositionArg ->
            var p = arg.wakeUpPos
            events.forEach { e -> p = e(arg.copy(wakeUpPos = p)) }
            p
        }
    }

    /**
 * %en
 * Living behavior events for Fabric platform.
 *
 * This object provides events related to mob behaviors including taming,
 * breeding, sleeping, elytra flight, and bed interaction.
 *
 * %zh
 * Fabric 平台的生物行为事件。
 *
 * 此对象提供与生物行为相关的事件，包括驯服、繁殖、睡眠、鞘翅飞行以及床交互。
 */
    @JvmField
    val onPlayerWakeUp = createUnit<PlayerWakeUpArg>()
}
