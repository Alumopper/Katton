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
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext
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
import net.fabricmc.fabric.api.item.v1.EnchantingContext
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents
import net.fabricmc.fabric.api.item.v1.EnchantmentSource
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.util.EventResult
import net.fabricmc.fabric.api.util.TriState
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.network.chat.ChatType.Bound
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.FullChunkStatus
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.ConversionParams
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.item.enchantment.Enchantment.Builder
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import top.katton.util.Event
import top.katton.util.Extension.dispatch

/**
 * Central container object that organizes event wrappers into logical groups.
 *
 * Use KattonEvents.<Group>.<eventProperty> to access the corresponding reloadable
 * event bridge for registering handlers.
 */
object KattonEvents {

    private fun <B> unit(): (List<(B) -> Unit>) -> (B) -> Unit = { events ->
        { arg: B -> events.forEach { e -> e(arg) } }
    }

    private fun <B, R> firstNotNullOfOrNull(): (List<(B) -> R?>) -> (B) -> R? = { events ->
        { arg: B -> events.firstNotNullOfOrNull { e -> e(arg) } }
    }

    private fun <B> all(): (List<(B) -> Boolean>) -> (B) -> Boolean = { events ->
        { arg: B -> events.all { e -> e(arg) } }
    }

    private fun <B> any(): (List<(B) -> Boolean>) -> (B) -> Boolean = { events ->
        { arg: B -> events.any { e -> e(arg) } }
    }

    private fun <B> triState(): (List<(B) -> TriState>) -> (B) -> TriState = { events ->
        { arg: B ->
            var status = TriState.DEFAULT
            for (e in events) {
                status = e(arg)
                if (status != TriState.DEFAULT) break
            }
            status
        }
    }

    private fun <B, R> dispatch(passValue: R): (List<(B) -> R>) -> (B) -> R = { events ->
        { arg: B -> events.dispatch(passValue) { e -> e(arg) } }
    }


    /**
     * Server lifecycle related events: server start/stop, datapack reload, save hooks.
     */
    object ServerLifecycle {

        data class ServerArg(val server: MinecraftServer)

        val onServerStarting = Event.createReloadable(ServerLifecycleEvents.SERVER_STARTING,
            { c -> ServerLifecycleEvents.ServerStarting {c(ServerArg(it))} },
            unit<ServerArg>()
        )

        val onServerStarted = Event.createReloadable(ServerLifecycleEvents.SERVER_STARTED,
            { c -> ServerLifecycleEvents.ServerStarted {c(ServerArg(it))} },
            unit<ServerArg>()
        )

        val onServerStopped = Event.createReloadable(ServerLifecycleEvents.SERVER_STOPPED,
            { c -> ServerLifecycleEvents.ServerStopped {c(ServerArg(it))} },
            unit<ServerArg>()
        )

        data class SyncDatapackContentsArg(
            val player: net.minecraft.server.level.ServerPlayer,
            val joined: Boolean
        )

        val onSyncDatapackContents =
            Event.createReloadable(ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS,
            { c -> ServerLifecycleEvents.SyncDataPackContents {a, b -> c(SyncDatapackContentsArg(a, b))} },
            unit<SyncDatapackContentsArg>()
        )

        data class StartDatapackReloadArg(
            val server: MinecraftServer,
            val resourceManager: ResourceManager
        )

        val onStartDatapackReload =
            Event.createReloadable(ServerLifecycleEvents.START_DATA_PACK_RELOAD,
            { c -> ServerLifecycleEvents.StartDataPackReload {a, b -> c(StartDatapackReloadArg(a, b))} },
            unit<StartDatapackReloadArg>()
        )

        data class EndDatapackReloadArg(
            val server: MinecraftServer,
            val resourceManager: ResourceManager,
            val success: Boolean
        )

        val onEndDatapackReload = Event.createReloadable(ServerLifecycleEvents.END_DATA_PACK_RELOAD,
            { c -> ServerLifecycleEvents.EndDataPackReload {a, b, c -> c(EndDatapackReloadArg(a, b, c))} },
            unit<EndDatapackReloadArg>()
        )

        data class SaveArg(
            val server: MinecraftServer,
            val flush: Boolean,
            val force: Boolean
        )

        val onBeforeSave = Event.createReloadable(ServerLifecycleEvents.BEFORE_SAVE,
            { c -> ServerLifecycleEvents.BeforeSave {a, b, c -> c(SaveArg(a, b, c))} },
            unit<SaveArg>()
        )

        val onAfterSave = Event.createReloadable(ServerLifecycleEvents.AFTER_SAVE,
            { c -> ServerLifecycleEvents.AfterSave {a, b, c -> c(SaveArg(a, b, c))} },
            unit<SaveArg>()
        )
    }

