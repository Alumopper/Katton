@file:Suppress("unused")

/**
 * KattonEvents
 *
 * This file provides Kotlin-friendly wrappers around various Fabric API events.
 * Each nested object groups related server-side events and exposes them as
 * reloadable Event properties via top.katton.util.Event.createReloadable.
 *
 * The wrappers adapt Fabric's Java-style callback invokers into concise Kotlin
 * lambdas, making event handling ergonomical for Kotlin-based mods.
 *
 * Note: the properties mirror Fabric event semantics and typically aggregate
 * or dispatch underlying callbacks according to the original Fabric behavior.
 */
package top.katton.api

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.BlockEvents
import net.fabricmc.fabric.api.event.player.ItemEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.util.TriState
import net.minecraft.world.InteractionResult
import top.katton.util.Event
import top.katton.util.Extension.dispatch

/**
 * Central container object that organizes event wrappers into logical groups.
 *
 * Use KattonEvents.<Group>.<eventProperty> to access the corresponding reloadable
 * event bridge for registering handlers.
 */
object KattonEvents {

    /**
     * Server lifecycle related events: server start/stop, datapack reload, save hooks.
     */
    object ServerLifecycle {
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

        val onSyncDatapackContents =
            Event.createReloadable(ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS) { syncDatapackContents ->
                { player, joined ->
                    syncDatapackContents.forEach { e -> e.onSyncDataPackContents(player, joined) }
                }
            }

        val onStartDatapackReload =
            Event.createReloadable(ServerLifecycleEvents.START_DATA_PACK_RELOAD) { startDatapackReloads ->
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
    }

    /**
     * Server tick events (server/world start/end ticks).
     */
    object ServerTick {
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
    }

    /**
     * General server entity lifecycle events (load/unload/equipment changes).
     */
    object ServerEntity {
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
    }

    /**
     * Server chunk lifecycle events (load/generate/unload/level-type change).
     */
    object ServerChunk {
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

        val onChunkLevelTypeChange =
            Event.createReloadable(ServerChunkEvents.CHUNK_LEVEL_TYPE_CHANGE) { chunkLevelTypeChanges ->
                { world, chunk, oldLevelType, newLevelType ->
                    chunkLevelTypeChanges.forEach { e ->
                        e.onChunkLevelTypeChange(
                            world,
                            chunk,
                            oldLevelType,
                            newLevelType
                        )
                    }
                }
            }
    }

    /**
     * Server block-entity lifecycle events (load/unload).
     */
    object ServerBlockEntity {
        val onBlockEntityLoad = Event.createReloadable(ServerBlockEntityEvents.BLOCK_ENTITY_LOAD) { blockEntityLoads ->
            { blockEntity, level ->
                blockEntityLoads.forEach { e -> e.onLoad(blockEntity, level) }
            }
        }

        val onBlockEntityUnload =
            Event.createReloadable(ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD) { blockEntityUnloads ->
                { blockEntity, level ->
                    blockEntityUnloads.forEach { e -> e.onUnload(blockEntity, level) }
                }
            }
    }

    /**
     * Block interaction events (use with or without item).
     */
    object Block {
        val onUseItemOn = Event.createReloadable(BlockEvents.USE_ITEM_ON) { listeners ->
            { itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult ->
                listeners.firstNotNullOfOrNull {
                    it.useItemOn(
                        itemStack,
                        blockState,
                        level,
                        blockPos,
                        player,
                        interactionHand,
                        blockHitResult
                    )
                }
            }
        }

        val onUseWithoutItem = Event.createReloadable(BlockEvents.USE_WITHOUT_ITEM) { listeners ->
            { blockState, level, blockPos, player, blockHitResult ->
                listeners.firstNotNullOfOrNull { it.useWithoutItem(blockState, level, blockPos, player, blockHitResult) }
            }
        }
    }

    /**
     * Item interaction events (use / useOn).
     */
    object Item {
        val onUseOn = Event.createReloadable(ItemEvents.USE_ON) { listeners ->
            { useOnContext ->
                listeners.firstNotNullOfOrNull { it.useOn(useOnContext) }
            }
        }

        val onUse = Event.createReloadable(ItemEvents.USE) { listeners ->
            { level, player, interactionHand ->
                listeners.firstNotNullOfOrNull { it.use(level, player, interactionHand) }
            }
        }
    }

    /**
     * Player interaction events (attack/use interactions).
     */
    object Player {
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
    }

    /**
     * Loot table related events (replace/modify/all loaded/modify drops).
     */
    object LootTable {
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

