package top.katton.api.event

import net.minecraft.world.entity.player.Player
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent
import top.katton.Katton
import top.katton.bridger.EventResult
import top.katton.util.CancellableDelegateEvent
import top.katton.util.CancellableEventArg
import top.katton.util.DelegateEvent
import top.katton.util.setCancel

/**
 * Living entity behavior events (animal taming, baby spawning, elytra, sleep).
 * Note: NeoForge has native events for some, placeholders for others.
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

    val onAnimalTame = createCancellableUnitEvent<AnimalTameArg>()

    // === Baby Spawning ===
    val onBabySpawn = createCancellableUnitEvent<BabySpawnArg>()

    // === Elytra Events ===
    @JvmField
    val onElytraAllow = createAllEvent<EntityElytraAllowArg>()

    @JvmField
    val onElytraCustom = createAnyEvent<EntityElytraCustomArg>()

    // === Sleep Events ===
    @JvmField
    val onAllowSleeping = createFirstNotNullOfOrNullEvent<AllowSleepingArg, Player.BedSleepingProblem?>()

    @JvmField
    val onStartSleeping = createUnitEvent<SleepingArg>()

    @JvmField
    val onStopSleeping = createUnitEvent<SleepingArg>()

    @JvmField
    val onAllowBed = createReturnIfNotEvent<AllowBedArg, EventResult>(EventResult.PASS)

    @JvmField
    val onAllowNearbyMonsters = createReturnIfNotEvent<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)

    @JvmField
    val onAllowResettingTime = createAllEvent<AllowResettingTimeArg>()

    @JvmField
    val onModifySleepingDirection = createEvent { events: Array<(ModifySleepingDirectionArg) -> net.minecraft.core.Direction?> ->
        { arg: ModifySleepingDirectionArg ->
            var dir = arg.direction
            events.forEach {
                handler -> dir = handler(arg.copy(direction = dir))
            }
            dir
        }
    }

    @JvmField
    val onAllowSettingSpawn = createAllEvent<AllowSettingSpawnArg>()

    @JvmField
    val onSetBedOccupationState = createAnyEvent<SetBedOccupationStateArg>()

    @JvmField
    val onModifyWakeUpPosition = createEvent { events: Array<(ModifyWakeUpPositionArg) -> net.minecraft.world.phys.Vec3?> ->
        { arg: ModifyWakeUpPositionArg ->
            var p = arg.wakeUpPos
            events.forEach { handler -> p = handler(arg.copy(wakeUpPos = p)) }
            p
        }
    }

    val onPlayerWakeUp = createUnitEvent<PlayerWakeUpArg>()

    private fun <T, R> createEvent(invoker: (Array<(T) -> R>) -> (T) -> R) = DelegateEvent(invoker)

    private fun <T> createUnitEvent() = DelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }

    private fun <T : CancellableEventArg> createCancellableUnitEvent() = CancellableDelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }

    private fun <T> createAllEvent() = DelegateEvent<T, Boolean> { events ->
        { arg -> events.all { handler -> handler(arg) } }
    }

    private fun <T> createAnyEvent() = DelegateEvent<T, Boolean> { events ->
        { arg -> events.any { handler -> handler(arg) } }
    }

    private fun <T, R> createReturnIfNotEvent(unexpectedValue: R) = DelegateEvent<T, R> { events ->
        { arg ->
            var finalResult = unexpectedValue
            for (handler in events) {
                val result = handler(arg)
                if (result != unexpectedValue) {
                    finalResult = result
                    break
                }
            }
            finalResult
        }
    }

    private fun <T, R> createFirstNotNullOfOrNullEvent() = DelegateEvent<T, R?> { events ->
        { arg -> events.firstNotNullOfOrNull { handler -> handler(arg) } }
    }
}