    /**
     * Server tick events (server/world start/end ticks).
     */
    object ServerTick {
        data class ServerTickArg(val server: MinecraftServer)

        val onStartServerTick = Event.createReloadable(ServerTickEvents.START_SERVER_TICK,
            { c -> ServerTickEvents.StartTick {c(ServerTickArg(it))} },
            unit<ServerTickArg>()
        )

        val onEndServerTick = Event.createReloadable(ServerTickEvents.END_SERVER_TICK,
            { c -> ServerTickEvents.EndTick {c(ServerTickArg(it))} },
            unit<ServerTickArg>()
        )

        data class WorldTickArg(val world: ServerLevel)

        val onStartWorldTick = Event.createReloadable(ServerTickEvents.START_LEVEL_TICK,
            { c -> ServerTickEvents.StartLevelTick {c(WorldTickArg(it))} },
            unit<WorldTickArg>()
        )

        val onEndWorldTick = Event.createReloadable(ServerTickEvents.END_LEVEL_TICK,
            { c -> ServerTickEvents.EndLevelTick {c(WorldTickArg(it))} },
            unit<WorldTickArg>()
        )
    }

    /**
     * General server entity lifecycle events (load/unload/equipment changes).
     */
    object ServerEntity {
        data class EntityLoadArg(
            val entity: Entity,
            val world: ServerLevel
        )

        val onEntityLoad = Event.createReloadable(ServerEntityEvents.ENTITY_LOAD,
            { c -> ServerEntityEvents.Load {a, b -> c(EntityLoadArg(a, b))} },
            unit<EntityLoadArg>()
        )

        val onEntityUnload = Event.createReloadable(ServerEntityEvents.ENTITY_UNLOAD,
            { c -> ServerEntityEvents.Unload {a, b -> c(EntityLoadArg(a, b))} },
            unit<EntityLoadArg>()
        )

        data class EquipmentChangeArg(
            val entity: Entity,
            val slot: EquipmentSlot,
            val from: ItemStack,
            val to: ItemStack
        )

        val onEquipmentChange = Event.createReloadable(ServerEntityEvents.EQUIPMENT_CHANGE,
            { c -> ServerEntityEvents.EquipmentChange {a, b, c, d -> c(EquipmentChangeArg(a, b, c, d))} },
            unit<EquipmentChangeArg>()
        )
    }

    /**
     * Server chunk lifecycle events (load/generate/unload/level-type change).
     */
    object ServerChunk {
        data class ChunkLoadArg(
            val world: ServerLevel,
            val chunk: LevelChunk,
            val isNew: Boolean
        )

        val onChunkLoad = Event.createReloadable(ServerChunkEvents.CHUNK_LOAD,
            { c -> ServerChunkEvents.Load {a, b, c -> c(ChunkLoadArg(a, b, c))} },
            unit<ChunkLoadArg>()
        )

        data class ChunkUnloadArg(
            val world: ServerLevel,
            val chunk: LevelChunk
        )

        val onChunkUnload = Event.createReloadable(ServerChunkEvents.CHUNK_UNLOAD,
            { c -> ServerChunkEvents.Unload {a, b -> c(ChunkUnloadArg(a, b))} },
            unit<ChunkUnloadArg>()
        )

        data class ChunkStatusChangeArg(
            val world: ServerLevel,
            val chunk: LevelChunk,
            val oldStatus: FullChunkStatus,
            val newStatus: FullChunkStatus
        )

        val onChunkLevelTypeChange =
            Event.createReloadable(ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE,
                { c -> ServerChunkEvents.FullChunkStatusChange {a, b, c, d -> c(ChunkStatusChangeArg(a, b, c, d))} },
                unit<ChunkStatusChangeArg>()
            )
    }

    /**
     * Server block-entity lifecycle events (load/unload).
     */
    object ServerBlockEntity {
        data class BlockEntityLoadArg(
            val blockEntity: net.minecraft.world.level.block.entity.BlockEntity,
            val world: ServerLevel
        )

        val onBlockEntityLoad = Event.createReloadable(ServerBlockEntityEvents.BLOCK_ENTITY_LOAD,
            { c -> ServerBlockEntityEvents.Load {a, b -> c(BlockEntityLoadArg(a, b))} },
            unit<BlockEntityLoadArg>()
        )

        val onBlockEntityUnload = Event.createReloadable(ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD,
            { c -> ServerBlockEntityEvents.Unload {a, b -> c(BlockEntityLoadArg(a, b))} },
            unit<BlockEntityLoadArg>()
        )
    }