    /**
     * ServerPlayer specific events (join/leave/respawn/copy/allow death legacy).
     */
    object ServerPlayer {
        val onPlayerJoin = Event.createReloadable(ServerPlayerEvents.JOIN) { playerJoins ->
            { player ->
                playerJoins.forEach { e -> e.onJoin(player) }
            }
        }

        val onPlayerLeave = Event.createReloadable(ServerPlayerEvents.LEAVE) { playerLeaves ->
            { player ->
                playerLeaves.forEach { e -> e.onLeave(player) }
            }
        }

        val onAfterPlayerRespawn = Event.createReloadable(ServerPlayerEvents.AFTER_RESPAWN) { afterRespawns ->
            { oldPlayer, newPlayer, alive ->
                afterRespawns.forEach { e -> e.afterRespawn(oldPlayer, newPlayer, alive) }
            }
        }

        @Deprecated("Use the more general ServerLivingEntityEvents.ALLOW_DEATH event instead and check for instanceof ServerPlayerEntity.")
        val onPlayerAllowDeath = Event.createReloadable(ServerPlayerEvents.ALLOW_DEATH) { allowDeaths ->
            { player, damageSource, damageAmount ->
                allowDeaths.all { e -> e.allowDeath(player, damageSource, damageAmount) }
            }
        }

        val onPlayerCopy = Event.createReloadable(ServerPlayerEvents.COPY_FROM) { playerCopies ->
            { oldPlayer, newPlayer, alive ->
                playerCopies.forEach { e -> e.copyFromPlayer(oldPlayer, newPlayer, alive) }
            }
        }
    }

    /**
     * Elytra related entity events.
     */
    object EntityElytra {
        val onEntityElytraAllow = Event.createReloadable(EntityElytraEvents.ALLOW) { elytraAllows ->
            { entity, ->
                elytraAllows.all { e -> e.allowElytraFlight(entity) }
            }
        }

        val onEntityElytraCustom = Event.createReloadable(EntityElytraEvents.CUSTOM) { elytraCustoms ->
            { entity, tickElytra ->
                elytraCustoms.any { e -> e.useCustomElytra(entity, tickElytra) }
            }
        }
    }

    /**
     * Entity sleeping related events (allow/start/stop/bed/nearby monsters etc.).
     */
    object EntitySleep {
        val onAllowSleeping = Event.createReloadable(EntitySleepEvents.ALLOW_SLEEPING) { allowSleepings ->
            { entity, pos ->
                allowSleepings.firstNotNullOfOrNull { e -> e.allowSleep(entity, pos) }
            }
        }

        val onStartSleeping = Event.createReloadable(EntitySleepEvents.START_SLEEPING) { startSleepings ->
            { entity, pos ->
                startSleepings.forEach { e -> e.onStartSleeping(entity, pos) }
            }
        }

        val onStopSleeping = Event.createReloadable(EntitySleepEvents.STOP_SLEEPING) { stopSleepings ->
            { entity, pos ->
                stopSleepings.forEach { e -> e.onStopSleeping(entity, pos) }
            }
        }

        val onAllowBed = Event.createReloadable(EntitySleepEvents.ALLOW_BED) { allowBeds ->
            { entity, pos, state, vanillaResult ->
                allowBeds.dispatch(InteractionResult.PASS) { e -> e.allowBed(entity, pos, state, vanillaResult) }
            }
        }

        val onAllowNearbyMonsters = Event.createReloadable(EntitySleepEvents.ALLOW_NEARBY_MONSTERS) { allowNearbyMonsters ->
            { entity, pos, vanillaResult ->
                allowNearbyMonsters.dispatch(InteractionResult.PASS) { e -> e.allowNearbyMonsters(entity, pos, vanillaResult) }
            }
        }

        val onAllowResettingTime = Event.createReloadable(EntitySleepEvents.ALLOW_RESETTING_TIME) { allowResettingTimes ->
            { player ->
                allowResettingTimes.all { e -> e.allowResettingTime(player) }
            }
        }

        val onModifySleepingDirection = Event.createReloadable(EntitySleepEvents.MODIFY_SLEEPING_DIRECTION) { modifySleepingDirections ->
            { entity, pos, direction ->
                var dir = direction
                modifySleepingDirections.forEach { e -> dir = e.modifySleepDirection(entity, pos, dir) }
                dir
            }
        }

        val onAllowSettingSpawn = Event.createReloadable(EntitySleepEvents.ALLOW_SETTING_SPAWN) { allowSettingSpawns ->
            { entity, pos ->
                allowSettingSpawns.all { e -> e.allowSettingSpawn(entity, pos) }
            }
        }

        val onSetBedOccupationState = Event.createReloadable(EntitySleepEvents.SET_BED_OCCUPATION_STATE) { setBedOccupationStates ->
            { entity, pos, state, occupied ->
                setBedOccupationStates.any { e -> e.setBedOccupationState(entity, pos, state, occupied) }
            }
        }

