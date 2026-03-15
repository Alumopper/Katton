package top.katton.api.event

import net.minecraft.world.entity.player.Player
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent
import top.katton.Katton
import top.katton.api.event.LivingBehaviorEvent.onPlayerWakeUp
import top.katton.bridger.EventResult
import top.katton.util.CancellableDelegateEvent
import top.katton.util.CancellableEventArg
import top.katton.util.DelegateEvent
import top.katton.util.create
import top.katton.util.createAll
import top.katton.util.createAny
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createUnit
import top.katton.util.setCancel

/**
 * Living entity behavior events for NeoForge platform.
 *
 * This object provides events related to living entity behaviors including
 * animal taming, baby spawning, elytra flight, and sleeping.
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object LivingBehaviorEvent {

    // === Animal Taming ===

    @SubscribeEvent
    private fun handleAnimalTame(e: AnimalTameEvent) {
        val arg = AnimalTameArg(e.animal, e.tamer)
        onAnimalTame(arg)
        setCancel(onAnimalTame, e)
    }

    @SubscribeEvent
    private fun handleBabySpawn(e: BabyEntitySpawnEvent) {
        val arg = BabySpawnArg(e.parentA, e.parentB, e.child)
        onBabySpawn(arg)
        setCancel(onBabySpawn, e)
    }

    @SubscribeEvent
    private fun handlePlayerWakeUp(e: PlayerWakeUpEvent) {
        val arg = PlayerWakeUpArg(e.entity, e.wakeImmediately(), e.updateLevel())
        onPlayerWakeUp(arg)
    }

    /**
     * Event triggered when an animal is being tamed.
     * Can be cancelled to prevent taming.
     */
    val onAnimalTame = createCancellableUnit<AnimalTameArg>()

    // === Baby Spawning ===

    /**
     * Event triggered when a baby entity is spawning (breeding).
     * Can be cancelled to prevent the spawn.
     */
    val onBabySpawn = createCancellableUnit<BabySpawnArg>()

    // === Elytra Events ===

    /**
     * Event triggered to check if an entity is allowed to use elytra.
     *
     * @return true to allow elytra usage, false to deny.
     */
    @JvmField
    val onElytraAllow = createAll<ElytraAllowArg>()

    /**
     * Event triggered to provide custom elytra flight behavior.
     *
     * @return true if custom behavior is applied, false to use default.
     */
    @JvmField
    val onElytraCustom = createAny<ElytraCustomArg>()

    // === Sleep Events ===

    /**
     * Event triggered to check if a player is allowed to sleep.
     *
     * @return BedSleepingProblem if sleep is denied, null to allow.
     */
    @JvmField
    val onAllowSleeping = createFirstNotNullOfOrNull<AllowSleepingArg, Player.BedSleepingProblem?>()

    /**
     * Event triggered when a player starts sleeping.
     */
    @JvmField
    val onStartSleeping = createUnit<SleepingArg>()

    /**
     * Event triggered when a player stops sleeping.
     */
    @JvmField
    val onStopSleeping = createUnit<SleepingArg>()

    /**
     * Event triggered to check if a player is allowed to use a bed.
     *
     * @return EventResult indicating the result of the check.
     */
    @JvmField
    val onAllowBed = createReturnIfNot<AllowBedArg, EventResult>(EventResult.PASS)

    /**
     * Event triggered to check if nearby monsters prevent sleeping.
     *
     * @return EventResult indicating whether monsters should prevent sleep.
     */
    @JvmField
    val onAllowNearbyMonsters = createReturnIfNot<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)

    /**
     * Event triggered to check if time should reset after sleeping.
     *
     * @return true to allow time reset, false to prevent it.
     */
    @JvmField
    val onAllowResettingTime = createAll<AllowResettingTimeArg>()

    /**
     * Event triggered to modify the sleeping direction when entering a bed.
     *
     * @return The modified direction for the player to face.
     */
    @JvmField
    val onModifySleepingDirection = create { events: Array<(ModifySleepingDirectionArg) -> net.minecraft.core.Direction?> ->
        { arg: ModifySleepingDirectionArg ->
            var dir = arg.direction
            events.forEach {
                handler -> dir = handler(arg.copy(direction = dir))
            }
            dir
        }
    }

    /**
     * Event triggered to check if spawn point should be set when sleeping.
     *
     * @return true to allow setting spawn, false to prevent it.
     */
    @JvmField
    val onAllowSettingSpawn = createAll<AllowSleepingArg>()

    /**
     * Event triggered to set the bed occupation state.
     *
     * @return true if the state was handled, false for default behavior.
     */
    @JvmField
    val onSetBedOccupationState = createAny<SetBedOccupationStateArg>()

    /**
     * Event triggered to modify the player's wake-up position.
     *
     * @return The modified Vec3 wake-up position.
     */
    @JvmField
    val onModifyWakeUpPosition = create { events: Array<(ModifyWakeUpPositionArg) -> net.minecraft.world.phys.Vec3?> ->
        { arg: ModifyWakeUpPositionArg ->
            var p = arg.wakeUpPos
            events.forEach { handler -> p = handler(arg.copy(wakeUpPos = p)) }
            p
        }
    }

    /**
     * Event triggered when a player wakes up from sleeping.
     */
    val onPlayerWakeUp = createUnit<PlayerWakeUpArg>()

}