    /**
     * Block interaction events (use with or without item).
     */
    object Block {
        data class UseItemOnArg(
            val stack: ItemStack,
            val state: BlockState,
            val world: Level,
            val pos: BlockPos,
            val player: net.minecraft.world.entity.player.Player,
            val hand: InteractionHand,
            val hitResult: BlockHitResult
        )

        val onUseItemOn = Event.createReloadable(BlockEvents.USE_ITEM_ON,
            { c -> BlockEvents.UseItemOnCallback {a, b, c1, d, e, f, g -> c(UseItemOnArg(a, b, c1, d, e, f, g))} },
            dispatch<UseItemOnArg, InteractionResult>(InteractionResult.PASS)
        )

        data class UseWithoutItemArg(
            val state: BlockState,
            val world: Level,
            val pos: BlockPos,
            val player: net.minecraft.world.entity.player.Player,
            val hitResult: BlockHitResult
        )

        val onUseWithoutItem = Event.createReloadable(BlockEvents.USE_WITHOUT_ITEM,
            { c -> BlockEvents.UseWithoutItemCallback {a, b, c1, d, e -> c(UseWithoutItemArg(a, b, c1, d, e))} },
            dispatch<UseWithoutItemArg, InteractionResult>(InteractionResult.PASS)
        )
    }

    /**
     * Item interaction events (use / useOn).
     */
    object Item {

        data class UseOnArg(val context: UseOnContext)

        val onUseOn = Event.createReloadable(ItemEvents.USE_ON,
            {c -> ItemEvents.UseOnCallback {c(UseOnArg(it))}},
            dispatch<UseOnArg, InteractionResult>(InteractionResult.PASS)
        )

        data class UseArg(
            val world: Level,
            val player: net.minecraft.world.entity.player.Player,
            val hand: InteractionHand
        )

        val onUse = Event.createReloadable(ItemEvents.USE,
            {c -> ItemEvents.UseCallback {a, b, c1 -> c(UseArg(a, b, c1))}},
            dispatch<UseArg, InteractionResult>(InteractionResult.PASS)
        )
    }

    /**
     * Player interaction events (attack/use interactions).
     */
    object Player {

        data class OnAttackBlockArg(
            val player: net.minecraft.world.entity.player.Player,
            val world: Level,
            val hand: InteractionHand,
            val pos: BlockPos,
            val direction: Direction,
        )

        val onAttackBlock = Event.createReloadable(AttackBlockCallback.EVENT,
            {c -> AttackBlockCallback {a, b, c1, d, e -> c(OnAttackBlockArg(a, b, c1, d, e))}},
            dispatch<OnAttackBlockArg, InteractionResult>(InteractionResult.PASS)
        )

        data class AttackEntityArg(
            val player: net.minecraft.world.entity.player.Player,
            val world: Level,
            val hand: InteractionHand,
            val entity: Entity,
            val hitResult: net.minecraft.world.phys.EntityHitResult?
        )

        val onAttackEntity = Event.createReloadable(AttackEntityCallback.EVENT,
            { c -> AttackEntityCallback { a, b, c1, d, e -> c(AttackEntityArg(a, b, c1, d, e)) } },
            dispatch<AttackEntityArg, InteractionResult>(InteractionResult.PASS)
        )

        data class UseBlockArg(
            val player: net.minecraft.world.entity.player.Player,
            val world: Level,
            val hand: InteractionHand,
            val hitResult: BlockHitResult
        )

        val onUseBlock = Event.createReloadable(UseBlockCallback.EVENT,
            { c -> UseBlockCallback { a, b, c1, d -> c(UseBlockArg(a, b, c1, d)) } },
            dispatch<UseBlockArg, InteractionResult>(InteractionResult.PASS)
        )

        data class UseEntityArg(
            val player: net.minecraft.world.entity.player.Player,
            val world: Level,
            val hand: InteractionHand,
            val entity: Entity,
            val hitResult: net.minecraft.world.phys.EntityHitResult?
        )

        val onUseEntity = Event.createReloadable(UseEntityCallback.EVENT,
            { c -> UseEntityCallback { a, b, c1, d, e -> c(UseEntityArg(a, b, c1, d, e)) } },
            dispatch<UseEntityArg, InteractionResult>(InteractionResult.PASS)
        )

        data class UseItemArg(
            val player: net.minecraft.world.entity.player.Player,
            val world: Level,
            val hand: InteractionHand
        )

        val onUseItem = Event.createReloadable(UseItemCallback.EVENT,
            { c -> UseItemCallback { a, b, c1 -> c(UseItemArg(a, b, c1)) } },
            dispatch<UseItemArg, InteractionResult>(InteractionResult.PASS)
        )
    }