        val onModifyWakeUpPosition = Event.createReloadable(EntitySleepEvents.MODIFY_WAKE_UP_POSITION) { modifyWakeUpPositions ->
            { entity, sleepingPos, bedState, wakeUpPos ->
                var p = wakeUpPos
                modifyWakeUpPositions.forEach { e -> p = e.modifyWakeUpPosition(entity, sleepingPos, bedState, p) }
                p
            }
        }
    }

    /**
     * Server-side living entity events (allow damage, after damage, allow death, after death, conversion).
     */
    object ServerLivingEntity {
        val onAllowDamage = Event.createReloadable(ServerLivingEntityEvents.ALLOW_DAMAGE) { allowDamageEvents ->
            { entity, source, amount ->
                allowDamageEvents.all { it.allowDamage(entity, source, amount) }
            }
        }

        val onAfterDamage = Event.createReloadable(ServerLivingEntityEvents.AFTER_DAMAGE) { afterDamageEvents ->
            { entity, source, baseDamageTaken, damageTaken, blocked ->
                afterDamageEvents.forEach { it.afterDamage(entity, source, baseDamageTaken, damageTaken, blocked) }
            }
        }

        val onAllowDeath = Event.createReloadable(ServerLivingEntityEvents.ALLOW_DEATH) { allowDeathEvents ->
            { entity, damageSource, damageAmount ->
                allowDeathEvents.all { it.allowDeath(entity, damageSource, damageAmount) }
            }
        }

        val onAfterDeath = Event.createReloadable(ServerLivingEntityEvents.AFTER_DEATH) { afterDeathEvents ->
            { entity, damageSource ->
                afterDeathEvents.forEach { it.afterDeath(entity, damageSource) }
            }
        }

        val onMobConversion = Event.createReloadable(ServerLivingEntityEvents.MOB_CONVERSION) { mobConversionEvents ->
            { previous, converted, conversionContext ->
                mobConversionEvents.forEach { it.onConversion(previous, converted, conversionContext) }
            }
        }
    }

    /**
     * Server entity combat events (after killed other entity).
     */
    object ServerEntityCombat {
        val onAfterKilledOtherEntity = Event.createReloadable(ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY) { afterKilledOtherEntities ->
            { world, entity, target, damageSource ->
                afterKilledOtherEntities.forEach { e -> e.afterKilledOtherEntity(world, entity, target, damageSource) }
            }
        }
    }

    /**
     * Server entity world change events (after entity/player change world).
     */
    object ServerEntityWorldChange {
        val onAfterEntityChangeWorld = Event.createReloadable(ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD) { afterEntityChangeWorlds ->
            { entity, newEntity, oldWorld, newWorld ->
                afterEntityChangeWorlds.forEach { e -> e.afterChangeWorld(entity, newEntity, oldWorld, newWorld) }
            }
        }

        val onAfterPlayerChangeWorld = Event.createReloadable(ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD) { afterPlayerChangeWorlds ->
            { player, oldWorld, newWorld ->
                afterPlayerChangeWorlds.forEach { e -> e.afterChangeWorld(player, oldWorld, newWorld) }
            }
        }
    }

    /**
     * Mob effect events on the server (allow add / before/after add / allow early remove / before/after remove).
     */
    object ServerMobEffect {
        val onAllowAdd = Event.createReloadable(ServerMobEffectEvents.ALLOW_ADD) { allowAddEvents ->
            { effectInstance, entity, ctx ->
                allowAddEvents.all { it.allowAdd(effectInstance, entity, ctx) }
            }
        }

        val onBeforeAdd = Event.createReloadable(ServerMobEffectEvents.BEFORE_ADD) { beforeAddEvents ->
            { effectInstance, entity, ctx ->
                beforeAddEvents.forEach { it.beforeAdd(effectInstance, entity, ctx) }
            }
        }

        val onAfterAdd = Event.createReloadable(ServerMobEffectEvents.AFTER_ADD) { afterAddEvents ->
            { effectInstance, entity, ctx ->
                afterAddEvents.forEach { it.afterAdd(effectInstance, entity, ctx) }
            }
        }

        val onAllowEarlyRemove = Event.createReloadable(ServerMobEffectEvents.ALLOW_EARLY_REMOVE) { allowEarlyRemoveEvents ->
            { effectInstance, entity, ctx ->
                allowEarlyRemoveEvents.all { it.allowEarlyRemove(effectInstance, entity, ctx) }
            }
        }

