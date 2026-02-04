@file:Suppress("unused")

package top.katton.api

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.world.InteractionResult
import top.katton.util.Event
import top.katton.util.Extension.dispatch

object KattonEvents {

    //============================ Server lifecycle events ============================

    val onServerStarting = Event.create<ServerLifecycleEvents.ServerStarting> { startings ->
        {
            startings.forEach { e -> e.onServerStarting(it) }
        }
    }

    val onServerStarted = Event.create<ServerLifecycleEvents.ServerStarted> { starteds ->
        {
            starteds.forEach { e -> e.onServerStarted(it) }
        }
    }

    val onServerStopped = Event.create<ServerLifecycleEvents.ServerStopped> { stoppeds ->
        {
            stoppeds.forEach { e -> e.onServerStopped(it) }
        }
    }

    val onSyncDatapackContents = Event.create<ServerLifecycleEvents.SyncDataPackContents> { syncDatapackContents ->
        { player, joined ->
            syncDatapackContents.forEach { e -> e.onSyncDataPackContents(player, joined) }
        }
    }

    val onStartDatapackReload = Event.create<ServerLifecycleEvents.StartDataPackReload> { startDatapackReloads ->
        { server, resourceManager ->
            startDatapackReloads.forEach { e -> e.startDataPackReload(server, resourceManager) }
        }
    }

    val onEndDatapackReload = Event.create<ServerLifecycleEvents.EndDataPackReload> { endDatapackReloads ->
        { server, resourceManager, success ->
            endDatapackReloads.forEach { e -> e.endDataPackReload(server, resourceManager, success) }
        }
    }

    val onBeforeSave = Event.create<ServerLifecycleEvents.BeforeSave> { beforeSaves ->
        { server, flush, flushPlayers ->
            beforeSaves.forEach { e -> e.onBeforeSave(server, flush, flushPlayers) }
        }
    }

    val onAfterSave = Event.create<ServerLifecycleEvents.AfterSave> { afterSaves ->
        { server, flush, flushPlayers ->
            afterSaves.forEach { e -> e.onAfterSave(server, flush, flushPlayers) }
        }
    }


    //============================ Server tick events ============================

    val onStartServerTick = Event.create<ServerTickEvents.StartTick> { startServerTicks ->
        { server ->
            startServerTicks.forEach { e -> e.onStartTick(server) }
        }
    }

    val onEndServerTick = Event.create<ServerTickEvents.EndTick> { endServerTicks ->
        { server ->
            endServerTicks.forEach { e -> e.onEndTick(server) }
        }
    }

    val onStartWorldTick = Event.create<ServerTickEvents.StartTick> { startWorldTicks ->
        { server ->
            startWorldTicks.forEach { e -> e.onStartTick(server) }
        }
    }

    val onEndWorldTick = Event.create<ServerTickEvents.EndTick> { endWorldTicks ->
        { server ->
            endWorldTicks.forEach { e -> e.onEndTick(server) }
        }
    }

    //============================== Server entity events ============================

    val onEntityLoad = Event.create<ServerEntityEvents.Load> { entityLoads ->
        { entity, level ->
            entityLoads.forEach { e -> e.onLoad(entity, level) }
        }
    }

    val onEntityUnload = Event.create<ServerEntityEvents.Unload> { entityUnloads ->
        { entity, level ->
            entityUnloads.forEach { e -> e.onUnload(entity, level) }
        }
    }

    val onEquipmentChange = Event.create<ServerEntityEvents.EquipmentChange> { equipmentChanges ->
        { entity, slot, previousItem, newItem ->
            equipmentChanges.forEach { e -> e.onChange(entity, slot, previousItem, newItem) }
        }
    }

    //============================== Server chunk events ============================

    val onChunkLoad = Event.create<ServerChunkEvents.Load> { chunkLoads ->
        { level, chunk ->
            chunkLoads.forEach { e -> e.onChunkLoad(level, chunk) }
        }
    }

    val onChunkGenerate = Event.create<ServerChunkEvents.Generate> { chunkGenerates ->
        { level, chunk ->
            chunkGenerates.forEach { e -> e.onChunkGenerate(level, chunk) }
        }
    }

    val onChunkUnload = Event.create<ServerChunkEvents.Unload> { chunkUnloads ->
        { level, chunk ->
            chunkUnloads.forEach { e -> e.onChunkUnload(level, chunk) }
        }
    }

    val onChunkLevelTypeChange = Event.create<ServerChunkEvents.LevelTypeChange> { chunkLevelTypeChanges ->
        { world, chunk, oldLevelType, newLevelType ->
            chunkLevelTypeChanges.forEach { e -> e.onChunkLevelTypeChange(world, chunk, oldLevelType, newLevelType) }
        }
    }

    //============================== Server block entity events ============================
    val onBlockEntityLoad = Event.create<ServerBlockEntityEvents.Load> { blockEntityLoads ->
        { blockEntity, level ->
            blockEntityLoads.forEach { e -> e.onLoad(blockEntity, level) }
        }
    }

    val onBlockEntityUnload = Event.create<ServerBlockEntityEvents.Unload> { blockEntityUnloads ->
        { blockEntity, level ->
            blockEntityUnloads.forEach { e -> e.onUnload(blockEntity, level) }
        }
    }

    //=============================== Player events ============================

    val onAttackBlock = Event.create<AttackBlockCallback> { attackBlock ->
        { player, world, hand, pos, direction ->
            attackBlock.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand, pos, direction)
            }
        }
    }

    val onAttackEntity = Event.create<AttackEntityCallback> { attackEntity ->
        { player, world, hand, entity, direction ->
            attackEntity.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand, entity, direction)
            }
        }
    }

    val onUseBlock = Event.create<UseBlockCallback> { useBlock ->
        { player, world, hand, hitResult ->
            useBlock.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand, hitResult)
            }
        }
    }

    val onUseEntity = Event.create<UseEntityCallback> { useEntity ->
        { player, world, hand, entity, hitResult ->
            useEntity.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand, entity, hitResult)
            }
        }
    }

    val onUseItem = Event.create<UseItemCallback> { useItem ->
        { player, world, hand ->
            useItem.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand)
            }
        }
    }

    //============================== Loot table events ============================
    val onLootTableReplace = Event.create<LootTableEvents.Replace> { lootTableLoads ->
        { key, original, source, registries ->
            lootTableLoads.dispatch(null) { e ->
                e.replaceLootTable(key, original, source, registries)
            }
        }
    }

    val onLootTableModify = Event.create<LootTableEvents.Modify> { lootTableLoads ->
        { key, tableBuilder, source, registries ->
            lootTableLoads.forEach { it.modifyLootTable(key, tableBuilder, source, registries) }
        }
    }

    val onLootTableAllLoad = Event.create<LootTableEvents.Loaded> { lootTableLoads ->
        { resourceManager, lootManager ->
            lootTableLoads.forEach { it.onLootTablesLoaded(resourceManager, lootManager) }
        }
    }

    val onLootTableModifyDrops = Event.create<LootTableEvents.ModifyDrops> { lootTableLoads ->
        { entry, context, drops ->
            lootTableLoads.forEach { it.modifyLootTableDrops(entry, context, drops) }
        }
    }

}