    /**
     * Loot table related events (replace/modify/all loaded/modify drops).
     */
    object LootTable {
        data class LootTableReplaceArg(
            val key: ResourceKey<net.minecraft.world.level.storage.loot.LootTable>,
            val original: net.minecraft.world.level.storage.loot.LootTable,
            val source: net.fabricmc.fabric.api.loot.v3.LootTableSource,
            val registries: net.minecraft.core.HolderLookup.Provider
        )

        val onLootTableReplace = Event.createReloadable(LootTableEvents.REPLACE,
            { c -> LootTableEvents.Replace { a, b, c1, d -> c(LootTableReplaceArg(a, b, c1, d)) } },
            firstNotNullOfOrNull<LootTableReplaceArg, net.minecraft.world.level.storage.loot.LootTable?>()
        )

        data class LootTableModifyArg(
            val key: ResourceKey<net.minecraft.world.level.storage.loot.LootTable>,
            val tableBuilder: net.minecraft.world.level.storage.loot.LootTable.Builder,
            val source: net.fabricmc.fabric.api.loot.v3.LootTableSource,
            val registries: net.minecraft.core.HolderLookup.Provider
        )

        val onLootTableModify = Event.createReloadable(LootTableEvents.MODIFY,
            { c -> LootTableEvents.Modify {a, b, c1, d -> c(LootTableModifyArg(a, b, c1, d))} },
            unit<LootTableModifyArg>()
        )

        data class LootTableAllLoadArg(
            val resourceManager: ResourceManager,
            val lootDataManager: Registry<net.minecraft.world.level.storage.loot.LootTable>
        )

        val onLootTableAllLoad = Event.createReloadable(LootTableEvents.ALL_LOADED,
            { c -> LootTableEvents.Loaded {a, b -> c(LootTableAllLoadArg(a, b))} },
            unit<LootTableAllLoadArg>()
        )

        data class LootTableModifyDropsArg(
            val table: Holder<net.minecraft.world.level.storage.loot.LootTable>,
            val context: LootContext,
            val drops: List<ItemStack>
        )

        val onLootTableModifyDrops = Event.createReloadable(LootTableEvents.MODIFY_DROPS,
            { c -> LootTableEvents.ModifyDrops {a, b, c1 -> c(LootTableModifyDropsArg(a, b, c1))} },
            unit<LootTableModifyDropsArg>()
        )
    }

    /**
     * ServerPlayer specific events (join/leave/respawn/copy/allow death legacy).
     */
    object ServerPlayer {
        data class PlayerArg(val player: net.minecraft.server.level.ServerPlayer)

        val onPlayerJoin = Event.createReloadable(ServerPlayerEvents.JOIN,
            { c -> ServerPlayerEvents.Join {c(PlayerArg(it))} },
            unit<PlayerArg>()
        )

        val onPlayerLeave = Event.createReloadable(ServerPlayerEvents.LEAVE,
            { c -> ServerPlayerEvents.Leave {c(PlayerArg(it))} },
            unit<PlayerArg>()
        )

        data class AfterRespawnArg(
            val oldPlayer: net.minecraft.server.level.ServerPlayer,
            val newPlayer: net.minecraft.server.level.ServerPlayer,
            val alive: Boolean
        )

        val onAfterPlayerRespawn = Event.createReloadable(ServerPlayerEvents.AFTER_RESPAWN,
            { c -> ServerPlayerEvents.AfterRespawn {a, b, c1 -> c(AfterRespawnArg(a, b, c1))} },
            unit<AfterRespawnArg>()
        )

        data class AllowDeathArg(
            val player: net.minecraft.server.level.ServerPlayer,
            val damageSource: net.minecraft.world.damagesource.DamageSource,
            val damageAmount: Float
        )

        @Deprecated("Use the more general ServerLivingEntityEvents.ALLOW_DEATH event instead and check for instanceof ServerPlayerEntity.")
        val onPlayerAllowDeath = Event.createReloadable(ServerPlayerEvents.ALLOW_DEATH,
            { c -> ServerPlayerEvents.AllowDeath { a, b, c1 -> c(AllowDeathArg(a, b, c1)) } },
            all<AllowDeathArg>()
        )

        data class CopyArg(
            val oldPlayer: net.minecraft.server.level.ServerPlayer,
            val newPlayer: net.minecraft.server.level.ServerPlayer,
            val alive: Boolean
        )

        val onPlayerCopy = Event.createReloadable(ServerPlayerEvents.COPY_FROM,
            { c -> ServerPlayerEvents.CopyFrom {a, b, c1 -> c(CopyArg(a, b, c1))} },
            unit<CopyArg>()
        )
    }

    /**
     * Elytra related entity events.
     */
    object EntityElytra {
        data class ElytraAllowArg(val entity: net.minecraft.world.entity.LivingEntity)

