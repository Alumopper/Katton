package top.katton.api.event

import net.minecraft.server.level.ServerLevel
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.EntityTeleportEvent
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit
import top.katton.util.setCancel

/**
 * General server entity lifecycle events (load/unload/equipment changes).
 * Also includes entity level/world changes and teleportation events.
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = top.katton.Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerEntityEvent {

    @SubscribeEvent
    private fun onEntityLoad(e: EntityJoinLevelEvent) {
        if (e.level is ServerLevel) {
            onEntityLoad(EntityLoadArg(e.entity, e.level as ServerLevel))
            setCancel(onEntityLoad, e)
        }
    }

    @SubscribeEvent
    private fun onEntityUnload(e: EntityLeaveLevelEvent) {
        if (e.level is ServerLevel) {
            onEntityUnload(EntityUnloadArg(e.entity, e.level as ServerLevel))
        }
    }

    @SubscribeEvent
    private fun onEquipmentChange(e: LivingEquipmentChangeEvent) {
        onEquipmentChange(
            EquipmentChangeArg(e.entity, e.slot, e.from, e.to)
        )
    }

    @SubscribeEvent
    private fun onEntityTeleport(e: EntityTeleportEvent) {
        onEntityTeleport(
            EntityTeleportArg(e.entity, e.prevX, e.prevY, e.prevZ, e.targetX, e.targetY, e.targetZ)
        )
        setCancel(onEntityTeleport, e)
    }

    @SubscribeEvent
    private fun onEndermanAnger(e: EnderManAngerEvent) {
        onEndermanAnger(
            EndermanAngerArg(e.entity, e.player)
        )
        setCancel(onEndermanAnger, e)
    }

    // === Entity Lifecycle ===
    val onEntityLoad = createCancellableUnit<EntityLoadArg>()

    val onEntityUnload = createUnit<EntityUnloadArg>()

    val onEquipmentChange = createUnit<EquipmentChangeArg>()

    // === Entity Level Change ===
    @JvmField
    val onAfterEntityChangeLevel = createUnit<AfterEntityChangeLevelArg>()

    @JvmField
    val onAfterPlayerChangeLevel = createUnit<AfterPlayerChangeLevelArg>()

    // === Entity Teleport ===
    val onEntityTeleport = createCancellableUnit<EntityTeleportArg>()

    val onEndermanAnger = createCancellableUnit<EndermanAngerArg>()
}