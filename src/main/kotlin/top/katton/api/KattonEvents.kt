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

    val onServerStarting = Event.createReloadable(ServerLifecycleEvents.SERVER_STARTING) { startings ->
        {
            startings.forEach { e -> e.onServerStarting(it) }
        }
    }

    val onServerStarted = Event.createReloadable(ServerLifecycleEvents.SERVER_STARTED) { starteds ->
        {
            starteds.forEach { e -> e.onServerStarted(it) }
        }
    }

    val onServerStopped = Event.createReloadable(ServerLifecycleEvents.SERVER_STOPPED) { stoppeds ->
        {
            stoppeds.forEach { e -> e.onServerStopped(it) }
        }
    }

    val onSyncDatapackContents = Event.createReloadable(ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS) { syncDatapackContents ->
        { player, joined ->
            syncDatapackContents.forEach { e -> e.onSyncDataPackContents(player, joined) }
        }
    }

    val onStartDatapackReload = Event.createReloadable(ServerLifecycleEvents.START_DATA_PACK_RELOAD) { startDatapackReloads ->
        { server, resourceManager ->
            startDatapackReloads.forEach { e -> e.startDataPackReload(server, resourceManager) }
        }
    }

    val onEndDatapackReload = Event.createReloadable(ServerLifecycleEvents.END_DATA_PACK_RELOAD) { endDatapackReloads ->
        { server, resourceManager, success ->
            endDatapackReloads.forEach { e -> e.endDataPackReload(server, resourceManager, success) }
        }
    }

    val onBeforeSave = Event.createReloadable(ServerLifecycleEvents.BEFORE_SAVE) { beforeSaves ->
        { server, flush, flushPlayers ->
            beforeSaves.forEach { e -> e.onBeforeSave(server, flush, flushPlayers) }
        }
    }

    val onAfterSave = Event.createReloadable(ServerLifecycleEvents.AFTER_SAVE) { afterSaves ->
        { server, flush, flushPlayers ->
            afterSaves.forEach { e -> e.onAfterSave(server, flush, flushPlayers) }
        }
    }


    //============================ Server tick events ============================

    val onStartServerTick = Event.createReloadable(ServerTickEvents.START_SERVER_TICK) { startServerTicks ->
        { server ->
            startServerTicks.forEach { e -> e.onStartTick(server) }
        }
    }

    val onEndServerTick = Event.createReloadable(ServerTickEvents.END_SERVER_TICK) { endServerTicks ->
        { server ->
            endServerTicks.forEach { e -> e.onEndTick(server) }
        }
    }

    val onStartWorldTick = Event.createReloadable(ServerTickEvents.START_WORLD_TICK) { startWorldTicks ->
        { server ->
            startWorldTicks.forEach { e -> e.onStartTick(server) }
        }
    }

    val onEndWorldTick = Event.createReloadable(ServerTickEvents.END_WORLD_TICK) { endWorldTicks ->
        { server ->
            endWorldTicks.forEach { e -> e.onEndTick(server) }
        }
    }

    //============================== Server entity events ============================

    val onEntityLoad = Event.createReloadable(ServerEntityEvents.ENTITY_LOAD) { entityLoads ->
        { entity, level ->
            entityLoads.forEach { e -> e.onLoad(entity, level) }
        }
    }

    val onEntityUnload = Event.createReloadable(ServerEntityEvents.ENTITY_UNLOAD) { entityUnloads ->
        { entity, level ->
            entityUnloads.forEach { e -> e.onUnload(entity, level) }
        }
    }

    val onEquipmentChange = Event.createReloadable(ServerEntityEvents.EQUIPMENT_CHANGE) { equipmentChanges ->
        { entity, slot, previousItem, newItem ->
            equipmentChanges.forEach { e -> e.onChange(entity, slot, previousItem, newItem) }
        }
    }

    //============================== Server chunk events ============================

    val onChunkLoad = Event.createReloadable(ServerChunkEvents.CHUNK_LOAD) { chunkLoads ->
        { level, chunk ->
            chunkLoads.forEach { e -> e.onChunkLoad(level, chunk) }
        }
    }

    val onChunkGenerate = Event.createReloadable(ServerChunkEvents.CHUNK_GENERATE) { chunkGenerates ->
        { level, chunk ->
            chunkGenerates.forEach { e -> e.onChunkGenerate(level, chunk) }
        }
    }

    val onChunkUnload = Event.createReloadable(ServerChunkEvents.CHUNK_UNLOAD) { chunkUnloads ->
        { level, chunk ->
            chunkUnloads.forEach { e -> e.onChunkUnload(level, chunk) }
        }
    }

    val onChunkLevelTypeChange = Event.createReloadable(ServerChunkEvents.CHUNK_LEVEL_TYPE_CHANGE) { chunkLevelTypeChanges ->
        { world, chunk, oldLevelType, newLevelType ->
            chunkLevelTypeChanges.forEach { e -> e.onChunkLevelTypeChange(world, chunk, oldLevelType, newLevelType) }
        }
    }

    //============================== Server block entity events ============================
    val onBlockEntityLoad = Event.createReloadable(ServerBlockEntityEvents.BLOCK_ENTITY_LOAD) { blockEntityLoads ->
        { blockEntity, level ->
            blockEntityLoads.forEach { e -> e.onLoad(blockEntity, level) }
        }
    }

    val onBlockEntityUnload = Event.createReloadable(ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD) { blockEntityUnloads ->
        { blockEntity, level ->
            blockEntityUnloads.forEach { e -> e.onUnload(blockEntity, level) }
        }
    }

    //=============================== Player events ============================

    val onAttackBlock = Event.createReloadable(AttackBlockCallback.EVENT) { attackBlock ->
        { player, world, hand, pos, direction ->
            attackBlock.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand, pos, direction)
            }
        }
    }

    val onAttackEntity = Event.createReloadable(AttackEntityCallback.EVENT) { attackEntity ->
        { player, world, hand, entity, direction ->
            attackEntity.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand, entity, direction)
            }
        }
    }

    val onUseBlock = Event.createReloadable(UseBlockCallback.EVENT) { useBlock ->
        { player, world, hand, hitResult ->
            useBlock.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand, hitResult)
            }
        }
    }

    val onUseEntity = Event.createReloadable(UseEntityCallback.EVENT) { useEntity ->
        { player, world, hand, entity, hitResult ->
            useEntity.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand, entity, hitResult)
            }
        }
    }

    val onUseItem = Event.createReloadable(UseItemCallback.EVENT) { useItem ->
        { player, world, hand ->
            useItem.dispatch(InteractionResult.PASS) { e ->
                e.interact(player, world, hand)
            }
        }
    }

    //============================== Loot table events ============================
    val onLootTableReplace = Event.createReloadable(LootTableEvents.REPLACE) { lootTableLoads ->
        { key, original, source, registries ->
            lootTableLoads.dispatch(null) { e ->
                e.replaceLootTable(key, original, source, registries)
            }
        }
    }

    val onLootTableModify = Event.createReloadable(LootTableEvents.MODIFY) { lootTableLoads ->
        { key, tableBuilder, source, registries ->
            lootTableLoads.forEach { it.modifyLootTable(key, tableBuilder, source, registries) }
        }
    }

    val onLootTableAllLoad = Event.createReloadable(LootTableEvents.ALL_LOADED) { lootTableLoads ->
        { resourceManager, lootManager ->
            lootTableLoads.forEach { it.onLootTablesLoaded(resourceManager, lootManager) }
        }
    }

    val onLootTableModifyDrops = Event.createReloadable(LootTableEvents.MODIFY_DROPS) { lootTableLoads ->
        { entry, context, drops ->
            lootTableLoads.forEach { it.modifyLootTableDrops(entry, context, drops) }
        }
    }

}