        val onEntityElytraAllow = Event.createReloadable(EntityElytraEvents.ALLOW,
            { c -> EntityElytraEvents.Allow { c(ElytraAllowArg(it)) } },
            all<ElytraAllowArg>()
        )

        data class ElytraCustomArg(
            val entity: net.minecraft.world.entity.LivingEntity,
            val tickElytra: Boolean
        )

        val onEntityElytraCustom = Event.createReloadable(EntityElytraEvents.CUSTOM,
            { c -> EntityElytraEvents.Custom { a, b -> c(ElytraCustomArg(a, b)) } },
            any<ElytraCustomArg>()
        )
    }

    /**
     * Entity sleeping related events (allow/start/stop/bed/nearby monsters etc.).
     */
    object EntitySleep {
        data class AllowSleepingArg(
            val entity: net.minecraft.world.entity.LivingEntity,
            val pos: BlockPos
        )

        val onAllowSleeping = Event.createReloadable(EntitySleepEvents.ALLOW_SLEEPING,
            { c -> EntitySleepEvents.AllowSleeping { a, b -> c(AllowSleepingArg(a, b)) } },
            firstNotNullOfOrNull<AllowSleepingArg, Player.BedSleepingProblem?>()
        )

        data class SleepingArg(
            val entity: net.minecraft.world.entity.LivingEntity,
            val pos: BlockPos
        )

        val onStartSleeping = Event.createReloadable(EntitySleepEvents.START_SLEEPING,
            { c -> EntitySleepEvents.StartSleeping {a, b -> c(SleepingArg(a, b))} },
            unit<SleepingArg>()
        )

        val onStopSleeping = Event.createReloadable(EntitySleepEvents.STOP_SLEEPING,
            { c -> EntitySleepEvents.StopSleeping {a, b -> c(SleepingArg(a, b))} },
            unit<SleepingArg>()
        )

        data class AllowBedArg(
            val entity: LivingEntity,
            val pos: BlockPos,
            val state: BlockState,
            val vanillaResult: Boolean
        )

        val onAllowBed = Event.createReloadable(EntitySleepEvents.ALLOW_BED,
            { c -> EntitySleepEvents.AllowBed { a, b, c1, d -> c(AllowBedArg(a, b, c1, d)) } },
            dispatch<AllowBedArg, EventResult>(EventResult.PASS)
        )

        data class AllowNearbyMonstersArg(
            val entity: net.minecraft.world.entity.player.Player,
            val pos: BlockPos,
            val vanillaResult: Boolean
        )

        val onAllowNearbyMonsters = Event.createReloadable(EntitySleepEvents.ALLOW_NEARBY_MONSTERS,
            { c -> EntitySleepEvents.AllowNearbyMonsters { a, b, c1 -> c(AllowNearbyMonstersArg(a, b, c1)) } },
            dispatch<AllowNearbyMonstersArg, EventResult>(EventResult.PASS)
        )

        data class AllowResettingTimeArg(val player: net.minecraft.world.entity.player.Player)

        val onAllowResettingTime = Event.createReloadable(EntitySleepEvents.ALLOW_RESETTING_TIME,
            { c -> EntitySleepEvents.AllowResettingTime { c(AllowResettingTimeArg(it)) } },
            all<AllowResettingTimeArg>()
        )

        data class ModifySleepingDirectionArg(
            val entity: LivingEntity,
            val pos: BlockPos,
            val direction: Direction?
        )

        val onModifySleepingDirection = Event.createReloadable(EntitySleepEvents.MODIFY_SLEEPING_DIRECTION,
            { c -> EntitySleepEvents.ModifySleepingDirection { a, b, c1 -> c(ModifySleepingDirectionArg(a, b, c1)) } },
            { events ->
                { arg: ModifySleepingDirectionArg ->
                    var dir = arg.direction
                    events.forEach { e -> dir = e(arg.copy(direction = dir)) }
                    ModifySleepingDirectionArg(arg.entity, arg.pos, dir).direction
                }
            }
        )

        data class SettingSpawnArg(
            val entity: LivingEntity,
            val pos: BlockPos
        )

        val onAllowSettingSpawn = Event.createReloadable(EntitySleepEvents.ALLOW_SETTING_SPAWN,
            { c -> EntitySleepEvents.AllowSettingSpawn { a, b -> c(SettingSpawnArg(a, b)) } },
            all<SettingSpawnArg>()
        )

        data class SetBedOccupationStateArg(
            val entity: LivingEntity,
            val pos: BlockPos,
            val state: BlockState,
            val occupied: Boolean
        )

        val onSetBedOccupationState = Event.createReloadable(EntitySleepEvents.SET_BED_OCCUPATION_STATE,
            { c -> EntitySleepEvents.SetBedOccupationState { a, b, c1, d -> c(SetBedOccupationStateArg(a, b, c1, d)) } },
            any<SetBedOccupationStateArg>()
        )

