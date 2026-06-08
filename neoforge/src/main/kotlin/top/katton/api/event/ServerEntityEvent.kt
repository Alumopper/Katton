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
 * %en
 * Server entity lifecycle events for NeoForge platform.
 *
 * This object provides events related to entity lifecycle including
 * loading, unloading, equipment changes, teleportation, and Enderman anger.
 *
 * %zh
 * NeoForge 平台的服务端实体生命周期事件。
 * 此对象提供与实体生命周期相关的事件，包括加载、卸载、装备变化、传送和末影人愤怒。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = top.katton.Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerEntityEvent {

    @JvmStatic
    @SubscribeEvent
    private fun onEntityLoad(e: EntityJoinLevelEvent) {
        if (e.level is ServerLevel) {
            onEntityLoad(EntityLoadArg(e.entity, e.level as ServerLevel))
            setCancel(onEntityLoad, e)
        }
    }

    @JvmStatic
    @SubscribeEvent
    private fun onEntityUnload(e: EntityLeaveLevelEvent) {
        if (e.level is ServerLevel) {
            onEntityUnload(EntityUnloadArg(e.entity, e.level as ServerLevel))
        }
    }

    @JvmStatic
    @SubscribeEvent
    private fun onEquipmentChange(e: LivingEquipmentChangeEvent) {
        onEquipmentChange(
            EquipmentChangeArg(e.entity, e.slot, e.from, e.to)
        )
    }

    @JvmStatic
    @SubscribeEvent
    private fun onEntityTeleport(e: EntityTeleportEvent) {
        onEntityTeleport(
            EntityTeleportArg(e.entity, e.prevX, e.prevY, e.prevZ, e.targetX, e.targetY, e.targetZ)
        )
        setCancel(onEntityTeleport, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun onEndermanAnger(e: EnderManAngerEvent) {
        onEndermanAnger(
            EndermanAngerArg(e.entity, e.player)
        )
        setCancel(onEndermanAnger, e)
    }

    // === Entity Lifecycle ===

    /**
     * %en
     * Event triggered when an entity joins a level (server-side).
     * Can be cancelled to prevent the entity from joining.
     *
     * %zh
     * 当实体加入某个维度（服务端）时触发。
     * 可取消以阻止实体加入。
     */
    val onEntityLoad = createCancellableUnit<EntityLoadArg>()

    /**
     * %en
     * Event triggered when an entity leaves a level.
     *
     * %zh
     * 当实体离开某个维度时触发。
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
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 当实体切换维度/世界后触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onAfterEntityChangeLevel = createUnit<AfterEntityChangeLevelArg>()

    /**
     * %en
     * Event triggered after a player changes levels/dimensions.
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 当玩家切换维度/世界后触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onAfterPlayerChangeLevel = createUnit<AfterPlayerChangeLevelArg>()

    // === Entity Teleport ===

    /**
     * %en
     * Event triggered when an entity teleports.
     * Can be cancelled to prevent the teleport.
     *
     * %zh
     * 当实体传送时触发。
     * 可取消以阻止传送。
     */
    val onEntityTeleport = createCancellableUnit<EntityTeleportArg>()

    /**
     * %en
     * Event triggered when an Enderman is angered by a player.
     * Can be cancelled to prevent the anger.
     *
     * %zh
     * 当末影人被玩家激怒时触发。
     * 可取消以阻止激怒。
     */
    val onEndermanAnger = createCancellableUnit<EndermanAngerArg>()
}
