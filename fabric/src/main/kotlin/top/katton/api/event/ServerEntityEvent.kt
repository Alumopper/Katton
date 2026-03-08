package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * General server entity lifecycle events (load/unload/equipment changes).
 * Also includes entity level/world changes and teleportation events.
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
    val onAfterEntityLoad = createUnit<EntityLoadArg>()

    val onEntityUnload = createUnit<EntityUnloadArg>()

    val onEquipmentChange = createUnit<EquipmentChangeArg>()

    // === Entity Level Change ===
    val onAfterEntityChangeLevel = createUnit<AfterEntityChangeLevelArg>()

    val onAfterPlayerChangeLevel = createUnit<AfterPlayerChangeLevelArg>()

    // === Entity Teleport ===

    @JvmField
    val onEndermanAnger = createCancellableUnit<EndermanAngerArg>()
}