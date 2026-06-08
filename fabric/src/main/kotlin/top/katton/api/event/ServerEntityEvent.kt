package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * %en
 * Server entity lifecycle events for Fabric platform.
 *
 * This object provides events related to entity lifecycle including
 * loading, unloading, equipment changes, and level/world changes.
 *
 * %zh
 * Fabric 平台的服务端实体生命周期事件。
 * 此对象提供与实体生命周期相关的事件，包括加载、卸载、装备变更以及维度/世界变更。
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
 * %en
 * Event triggered after an entity is loaded into a world.
 *
 * %zh
 * 当实体加载到世界中后触发。
 */
    val onAfterEntityLoad = createUnit<EntityLoadArg>()

    /**
 * %en
 * Event triggered when an entity is unloaded from a world.
 *
 * %zh
 * 当实体从世界中卸载时触发。
 */
    val onEntityUnload = createUnit<EntityUnloadArg>()

    /**
 * %en
 * Event triggered when an entity's equipment changes.
 *
 * %zh
 * 当实体的装备发生变化时触发。
 */
    val onEquipmentChange = createUnit<EquipmentChangeArg>()

    // === Entity Level Change ===

    /**
 * %en
 * Event triggered after an entity changes levels/dimensions.
 *
 * %zh
 * 当实体切换维度/世界之后触发。
 */
    val onAfterEntityChangeLevel = createUnit<AfterEntityChangeLevelArg>()

    /**
 * %en
 * Event triggered after a player changes levels/dimensions.
 *
 * %zh
 * 当玩家切换维度/世界之后触发。
 */
    val onAfterPlayerChangeLevel = createUnit<AfterPlayerChangeLevelArg>()

    // === Entity Teleport ===

    /**
 * %en
 * Event triggered when an Enderman is angered by a player.
 * Can be cancelled to prevent the anger.
 *
 * %zh
 * 当末影人因玩家而进入仇恨状态时触发。
 * 可取消以阻止该行为。
 */
    @JvmField
    val onEndermanAnger = createCancellableUnit<EndermanAngerArg>()
}
