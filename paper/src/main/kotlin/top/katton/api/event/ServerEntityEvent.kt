package top.katton.api.event

import com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityJumpEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import net.minecraft.world.item.ItemStack
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * Server entity events for Paper (Bukkit) platform.
 *
 * This object provides events related to entity lifecycle including
 * load/unload, equipment change, teleport, enderman anger, and world change.
 */
@Suppress("unused")
object ServerEntityEvent {
    @JvmField
    val onAfterEntityLoad = createUnit<EntityLoadArg>()

    @JvmField
    val onEntityUnload = createUnit<EntityUnloadArg>()

    @JvmField
    val onEquipmentChange = createUnit<EquipmentChangeArg>()

    @JvmField
    val onAfterEntityChangeLevel = createUnit<AfterEntityChangeLevelArg>()

    @JvmField
    val onAfterPlayerChangeLevel = createUnit<AfterPlayerChangeLevelArg>()

    @JvmField
    val onEntityTeleport = createCancellableUnit<EntityTeleportArg>()

    @JvmField
    val onEndermanAnger = createCancellableUnit<EndermanAngerArg>()

    @JvmField
    val onEntityJump = createUnit<EntityJumpEvent>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onEntityAdd(event: EntityAddToWorldEvent) {
                onAfterEntityLoad(
                    EntityLoadArg(
                        PaperNmsBridge.toNmsEntity(event.entity),
                        PaperNmsBridge.toNmsLevel(event.world)
                    )
                )
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onEntityRemove(event: EntityRemoveFromWorldEvent) {
                onEntityUnload(
                    EntityUnloadArg(
                        PaperNmsBridge.toNmsEntity(event.entity),
                        PaperNmsBridge.toNmsLevel(event.world)
                    )
                )
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onEquipmentChanged(event: EntityEquipmentChangedEvent) {
                val entity = PaperNmsBridge.toNmsLivingEntity(event.entity)
                event.equipmentChanges.forEach { (slot, change) ->
                    val oldItem = PaperNmsBridge.toNmsItemStack(change.oldItem()) ?: ItemStack.EMPTY
                    val newItem = PaperNmsBridge.toNmsItemStack(change.newItem()) ?: ItemStack.EMPTY
                    onEquipmentChange(EquipmentChangeArg(entity, PaperNmsBridge.toNmsEquipmentSlot(slot), oldItem, newItem))
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onTeleport(event: EntityTeleportEvent) {
                val to = event.to ?: return
                val arg = EntityTeleportArg(
                    PaperNmsBridge.toNmsEntity(event.entity),
                    event.from.x,
                    event.from.y,
                    event.from.z,
                    to.x,
                    to.y,
                    to.z
                )
                onEntityTeleport(arg)
                if (arg.isCancelled()) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
                onAfterPlayerChangeLevel(
                    AfterPlayerChangeLevelArg(
                        PaperNmsBridge.toNmsPlayer(event.player),
                        PaperNmsBridge.toNmsLevel(event.from),
                        PaperNmsBridge.toNmsLevel(event.player.world)
                    )
                )
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onPortal(event: EntityPortalEvent) {
                val to = event.to ?: return
                if (to.world == null || to.world == event.from.world || event.entity is org.bukkit.entity.Player) {
                    return
                }

                val original = PaperNmsBridge.toNmsEntity(event.entity)
                val sourceLevel = PaperNmsBridge.toNmsLevel(event.from.world)
                val destinationWorld = to.world

                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (!event.entity.isValid || event.entity.world != destinationWorld) {
                        return@Runnable
                    }

                    onAfterEntityChangeLevel(
                        AfterEntityChangeLevelArg(
                            original,
                            PaperNmsBridge.toNmsEntity(event.entity),
                            sourceLevel,
                            PaperNmsBridge.toNmsLevel(destinationWorld)
                        )
                    )
                })
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onEndermanAttack(event: EndermanAttackPlayerEvent) {
                val arg = EndermanAngerArg(
                    PaperNmsBridge.toNmsEntity(event.entity) as net.minecraft.world.entity.monster.EnderMan,
                    PaperNmsBridge.toNmsPlayer(event.player)
                )
                onEndermanAnger(arg)
                if (arg.isCancelled()) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onEntityJump(event: EntityJumpEvent) {
                this@ServerEntityEvent.onEntityJump(event)
            }
        }, plugin)
    }
}