        data class ModifyWakeUpPositionArg(
            val entity: LivingEntity,
            val sleepingPos: BlockPos,
            val bedState: BlockState,
            val wakeUpPos: Vec3?
        )

        val onModifyWakeUpPosition = Event.createReloadable(EntitySleepEvents.MODIFY_WAKE_UP_POSITION,
            { c -> EntitySleepEvents.ModifyWakeUpPosition { a, b, c1, d -> c(ModifyWakeUpPositionArg(a, b, c1, d)) } },
            { events ->
                { arg: ModifyWakeUpPositionArg ->
                    var p = arg.wakeUpPos
                    events.forEach { e -> p = e(arg.copy(wakeUpPos = p)) }
                    ModifyWakeUpPositionArg(arg.entity, arg.sleepingPos, arg.bedState, p).wakeUpPos
                }
            }
        )
    }

    /**
     * Server-side living entity events (allow damage, after damage, allow death, after death, conversion).
     */
    object ServerLivingEntity {
        data class AllowDamageArg(
            val entity: net.minecraft.world.entity.LivingEntity,
            val source: net.minecraft.world.damagesource.DamageSource,
            val amount: Float
        )

        val onAllowDamage = Event.createReloadable(ServerLivingEntityEvents.ALLOW_DAMAGE,
            { c -> ServerLivingEntityEvents.AllowDamage { a, b, c1 -> c(AllowDamageArg(a, b, c1)) } },
            all<AllowDamageArg>()
        )

        data class AfterDamageArg(
            val entity: net.minecraft.world.entity.LivingEntity,
            val source: net.minecraft.world.damagesource.DamageSource,
            val initialDamage: Float,
            val finalDamage: Float,
            val handled: Boolean
        )

        val onAfterDamage = Event.createReloadable(ServerLivingEntityEvents.AFTER_DAMAGE,
            { c -> ServerLivingEntityEvents.AfterDamage {a, b, c1, d, e -> c(AfterDamageArg(a, b, c1, d, e))} },
            unit<AfterDamageArg>()
        )

        data class AllowDeathArg(
            val entity: net.minecraft.world.entity.LivingEntity,
            val source: net.minecraft.world.damagesource.DamageSource,
            val amount: Float
        )

        val onAllowDeath = Event.createReloadable(ServerLivingEntityEvents.ALLOW_DEATH,
            { c -> ServerLivingEntityEvents.AllowDeath { a, b, c1 -> c(AllowDeathArg(a, b, c1)) } },
            all<AllowDeathArg>()
        )

        data class AfterDeathArg(
            val entity: net.minecraft.world.entity.LivingEntity,
            val source: net.minecraft.world.damagesource.DamageSource
        )

        val onAfterDeath = Event.createReloadable(ServerLivingEntityEvents.AFTER_DEATH,
            { c -> ServerLivingEntityEvents.AfterDeath {a, b -> c(AfterDeathArg(a, b))} },
            unit<AfterDeathArg>()
        )

        data class MobConversionArg(
            val oldEntity: Mob,
            val newEntity: Mob,
            val keepEquipment: ConversionParams
        )

        val onMobConversion = Event.createReloadable(ServerLivingEntityEvents.MOB_CONVERSION,
            { c -> ServerLivingEntityEvents.MobConversion {a, b, c1 -> c(MobConversionArg(a, b, c1))} },
            unit<MobConversionArg>()
        )
    }

    /**
     * Server entity combat events (after killed other entity).
     */
    object ServerEntityCombat {
        data class AfterKilledOtherEntityArg(
            val world: ServerLevel,
            val entity: Entity,
            val killedEntity: LivingEntity,
            val source: net.minecraft.world.damagesource.DamageSource
        )

        val onAfterKilledOtherEntity = Event.createReloadable(ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY,
            { c -> ServerEntityCombatEvents.AfterKilledOtherEntity {a, b, c1, d -> c(AfterKilledOtherEntityArg(a, b, c1, d))} },
            unit<AfterKilledOtherEntityArg>()
        )
    }

    /**
     * Server entity world change events (after entity/player change world).
     */
    object ServerEntityLevelChange {
        data class AfterEntityChangeLevelArg(
            val originalEntity: Entity,
            val destinationEntity: Entity,
            val originalLevel: ServerLevel,
            val destinationLevel: ServerLevel
        )

        val onAfterEntityChangeLevel = Event.createReloadable(ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL,
            { c -> ServerEntityLevelChangeEvents.AfterEntityChange {a, b, c1, d -> c(AfterEntityChangeLevelArg(a, b, c1, d))} },
            unit<AfterEntityChangeLevelArg>()
        )