        val onBeforeRemove = Event.createReloadable(ServerMobEffectEvents.BEFORE_REMOVE) { beforeRemoveEvents ->
            { effectInstance, entity, ctx ->
                beforeRemoveEvents.forEach { it.beforeRemove(effectInstance, entity, ctx) }
            }
        }

        val onAfterRemove = Event.createReloadable(ServerMobEffectEvents.AFTER_REMOVE) { afterRemoveEvents ->
            { effectInstance, entity, ctx ->
                afterRemoveEvents.forEach { it.afterRemove(effectInstance, entity, ctx) }
            }
        }
    }

    /**
     * Player block break lifecycle events (before/after/canceled).
     */
    object PlayerBlockBreak {
        val onBefore = Event.createReloadable(PlayerBlockBreakEvents.BEFORE) { befores ->
            { world, player, pos, state, blockEntity ->
                befores.all { it.beforeBlockBreak(world, player, pos, state, blockEntity) }
            }
        }

        val onAfter = Event.createReloadable(PlayerBlockBreakEvents.AFTER) { afters ->
            { world, player, pos, state, blockEntity ->
                afters.forEach { it.afterBlockBreak(world, player, pos, state, blockEntity) }
            }
        }

        val onCanceled = Event.createReloadable(PlayerBlockBreakEvents.CANCELED) { canceleds ->
            { world, player, pos, state, blockEntity ->
                canceleds.forEach { it.onBlockBreakCanceled(world, player, pos, state, blockEntity) }
            }
        }
    }

    /**
     * Player pick-item events (from block / from entity).
     */
    object PlayerPickItem {
        val onPickFromBlock = Event.createReloadable(PlayerPickItemEvents.BLOCK) { listeners ->
            { player, pos, state, includeData ->
                listeners.firstNotNullOfOrNull { it.onPickItemFromBlock(player, pos, state, includeData) }
            }
        }

        val onPickFromEntity = Event.createReloadable(PlayerPickItemEvents.ENTITY) { listeners ->
            { player, entity, includeData ->
                listeners.firstNotNullOfOrNull { it.onPickItemFromEntity(player, entity, includeData) }
            }
        }
    }

    /**
     * Default item component modification events.
     */
    object DefaultItemComponent {
        val onModify = Event.createReloadable(DefaultItemComponentEvents.MODIFY) { modifiers ->
            { context ->
                modifiers.forEach { it.modify(context) }
            }
        }
    }

    /**
     * Enchantment related events (allow enchanting with TriState semantics, modify).
     */
    object Enchantment {
        val onAllowEnchanting = Event.createReloadable(EnchantmentEvents.ALLOW_ENCHANTING) { allowEnchantings ->
            { enchantment, target, context ->
                var result = TriState.DEFAULT
                for (listener in allowEnchantings) {
                    val r = listener.allowEnchanting(enchantment, target, context)
                    if (r != TriState.DEFAULT) {
                        result = r
                        break
                    }
                }
                result
            }
        }

        val onModify = Event.createReloadable(EnchantmentEvents.MODIFY) { modifiers ->
            { key, builder, source ->
                modifiers.forEach { it.modify(key, builder, source) }
            }
        }
    }

    /**
     * Server-side message events (chat/game/command allow and handlers).
     */
    object ServerMessage {
        val onAllowChatMessage = Event.createReloadable(ServerMessageEvents.ALLOW_CHAT_MESSAGE) { handlers ->
            { message, sender, params ->
                handlers.all { it.allowChatMessage(message, sender, params) }
            }
        }

        val onAllowGameMessage = Event.createReloadable(ServerMessageEvents.ALLOW_GAME_MESSAGE) { handlers ->
            { server, message, overlay ->
                handlers.all { it.allowGameMessage(server, message, overlay) }
            }
        }

        val onAllowCommandMessage = Event.createReloadable(ServerMessageEvents.ALLOW_COMMAND_MESSAGE) { handlers ->
            { message, source, params ->
                handlers.all { it.allowCommandMessage(message, source, params) }
            }
        }

        val onChatMessage = Event.createReloadable(ServerMessageEvents.CHAT_MESSAGE) { handlers ->
            { message, sender, params ->
                handlers.forEach { it.onChatMessage(message, sender, params) }
            }
        }

        val onGameMessage = Event.createReloadable(ServerMessageEvents.GAME_MESSAGE) { handlers ->
            { server, message, overlay ->
                handlers.forEach { it.onGameMessage(server, message, overlay) }
            }
        }

        val onCommandMessage = Event.createReloadable(ServerMessageEvents.COMMAND_MESSAGE) { handlers ->
            { message, source, params ->
                handlers.forEach { it.onCommandMessage(message, source, params) }
            }
        }
    }
}
