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
 * Server entity lifecycle events for NeoForge platform.
 *
 * This object provides events related to entity lifecycle including
 * loading, unloading, equipment changes, teleportation, and Enderman anger.
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

    /**
     * Event triggered when an entity joins a level (server-side).
     * Can be cancelled to prevent the entity from joining.
     */
    val onEntityLoad = createCancellableUnit<EntityLoadArg>()

    /**
     * Event triggered when an entity leaves a level.
     */
    val onEntityUnload = createUnit<EntityUnloadArg>()

    /**
     * Event triggered when an entity's equipment changes.
     */
    val onEquipmentChange = createUnit<EquipmentChangeArg>()

    // === Entity Level Change ===

    /**
     * Event triggered after an entity changes levels/dimensions.
     * Note: This is a placeholder for NeoForge compatibility.
     */
    @JvmField
    val onAfterEntityChangeLevel = createUnit<AfterEntityChangeLevelArg>()

    /**
     * Event triggered after a player changes levels/dimensions.
     * Note: This is a placeholder for NeoForge compatibility.
     */
    @JvmField
    val onAfterPlayerChangeLevel = createUnit<AfterPlayerChangeLevelArg>()

    // === Entity Teleport ===

    /**
     * Event triggered when an entity teleports.
     * Can be cancelled to prevent the teleport.
     */
    val onEntityTeleport = createCancellableUnit<EntityTeleportArg>()

    /**
     * Event triggered when an Enderman is angered by a player.
     * Can be cancelled to prevent the anger.
     */
    val onEndermanAnger = createCancellableUnit<EndermanAngerArg>()
}