        data class AfterPlayerChangeLevelArg(
            val player: net.minecraft.server.level.ServerPlayer,
            val originalLevel: ServerLevel,
            val destinationLevel: ServerLevel
        )

        val onAfterPlayerChangeLevel = Event.createReloadable(ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL,
             { c -> ServerEntityLevelChangeEvents.AfterPlayerChange {a, b, c1 -> c(AfterPlayerChangeLevelArg(a, b, c1))} },
             unit<AfterPlayerChangeLevelArg>()
        )
    }

    /**
     * Mob effect events on the server (allow add / before/after add / allow early remove / before/after remove).
     */
    object ServerMobEffect {
        data class AllowMobEffectArg(
            val effectInstance: MobEffectInstance,
            val entity: Entity,
            val context: EffectEventContext
        )

        val onAllowAdd = Event.createReloadable(ServerMobEffectEvents.ALLOW_ADD,
            { c -> ServerMobEffectEvents.AllowAdd { a, b, c1 -> c(AllowMobEffectArg(a, b, c1)) } },
            all<AllowMobEffectArg>()
        )

        data class MobEffectArg(
            val effectInstance: MobEffectInstance,
            val entity: Entity,
            val context: EffectEventContext
        )

        val onBeforeAdd = Event.createReloadable(ServerMobEffectEvents.BEFORE_ADD,
            { c -> ServerMobEffectEvents.BeforeAdd {a, b, c1 -> c(MobEffectArg(a, b, c1))} },
            unit<MobEffectArg>()
        )

        val onAfterAdd = Event.createReloadable(ServerMobEffectEvents.AFTER_ADD,
             { c -> ServerMobEffectEvents.AfterAdd {a, b, c1 -> c(MobEffectArg(a, b, c1))} },
             unit<MobEffectArg>()
        )

        val onAllowEarlyRemove = Event.createReloadable(ServerMobEffectEvents.ALLOW_EARLY_REMOVE,
            { c -> ServerMobEffectEvents.AllowEarlyRemove { a, b, c1 -> c(AllowMobEffectArg(a, b, c1)) } },
            all<AllowMobEffectArg>()
        )

        val onBeforeRemove = Event.createReloadable(ServerMobEffectEvents.BEFORE_REMOVE,
            { c -> ServerMobEffectEvents.BeforeRemove {a, b, c1 -> c(MobEffectArg(a, b, c1))} },
            unit<MobEffectArg>()
        )

        val onAfterRemove = Event.createReloadable(ServerMobEffectEvents.AFTER_REMOVE,
             { c -> ServerMobEffectEvents.AfterRemove {a, b, c1 -> c(MobEffectArg(a, b, c1))} },
             unit<MobEffectArg>()
        )
    }

    /**
     * Player block break lifecycle events (before/after/canceled).
     */
    object PlayerBlockBreak {
        data class BlockBreakArg(
            val world: net.minecraft.world.level.Level,
            val player: net.minecraft.world.entity.player.Player,
            val pos: net.minecraft.core.BlockPos,
            val state: net.minecraft.world.level.block.state.BlockState,
            val blockEntity: net.minecraft.world.level.block.entity.BlockEntity?
        )

        val onBefore = Event.createReloadable(PlayerBlockBreakEvents.BEFORE,
            { c -> PlayerBlockBreakEvents.Before {a, b, c1, d, e -> c(BlockBreakArg(a, b, c1, d, e))} },
            all<BlockBreakArg>()
        )

        val onAfter = Event.createReloadable(PlayerBlockBreakEvents.AFTER,
            { c -> PlayerBlockBreakEvents.After {a, b, c1, d, e -> c(BlockBreakArg(a, b, c1, d, e))} },
            unit<BlockBreakArg>()
        )

        val onCanceled = Event.createReloadable(PlayerBlockBreakEvents.CANCELED,
             { c -> PlayerBlockBreakEvents.Canceled {a, b, c1, d, e -> c(BlockBreakArg(a, b, c1, d, e))} },
             unit<BlockBreakArg>()
        )
    }

    /**
     * Player pick-item events (from block / from entity).
     */
    object PlayerPickItem {
        data class PickFromBlockArg(
            val player: net.minecraft.server.level.ServerPlayer,
            val pos: BlockPos,
            val state: BlockState,
            val includeData: Boolean
        )

        val onPickFromBlock = Event.createReloadable(PlayerPickItemEvents.BLOCK,
            { c -> PlayerPickItemEvents.PickItemFromBlock { a, b, c1, d -> c(PickFromBlockArg(a, b, c1, d)) } },
            firstNotNullOfOrNull<PickFromBlockArg, ItemStack>()
        )

