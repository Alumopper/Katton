package top.katton.api.event

import io.papermc.paper.event.player.PlayerDeepSleepEvent
import io.papermc.paper.event.player.PlayerBedFailEnterEvent
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.player.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.entity.EntityTameEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerBedLeaveEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.bridger.EventResult
import top.katton.paper.PaperNmsBridge
import top.katton.util.*

object LivingBehaviorEvent {
    @JvmField
    val onAnimalTame = createCancellableUnit<AnimalTameArg>()

    @JvmField
    val onBabySpawn = createCancellableUnit<BabySpawnArg>()

    @JvmField
    val onElytraAllow = createAll<ElytraAllowArg>()

    @JvmField
    val onElytraCustom = createAny<ElytraCustomArg>()

    @JvmField
    val onAllowSleeping = createFirstNotNullOfOrNull<AllowSleepingArg, Player.BedSleepingProblem?>()

    @JvmField
    val onStartSleeping = createUnit<SleepingArg>()

    @JvmField
    val onStopSleeping = createUnit<SleepingArg>()

    @JvmField
    val onAllowBed = createReturnIfNot<AllowBedArg, EventResult>(EventResult.PASS)

//    @JvmField
//    val onAllowNearbyMonsters = createReturnIfNot<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)

    @JvmField
    val onAllowResettingTime = createAll<AllowResettingTimeArg>()

//    @JvmField
//    val onModifySleepingDirection = create { events ->
//        { arg: ModifySleepingDirectionArg ->
//            var direction = arg.direction
//            events.forEach { direction = it.handler(arg.copy(direction = direction)) }
//            direction
//        }
//    }

    @JvmField
    val onAllowSettingSpawn = createAll<AllowSettingSpawnArg>()

//    @JvmField
//    val onSetBedOccupationState = createAny<SetBedOccupationStateArg>()

//    @JvmField
//    val onModifyWakeUpPosition = create { events ->
//        { arg: ModifyWakeUpPositionArg ->
//            var wakeUpPos = arg.wakeUpPos
//            events.forEach { wakeUpPos = it.handler(arg.copy(wakeUpPos = wakeUpPos)) }
//            wakeUpPos
//        }
//    }

    @JvmField
    val onPlayerWakeUp = createUnit<PlayerWakeUpArg>()

    @JvmField
    val onBedFailEnter = createUnit<Any>() // PlayerBedFailEnterEvent (Paper-specific, no fabric equivalent)

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onTame(event: EntityTameEvent) {
                val owner = event.owner as? org.bukkit.entity.Player ?: return
                val entity = PaperNmsBridge.toNmsEntity(event.entity) as? Animal ?: return
                val arg = AnimalTameArg(entity, PaperNmsBridge.toNmsPlayer(owner))
                onAnimalTame(arg)
                if (arg.isCancelled()) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onBreed(event: EntityBreedEvent) {
                val child = PaperNmsBridge.toNmsEntity(event.entity) as? AgeableMob
                val arg = BabySpawnArg(
                    PaperNmsBridge.toNmsLivingEntity(event.mother),
                    PaperNmsBridge.toNmsLivingEntity(event.father),
                    child
                )
                onBabySpawn(arg)
                if (arg.isCancelled()) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onBedEnter(event: PlayerBedEnterEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val pos = PaperNmsBridge.toNmsBlockPos(event.bed.location)
                val bedState = PaperNmsBridge.toNmsBlockState(event.bed)
                val allowBed = onAllowBed(
                    AllowBedArg(player, pos, bedState, true)
                ).getOrElse { EventResult.PASS }
                if (allowBed == EventResult.DENY) {
                    event.isCancelled = true
                    return
                }
                if (onAllowSleeping(AllowSleepingArg(player, pos)).getOrNull() != null) {
                    event.isCancelled = true
                    return
                }

                if (!onAllowSettingSpawn(AllowSettingSpawnArg(player, pos)).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }

                onStartSleeping(SleepingArg(player, pos))
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onBedLeave(event: PlayerBedLeaveEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val pos = PaperNmsBridge.toNmsBlockPos(event.bed.location)
                onStopSleeping(SleepingArg(player, pos))
                onPlayerWakeUp(PlayerWakeUpArg(player, false, false))
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onBedFailEnter(event: PlayerBedFailEnterEvent) {
                onBedFailEnter(event)
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onDeepSleep(event: PlayerDeepSleepEvent) {
                if (!onAllowResettingTime(AllowResettingTimeArg(PaperNmsBridge.toNmsPlayer(event.player))).getOrElse { true }) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onGlide(event: EntityToggleGlideEvent) {
                val living = event.entity as? org.bukkit.entity.LivingEntity ?: return
                val entity = PaperNmsBridge.toNmsLivingEntity(living)
                if (event.isGliding && !onElytraAllow(ElytraAllowArg(entity)).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }

                onElytraCustom(ElytraCustomArg(entity, event.isGliding))
            }
        }, plugin)
    }
}
