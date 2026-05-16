package top.katton.api.event

import net.minecraft.core.BlockPos; import net.minecraft.server.level.ServerPlayer
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.entity.*; import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit

object LivingBehaviorEvent {
    @JvmField val onElytraAllow = createUnit<Any>()
    @JvmField val onElytraCustom = createUnit<Any>()
    @JvmField val onAllowSleeping = createUnit<Any>()
    @JvmField val onStartSleeping = createUnit<SleepingArg>()
    @JvmField val onStopSleeping = createUnit<SleepingArg>()
    @JvmField val onAllowBed = createUnit<Any>()
    @JvmField val onAllowNearbyMonsters = createUnit<Any>()
    @JvmField val onAllowResettingTime = createUnit<Any>()
    @JvmField val onModifySleepingDirection = createUnit<Any>()
    @JvmField val onAllowSettingSpawn = createUnit<Any>()
    @JvmField val onSetBedOccupationState = createUnit<Any>()
    @JvmField val onModifyWakeUpPosition = createUnit<Any>()
    @JvmField val onPlayerWakeUp = createUnit<PlayerWakeUpArg>()
    @JvmField val onAnimalTame = createUnit<Any>()
    @JvmField val onBabySpawn = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onBedEnter(e: PlayerBedEnterEvent) {
                val p: ServerPlayer = PaperNmsBridge.toNmsPlayer(e.player)
                val bp: BlockPos = PaperNmsBridge.toNmsBlockPos(e.bed.location)
                onStartSleeping(SleepingArg(p, bp))
            }
            @EventHandler fun onBedLeave(e: PlayerBedLeaveEvent) {
                val p: ServerPlayer = PaperNmsBridge.toNmsPlayer(e.player)
                val bp: BlockPos = PaperNmsBridge.toNmsBlockPos(e.bed.location)
                onStopSleeping(SleepingArg(p, bp))
                onPlayerWakeUp(PlayerWakeUpArg(p, false, false))
            }
            @EventHandler fun onGlide(e: EntityToggleGlideEvent) { onElytraCustom(PaperNmsBridge.toNmsEntity(e.entity)) }
            @EventHandler fun onSpawn(e: CreatureSpawnEvent) { if (e.spawnReason == CreatureSpawnEvent.SpawnReason.BREEDING) onBabySpawn(PaperNmsBridge.toNmsLivingEntity(e.entity)) }
        }, plugin)
    }
}




