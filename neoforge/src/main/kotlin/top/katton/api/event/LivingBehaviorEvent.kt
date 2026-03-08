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
import top.katton.util.create
import top.katton.util.createAll
import top.katton.util.createAny
import top.katton.util.createCancellableUnit
import top.katton.util.createReturnIfNot
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit
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
    private fun onAnimalTame(e: AnimalTameEvent) {
        val arg = AnimalTameArg(e.animal, e.tamer)
        onAnimalTame(arg)
        setCancel(onAnimalTame, e)
    }

    @SubscribeEvent
    private fun onBabySpawn(e: BabyEntitySpawnEvent) {
        val arg = BabySpawnArg(e.parentA, e.parentB, e.child)
        onBabySpawn(arg)
        setCancel(onBabySpawn, e)
    }

    @SubscribeEvent
    private fun onPlayerWakeUp(e: PlayerWakeUpEvent) {
        val arg = PlayerWakeUpArg(e.entity, e.wakeImmediately(), e.updateLevel())
        onPlayerWakeUp(arg)
    }

    val onAnimalTame = createCancellableUnit<AnimalTameArg>()

    // === Baby Spawning ===
    val onBabySpawn = createCancellableUnit<BabySpawnArg>()

    // === Elytra Events ===
    @JvmField
    val onElytraAllow = createAll<EntityElytraAllowArg>()

    @JvmField
    val onElytraCustom = createAny<EntityElytraCustomArg>()

    // === Sleep Events ===
    @JvmField
    val onAllowSleeping = createFirstNotNullOfOrNull<AllowSleepingArg, Player.BedSleepingProblem?>()

    @JvmField
    val onStartSleeping = createUnit<SleepingArg>()

    @JvmField
    val onStopSleeping = createUnit<SleepingArg>()

    @JvmField
    val onAllowBed = createReturnIfNot<AllowBedArg, EventResult>(EventResult.PASS)

    @JvmField
    val onAllowNearbyMonsters = createReturnIfNot<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)

    @JvmField
    val onAllowResettingTime = createAll<AllowResettingTimeArg>()

    @JvmField
    val onModifySleepingDirection = create { events ->
        { arg: ModifySleepingDirectionArg ->
            var dir = arg.direction
            events.forEach {
                e -> dir = e(arg.copy(direction = dir))
            }
            dir
        }
    }

    @JvmField
    val onAllowSettingSpawn = createAll<AllowSettingSpawnArg>()

    @JvmField
    val onSetBedOccupationState = createAll<SetBedOccupationStateArg>()

    @JvmField
    val onModifyWakeUpPosition = create { events ->
        { arg: ModifyWakeUpPositionArg ->
            var p = arg.wakeUpPos
            events.forEach { e -> p = e(arg.copy(wakeUpPos = p)) }
            p
        }
    }

    val onPlayerWakeUp = createUnit<PlayerWakeUpArg>()
}
