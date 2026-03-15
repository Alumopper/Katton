package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * Server entity lifecycle events for Fabric platform.
 *
 * This object provides events related to entity lifecycle including
 * loading, unloading, equipment changes, and level/world changes.
 */
object ServerEntityEvent {

    fun initialize() {
        ServerEntityEvents.ENTITY_LOAD.register { a, b ->
            onAfterEntityLoad(EntityLoadArg(a, b)).getOrNull()
        }

        ServerEntityEvents.ENTITY_UNLOAD.register { a, b ->
            onEntityUnload(EntityUnloadArg(a, b)).getOrNull()
        }

        ServerEntityEvents.EQUIPMENT_CHANGE.register { a, b, c, d ->
            onEquipmentChange(EquipmentChangeArg(a, b, c, d)).getOrNull()
        }

        ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register { a, b, c1, d ->
            onAfterEntityChangeLevel(AfterEntityChangeLevelArg(a, b, c1, d)).getOrNull()
        }

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register { a, b, c1 ->
            onAfterPlayerChangeLevel(AfterPlayerChangeLevelArg(a, b, c1)).getOrNull()
        }
    }

    // === Entity Lifecycle ===

    /**
     * Event triggered after an entity is loaded into a world.
     */
    val onAfterEntityLoad = createUnit<EntityLoadArg>()

    /**
     * Event triggered when an entity is unloaded from a world.
     */
    val onEntityUnload = createUnit<EntityUnloadArg>()

    /**
     * Event triggered when an entity's equipment changes.
     */
    val onEquipmentChange = createUnit<EquipmentChangeArg>()

    // === Entity Level Change ===

    /**
     * Event triggered after an entity changes levels/dimensions.
     */
    val onAfterEntityChangeLevel = createUnit<AfterEntityChangeLevelArg>()

    /**
     * Event triggered after a player changes levels/dimensions.
     */
    val onAfterPlayerChangeLevel = createUnit<AfterPlayerChangeLevelArg>()

    // === Entity Teleport ===

    /**
     * Event triggered when an Enderman is angered by a player.
     * Can be cancelled to prevent the anger.
     */
    @JvmField
    val onEndermanAnger = createCancellableUnit<EndermanAngerArg>()
}