        data class PickFromEntityArg(
            val player: net.minecraft.server.level.ServerPlayer,
            val entity: Entity,
            val includeData: Boolean
        )

        val onPickFromEntity = Event.createReloadable(PlayerPickItemEvents.ENTITY,
             { c -> PlayerPickItemEvents.PickItemFromEntity { a, b, c1 -> c(PickFromEntityArg(a, b, c1)) } },
             firstNotNullOfOrNull<PickFromEntityArg, ItemStack>()
        )
    }

    /**
     * Default item component modification events.
     */
    object DefaultItemComponent {
        data class ModifyContextArg(val context: DefaultItemComponentEvents.ModifyContext)

        val onModify = Event.createReloadable(DefaultItemComponentEvents.MODIFY,
            { c -> DefaultItemComponentEvents.ModifyCallback {c(ModifyContextArg(it))} },
            unit<ModifyContextArg>()
        )
    }

    /**
     * Enchantment related events (allow enchanting with TriState semantics, modify).
     */
    object Enchantment {
        data class AllowEnchantingArg(
            val enchantment: Holder<net.minecraft.world.item.enchantment.Enchantment>,
            val target: ItemStack,
            val context: EnchantingContext
        )

        val onAllowEnchanting = Event.createReloadable(EnchantmentEvents.ALLOW_ENCHANTING,
            { c -> EnchantmentEvents.AllowEnchanting { a, b, c1 -> c(AllowEnchantingArg(a, b, c1)) } },
            triState<AllowEnchantingArg>()
        )

        data class ModifyEnchantmentArg(
            val key: ResourceKey<net.minecraft.world.item.enchantment.Enchantment>,
            val builder: Builder,
            val enchantments: EnchantmentSource
        )

        val onModify = Event.createReloadable(EnchantmentEvents.MODIFY,
            { c -> EnchantmentEvents.Modify {a, b, c1 -> c(ModifyEnchantmentArg(a, b, c1))} },
            unit<ModifyEnchantmentArg>()
        )
    }

    /**
     * Server-side message events (chat/game/command allow and handlers).
     */
    object ServerMessage {
        data class AllowChatMessageArg(
            val message: PlayerChatMessage,
            val sender: net.minecraft.server.level.ServerPlayer,
            val params: Bound
        )

        val onAllowChatMessage = Event.createReloadable(ServerMessageEvents.ALLOW_CHAT_MESSAGE,
            { c -> ServerMessageEvents.AllowChatMessage { a, b, c1 -> c(AllowChatMessageArg(a, b, c1)) } },
            all<AllowChatMessageArg>()
        )

        data class AllowGameMessageArg(
            val server: MinecraftServer,
            val message: Component,
            val overlay: Boolean
        )

        val onAllowGameMessage = Event.createReloadable(ServerMessageEvents.ALLOW_GAME_MESSAGE,
            { c -> ServerMessageEvents.AllowGameMessage { a, b, c1 -> c(AllowGameMessageArg(a, b, c1)) } },
            all<AllowGameMessageArg>()
        )

        data class AllowCommandMessageArg(
            val message: PlayerChatMessage,
            val source: CommandSourceStack,
            val params: Bound
        )

        val onAllowCommandMessage = Event.createReloadable(ServerMessageEvents.ALLOW_COMMAND_MESSAGE,
            { c -> ServerMessageEvents.AllowCommandMessage { a, b, c1 -> c(AllowCommandMessageArg(a, b, c1)) } },
            all<AllowCommandMessageArg>()
        )

        data class ChatMessageArg(
            val message: PlayerChatMessage,
            val sender: net.minecraft.server.level.ServerPlayer,
            val params: Bound
        )

        val onChatMessage = Event.createReloadable(ServerMessageEvents.CHAT_MESSAGE,
            { c -> ServerMessageEvents.ChatMessage {a, b, c1 -> c(ChatMessageArg(a, b, c1))} },
            unit<ChatMessageArg>()
        )

        data class GameMessageArg(
            val server: MinecraftServer,
            val message: Component,
            val overlay: Boolean
        )

        val onGameMessage = Event.createReloadable(ServerMessageEvents.GAME_MESSAGE,
             { c -> ServerMessageEvents.GameMessage {a, b, c1 -> c(GameMessageArg(a, b, c1))} },
             unit<GameMessageArg>()
        )

        data class CommandMessageArg(
            val message: PlayerChatMessage,
            val source: CommandSourceStack,
            val params: Bound
        )

        val onCommandMessage = Event.createReloadable(ServerMessageEvents.COMMAND_MESSAGE,
             { c -> ServerMessageEvents.CommandMessage {a, b, c1 -> c(CommandMessageArg(a, b, c1))} },
             unit<CommandMessageArg>()
        )
    }
}
