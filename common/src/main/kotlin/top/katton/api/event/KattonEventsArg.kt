package top.katton.api.event

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.network.chat.ChatType.Bound
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.FullChunkStatus
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.ConversionParams
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import top.katton.bridger.EnchantingContext
import top.katton.util.CancellableEventArg

/**
 * Event argument data classes for server-side events.
 *
 * This file contains all the data classes used as arguments for various events
 * in the Katton event system. Each data class represents the context passed
 * to event handlers when the corresponding event is triggered.
 *
 * Events marked with [CancellableEventArg] can be cancelled to prevent the
 * default game behavior.
 */

/**
 * Argument for server-level events.
 *
 * @property server The MinecraftServer instance
 */
@JvmInline
value class ServerArg(val server: MinecraftServer)

/**
 * Argument for datapack contents synchronization event.
 *
 * @property player The player receiving the sync
 * @property joined Whether this sync is due to player joining
 */
data class SyncDatapackContentsArg(
    val player: ServerPlayer,
    val joined: Boolean
)

/**
 * Argument for datapack reload start event.
 *
 * @property server The MinecraftServer instance
 * @property resourceManager The resource manager being reloaded
 */
data class StartDatapackReloadArg(
    val server: MinecraftServer,
    val resourceManager: ResourceManager
)

/**
 * Argument for datapack reload end event.
 *
 * @property server The MinecraftServer instance
 * @property resourceManager The resource manager that was reloaded
 * @property success Whether the reload completed successfully
 */
data class EndDatapackReloadArg(
    val server: MinecraftServer,
    val resourceManager: ResourceManager,
    val success: Boolean
)

/**
 * Argument for server save event.
 *
 * @property server The MinecraftServer instance
 * @property flush Whether data should be flushed to disk
 * @property force Whether this is a forced save
 */
data class ServerSaveArg(
    val server: MinecraftServer,
    val flush: Boolean,
    val force: Boolean
)

/**
 * Argument for server tick event.
 *
 * @property server The MinecraftServer instance
 */
@JvmInline
value class ServerTickArg(val server: MinecraftServer)

/**
 * Argument for world tick event.
 *
 * @property world The ServerLevel being ticked
 */
@JvmInline
value class WorldTickArg(val world: ServerLevel)

/**
 * Argument for entity load event.
 *
 * Triggered when an entity is loaded into a world.
 * Can be cancelled to prevent the entity from loading.
 *
 * @property entity The entity being loaded
 * @property world The ServerLevel the entity is loading into
 */
data class EntityLoadArg(
    val entity: Entity,
    val world: ServerLevel
): CancellableEventArg()

/**
 * Argument for entity unload event.
 *
 * Triggered when an entity is unloaded from a world.
 *
 * @property entity The entity being unloaded
 * @property world The ServerLevel the entity is unloading from
 */
data class EntityUnloadArg(
    val entity: Entity,
    val world: ServerLevel
)

/**
 * Argument for equipment change event.
 *
 * Triggered when a living entity's equipment changes.
 *
 * @property entity The entity whose equipment changed
 * @property slot The equipment slot that changed
 * @property from The previous ItemStack in the slot
 * @property to The new ItemStack in the slot
 */
data class EquipmentChangeArg(
    val entity: LivingEntity,
    val slot: EquipmentSlot,
    val from: ItemStack,
    val to: ItemStack
)

/**
 * Argument for chunk load event.
 *
 * @property world The ServerLevel containing the chunk
 * @property chunk The LevelChunk that was loaded
 * @property generated Whether the chunk was newly generated
 */
data class ChunkLoadArg(
    val world: ServerLevel,
    val chunk: LevelChunk,
    val generated: Boolean
)

/**
 * Argument for chunk unload event.
 *
 * @property world The ServerLevel containing the chunk
 * @property chunk The LevelChunk being unloaded
 */
data class ChunkUnloadArg(
    val world: ServerLevel,
    val chunk: LevelChunk
)

/**
 * Argument for chunk status change event.
 *
 * Triggered when a chunk's loading status changes.
 *
 * @property world The ServerLevel containing the chunk
 * @property chunk The LevelChunk whose status changed
 * @property oldStatus The previous chunk status
 * @property newStatus The new chunk status
 */
data class ChunkStatusChangeArg(
    val world: ServerLevel,
    val chunk: LevelChunk,
    val oldStatus: FullChunkStatus,
    val newStatus: FullChunkStatus
)

/**
 * Argument for block entity load event.
 *
 * @property blockEntity The BlockEntity that was loaded
 * @property world The ServerLevel containing the block entity
 */
data class BlockEntityLoadArg(
    val blockEntity: BlockEntity,
    val world: ServerLevel
)

/**
 * Argument for block break event.
 *
 * Can be cancelled to prevent the block from being broken.
 *
 * @property world The Level containing the block
 * @property player The player breaking the block
 * @property pos The position of the block
 * @property state The BlockState being broken
 * @property blockEntity The BlockEntity at the position, if any
 */
data class BlockBreakArg(
    val world: Level,
    val player: Player,
    val pos: BlockPos,
    val state: BlockState,
    val blockEntity: BlockEntity?,
) : CancellableEventArg()

/**
 * Argument for block place event.
 *
 * Can be cancelled to prevent the block from being placed.
 *
 * @property world The Level where the block is being placed
 * @property player The player placing the block (may be null for non-player placement)
 * @property pos The position where the block is being placed
 * @property state The BlockState being placed
 * @property blockEntity The BlockEntity being placed, if any
 */
data class BlockPlaceArg(
    val world: Level,
    val player: Player?,
    val pos: BlockPos,
    val state: BlockState,
    val blockEntity: BlockEntity?
): CancellableEventArg()

/**
 * Argument for item use on block event.
 *
 * @property stack The ItemStack being used
 * @property state The BlockState of the block being interacted with
 * @property world The Level containing the block
 * @property pos The position of the block
 * @property player The player using the item
 * @property hand The hand holding the item
 * @property hitResult The block hit result containing face and position details
 */
data class UseItemOnArg(
    val stack: ItemStack,
    val state: BlockState,
    val world: Level,
    val pos: BlockPos,
    val player: Player,
    val hand: InteractionHand,
    val hitResult: BlockHitResult
)

/**
 * Argument for block interaction without item event.
 *
 * @property state The BlockState of the block being interacted with
 * @property world The Level containing the block
 * @property pos The position of the block
 * @property player The player interacting with the block
 * @property hitResult The block hit result
 */
data class UseWithoutItemOnArg(
    val state: BlockState,
    val world: Level,
    val pos: BlockPos,
    val player: Player,
    val hitResult: BlockHitResult
)

/**
 * Argument for enchantment allowance check event.
 *
 * Used to determine if an enchantment can be applied to an item.
 *
 * @property enchantment The enchantment being checked
 * @property target The target ItemStack
 * @property context The enchanting context (PRIMARY or ACCEPTABLE)
 */
data class AllowEnchantingArg(
    val enchantment: Holder<Enchantment>,
    val target: ItemStack,
    val context: EnchantingContext
)

/**
 * Argument for enchantment modification event.
 *
 * @property key The ResourceKey of the enchantment being modified
 * @property builder The Enchantment.Builder for modification
 */
data class ModifyEnchantmentArg(
    val key: ResourceKey<Enchantment>,
    val builder: Enchantment.Builder
)

/**
 * Argument for elytra flight allowance check.
 *
 * @property entity The entity attempting to use elytra flight
 */
data class ElytraAllowArg(val entity: LivingEntity)

/**
 * Argument for custom elytra flight event.
 *
 * @property entity The entity using elytra flight
 * @property tickElytra Whether vanilla elytra tick logic should run
 */
data class ElytraCustomArg(
    val entity: LivingEntity,
    val tickElytra: Boolean
)

/**
 * Argument for sleeping allowance check.
 *
 * @property entity The entity attempting to sleep
 * @property pos The position of the bed
 */
data class AllowSleepingArg(
    val entity: LivingEntity,
    val pos: BlockPos
)

/**
 * Argument for sleeping event.
 *
 * @property entity The entity that is sleeping
 * @property pos The position of the bed
 */
data class SleepingArg(
    val entity: LivingEntity,
    val pos: BlockPos
)

/**
 * Argument for bed usage allowance check.
 *
 * @property entity The entity attempting to use the bed
 * @property pos The position of the bed
 * @property state The BlockState of the bed
 * @property vanillaResult The vanilla game's result for this check
 */
data class AllowBedArg(
    val entity: LivingEntity,
    val pos: BlockPos,
    val state: BlockState,
    val vanillaResult: Boolean
)

/**
 * Argument for nearby monsters check during sleeping.
 *
 * @property entity The player attempting to sleep
 * @property pos The position of the bed
 * @property vanillaResult The vanilla game's result for this check
 */
data class AllowNearbyMonstersArg(
    val entity: Player,
    val pos: BlockPos,
    val vanillaResult: Boolean
)

/**
 * Argument for time reset allowance check.
 *
 * @property player The player attempting to reset the time
 */
data class AllowResettingTimeArg(val player: Player)

/**
 * Argument for sleeping direction modification.
 *
 * @property entity The entity sleeping
 * @property pos The position of the bed
 * @property direction The direction the entity is facing while sleeping
 */
data class ModifySleepingDirectionArg(
    val entity: LivingEntity,
    val pos: BlockPos,
    val direction: Direction?
)

/**
 * Argument for spawn point setting allowance.
 *
 * @property entity The entity whose spawn point is being set
 * @property pos The position of the spawn point
 */
data class AllowSettingSpawnArg(
    val entity: LivingEntity,
    val pos: BlockPos
)

/**
 * Argument for bed occupation state change.
 *
 * @property entity The entity occupying the bed
 * @property pos The position of the bed
 * @property state The BlockState of the bed
 * @property occupied Whether the bed is now occupied
 */
data class SetBedOccupationStateArg(
    val entity: LivingEntity,
    val pos: BlockPos,
    val state: BlockState,
    val occupied: Boolean
)

/**
 * Argument for wake-up position modification.
 *
 * @property entity The entity waking up
 * @property sleepingPos The position where the entity was sleeping
 * @property bedState The BlockState of the bed
 * @property wakeUpPos The calculated wake-up position (may be modified)
 */
data class ModifyWakeUpPositionArg(
    val entity: LivingEntity,
    val sleepingPos: BlockPos,
    val bedState: BlockState,
    val wakeUpPos: Vec3?
)

/**
 * Argument for item use on block context.
 *
 * @property context The UseOnContext containing all interaction details
 */
data class ItemUseOnArg(val context: UseOnContext)

/**
 * Argument for item use event.
 *
 * @property world The Level where the item is being used
 * @property player The player using the item
 * @property hand The hand holding the item
 */
data class ItemUseArg(
    val world: Level,
    val player: Player,
    val hand: InteractionHand
)

/**
 * Argument for loot table replacement event.
 *
 * @property key The ResourceKey of the loot table
 * @property original The original LootTable
 * @property registries The registry lookup provider
 */
data class LootTableReplaceArg(
    val key: ResourceKey<LootTable>,
    val original: LootTable,
    val registries: HolderLookup.Provider
)

/**
 * Argument for loot table modification event.
 *
 * @property key The ResourceKey of the loot table
 * @property tableBuilder The LootTable.Builder for modification
 * @property registries The registry lookup provider
 */
data class LootTableModifyArg(
    val key: ResourceKey<LootTable>,
    val tableBuilder: LootTable.Builder,
    val registries: HolderLookup.Provider
)

/**
 * Argument for all loot tables load event.
 *
 * @property resourceManager The resource manager
 * @property lootDataManager The loot data manager registry
 */
data class LootTableAllLoadArg(
    val resourceManager: ResourceManager,
    val lootDataManager: Registry<LootTable>
)

/**
 * Argument for loot table drops modification.
 *
 * @property table The loot table holder
 * @property context The loot context for the drop
 * @property drops The list of dropped ItemStacks (may be modified)
 */
data class LootTableModifyDropsArg(
    val table: Holder<LootTable>,
    val context: LootContext,
    val drops: List<ItemStack>
)

/**
 * Argument for player attack block event.
 *
 * @property player The player attacking the block
 * @property world The Level containing the block
 * @property hand The hand used to attack
 * @property pos The position of the block being attacked
 * @property direction The face being attacked
 */
data class PlayerAttackBlockArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand,
    val pos: BlockPos,
    val direction: Direction,
)

/**
 * Argument for player attack entity event.
 *
 * @property player The player attacking
 * @property world The Level where the attack occurs
 * @property hand The hand used to attack
 * @property entity The entity being attacked
 * @property hitResult The entity hit result
 */
data class PlayerAttackEntityArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand,
    val entity: Entity,
    val hitResult: EntityHitResult?
)

/**
 * Argument for player use block event.
 *
 * @property player The player interacting
 * @property world The Level containing the block
 * @property hand The hand used for interaction
 * @property hitResult The block hit result
 */
data class PlayerUseBlockArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand,
    val hitResult: BlockHitResult
)

/**
 * Argument for player use entity event.
 *
 * @property player The player interacting
 * @property world The Level containing the entity
 * @property hand The hand used for interaction
 * @property entity The entity being interacted with
 * @property hitResult The entity hit result
 */
data class PlayerUseEntityArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand,
    val entity: Entity,
    val hitResult: EntityHitResult?
)

/**
 * Argument for player use item event.
 *
 * @property player The player using the item
 * @property world The Level where the item is used
 * @property hand The hand holding the item
 */
data class PlayerUseItemArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand
)

/**
 * Argument for player pick block event.
 *
 * @property player The player picking the block
 * @property pos The position of the block being picked
 * @property state The BlockState of the block
 * @property includeData Whether block entity data should be included
 */
data class PlayerPickFromBlockArg(
    val player: ServerPlayer,
    val pos: BlockPos,
    val state: BlockState,
    val includeData: Boolean
)

/**
 * Argument for player pick entity event.
 *
 * @property player The player picking the entity
 * @property entity The entity being picked
 * @property includeData Whether entity data should be included
 */
data class PlayerPickFromEntityArg(
    val player: ServerPlayer,
    val entity: Entity,
    val includeData: Boolean
)

/**
 * Argument for after entity killed another entity event.
 *
 * @property world The ServerLevel where the kill occurred
 * @property entity The killer entity
 * @property killedEntity The entity that was killed
 * @property source The damage source that caused the death
 */
data class AfterKilledOtherEntityArg(
    val world: ServerLevel,
    val entity: Entity,
    val killedEntity: LivingEntity,
    val source: DamageSource
)

/**
 * Argument for entity level change event.
 *
 * @property originalEntity The entity before level change
 * @property destinationEntity The entity after level change
 * @property originalLevel The ServerLevel the entity was in
 * @property destinationLevel The ServerLevel the entity is now in
 */
data class AfterEntityChangeLevelArg(
    val originalEntity: Entity,
    val destinationEntity: Entity,
    val originalLevel: ServerLevel,
    val destinationLevel: ServerLevel
)

/**
 * Argument for player level change event.
 *
 * @property player The ServerPlayer who changed level
 * @property originalLevel The ServerLevel the player was in
 * @property destinationLevel The ServerLevel the player is now in
 */
data class AfterPlayerChangeLevelArg(
    val player: ServerPlayer,
    val originalLevel: ServerLevel,
    val destinationLevel: ServerLevel
)

/**
 * Argument for damage allowance check.
 *
 * @property entity The entity potentially receiving damage
 * @property source The damage source
 * @property amount The damage amount
 */
data class AllowDamageArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val amount: Float
)

/**
 * Argument for after damage event.
 *
 * @property entity The entity that received damage
 * @property source The damage source
 * @property initialDamage The damage amount before reductions
 * @property finalDamage The damage amount after reductions
 * @property handled Whether the damage was handled
 */
data class AfterDamageArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val initialDamage: Float,
    val finalDamage: Float,
    val handled: Boolean
)

/**
 * Argument for death allowance check.
 *
 * @property entity The entity potentially dying
 * @property source The damage source causing death
 * @property amount The damage amount
 */
data class AllowDeathArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val amount: Float
)

/**
 * Argument for after death event.
 *
 * @property entity The entity that died
 * @property source The damage source that caused death
 */
data class AfterDeathArg(
    val entity: LivingEntity,
    val source: DamageSource
)

/**
 * Argument for mob conversion event.
 *
 * @property oldEntity The original Mob before conversion
 * @property newEntity The new Mob after conversion
 * @property keepEquipment Parameters for equipment retention during conversion
 */
data class MobConversionArg(
    val oldEntity: Mob,
    val newEntity: Mob,
    val keepEquipment: ConversionParams?
)

/**
 * Argument for chat message allowance check.
 *
 * @property message The PlayerChatMessage being sent
 * @property sender The ServerPlayer sending the message
 * @property params The chat type bound parameters
 */
data class AllowChatMessageArg(
    val message: PlayerChatMessage,
    val sender: ServerPlayer,
    val params: Bound
)

/**
 * Argument for game message allowance check.
 *
 * @property server The MinecraftServer instance
 * @property message The message Component
 * @property overlay Whether the message should show as overlay
 */
data class AllowGameMessageArg(
    val server: MinecraftServer,
    val message: Component,
    val overlay: Boolean
)

/**
 * Argument for command message allowance check.
 *
 * @property message The PlayerChatMessage from command
 * @property source The CommandSourceStack executing the command
 * @property params The chat type bound parameters
 */
data class AllowCommandMessageArg(
    val message: PlayerChatMessage,
    val source: CommandSourceStack,
    val params: Bound
)

/**
 * Argument for chat message event.
 *
 * @property message The PlayerChatMessage being sent
 * @property sender The ServerPlayer sending the message
 * @property params The chat type bound parameters
 */
data class ChatMessageArg(
    val message: PlayerChatMessage,
    val sender: ServerPlayer,
    val params: Bound
)

/**
 * Argument for game message event.
 *
 * @property server The MinecraftServer instance
 * @property message The message Component
 * @property overlay Whether the message shows as overlay
 */
data class GameMessageArg(
    val server: MinecraftServer,
    val message: Component,
    val overlay: Boolean
)

/**
 * Argument for command message event.
 *
 * @property message The PlayerChatMessage from command
 * @property source The CommandSourceStack executing the command
 * @property params The chat type bound parameters
 */
data class CommandMessageArg(
    val message: PlayerChatMessage,
    val source: CommandSourceStack,
    val params: Bound
)

/**
 * Argument for player-related events.
 *
 * @property player The ServerPlayer involved in the event
 */
data class PlayerArg(val player: ServerPlayer)

/**
 * Argument for player respawn event.
 *
 * @property oldPlayer The ServerPlayer before respawn
 * @property newPlayer The ServerPlayer after respawn
 * @property alive Whether the player was alive before respawn
 */
data class ServerPlayerAfterRespawnArg(
    val oldPlayer: ServerPlayer,
    val newPlayer: ServerPlayer,
    val alive: Boolean
)

/**
 * Argument for player death allowance check.
 *
 * @property player The ServerPlayer potentially dying
 * @property damageSource The damage source causing death
 * @property damageAmount The damage amount
 */
data class ServerPlayerAllowDeathArg(
    val player: ServerPlayer,
    val damageSource: DamageSource,
    val damageAmount: Float
)

/**
 * Argument for player copy event (dimension change, etc.).
 *
 * @property oldPlayer The original ServerPlayer
 * @property newPlayer The new ServerPlayer copy
 * @property alive Whether the player was alive during copy
 */
data class ServerPlayerCopyArg(
    val oldPlayer: ServerPlayer,
    val newPlayer: ServerPlayer,
    val alive: Boolean
)

/**
 * Argument for animal tame event.
 *
 * Can be cancelled to prevent taming.
 *
 * @property animal The Animal being tamed
 * @property tamer The Player taming the animal
 */
data class AnimalTameArg(
    val animal: Animal,
    val tamer: Player
): CancellableEventArg()

/**
 * Argument for baby spawn event.
 *
 * Can be cancelled to prevent baby spawning.
 *
 * @property parentA The first parent LivingEntity
 * @property parentB The second parent LivingEntity
 * @property child The baby AgeableMob (may be null)
 */
data class BabySpawnArg(
    val parentA: LivingEntity,
    val parentB: LivingEntity,
    val child: AgeableMob?
): CancellableEventArg()

/**
 * Argument for critical hit event.
 *
 * @property player The Player making the attack
 * @property target The Entity being attacked
 * @property isVanillaCritical Whether vanilla considers this a critical hit
 */
data class CriticalHitArg(
    val player: Player,
    val target: Entity,
    val isVanillaCritical: Boolean
)

/**
 * Argument for player wake up event.
 *
 * @property player The Player waking up
 * @property wakeImmediately Whether to wake immediately
 * @property updateLevelList Whether to update the level list
 */
data class PlayerWakeUpArg(
    val player: Player,
    val wakeImmediately: Boolean,
    val updateLevelList: Boolean
)

/**
 * Argument for entity teleport event.
 *
 * Can be cancelled to prevent teleportation.
 *
 * @property entity The Entity teleporting
 * @property fromX The original X coordinate
 * @property fromY The original Y coordinate
 * @property fromZ The original Z coordinate
 * @property toX The destination X coordinate
 * @property toY The destination Y coordinate
 * @property toZ The destination Z coordinate
 */
data class EntityTeleportArg(
    val entity: Entity,
    val fromX: Double,
    val fromY: Double,
    val fromZ: Double,
    val toX: Double,
    val toY: Double,
    val toZ: Double
): CancellableEventArg()

/**
 * Argument for enderman anger event.
 *
 * Can be cancelled to prevent the enderman from becoming angry.
 *
 * @property enderman The EnderMan becoming angry
 * @property player The Player the enderman is targeting
 */
data class EndermanAngerArg(
    val enderman: EnderMan,
    val player: Player
): CancellableEventArg()

/**
 * Argument for explosion start event.
 *
 * Can be cancelled to prevent the explosion.
 *
 * @property level The Level where the explosion is starting
 * @property explosion The Explosion instance
 */
data class ExplosionStartArg(
    val level: Level,
    val explosion: Explosion
): CancellableEventArg()

/**
 * Argument for explosion detonate event.
 *
 * @property level The Level where the explosion is detonating
 * @property explosion The Explosion instance
 * @property affectedEntities List of entities affected by the explosion
 */
data class ExplosionDetonateArg(
    val level: Level,
    val explosion: Explosion,
    val affectedEntities: List<Entity>
)

/**
 * Argument for item toss event.
 *
 * @property player The Player tossing the item
 * @property item The ItemEntity being tossed
 */
data class ItemTossArg(
    val player: Player,
    val item: ItemEntity
)

/**
 * Argument for item destruction event.
 *
 * @property player The Player whose item was destroyed
 * @property item The ItemStack that was destroyed
 * @property hand The hand the item was in (may be null)
 */
data class PlayerDestroyItemArg(
    val player: Player,
    val item: ItemStack,
    val hand: InteractionHand?
)

/**
 * Argument for item use start event.
 *
 * Can be cancelled to prevent item use.
 *
 * @property entity The LivingEntity starting to use the item
 * @property item The ItemStack being used
 * @property hand The hand holding the item
 * @property duration The initial use duration in ticks
 */
data class LivingUseItemStartArg(
    val entity: LivingEntity,
    val item: ItemStack,
    val hand: InteractionHand,
    val duration: Int
): CancellableEventArg()

/**
 * Argument for item use tick event.
 *
 * @property entity The LivingEntity using the item
 * @property item The ItemStack being used
 * @property duration The remaining use duration (modifiable)
 */
data class LivingUseItemTickArg(
    val entity: LivingEntity,
    val item: ItemStack,
    var duration: Int
): CancellableEventArg()

/**
 * Argument for item use stop event.
 *
 * @property entity The LivingEntity that stopped using the item
 * @property item The ItemStack that was being used
 * @property duration The remaining use duration when stopped
 */
data class LivingUseItemStopArg(
    val entity: LivingEntity,
    val item: ItemStack,
    val duration: Int
): CancellableEventArg()

/**
 * Argument for item use finish event.
 *
 * @property entity The LivingEntity that finished using the item
 * @property item The ItemStack that was used
 * @property duration The total use duration in ticks
 * @property result The resulting ItemStack (modifiable)
 */
data class LivingUseItemFinishArg(
    val entity: LivingEntity,
    val item: ItemStack,
    val duration: Int,
    var result: ItemStack
)

/**
 * Argument for NeoForge player attack entity event.
 *
 * Can be cancelled to prevent the attack.
 *
 * @property player The Player attacking
 * @property target The Entity being attacked
 */
data class NeoPlayerAttackEntityArg(
    val player: Player,
    val target: Entity
) : CancellableEventArg()

/**
 * Argument for NeoForge player interact entity event.
 *
 * Can be cancelled to prevent the interaction.
 *
 * @property player The Player interacting
 * @property entity The Entity being interacted with
 * @property hand The hand used for interaction
 */
data class NeoPlayerInteractEntityArg(
    val player: Player,
    val entity: Entity,
    val hand: InteractionHand
) : CancellableEventArg()

/**
 * Argument for NeoForge player interact block event.
 *
 * Can be cancelled to prevent the interaction.
 *
 * @property player The Player interacting
 * @property pos The BlockPos being interacted with
 * @property face The Direction of the face being interacted with
 * @property hand The hand used for interaction
 */
data class NeoPlayerInteractBlockArg(
    val player: Player,
    val pos: BlockPos,
    val face: Direction?,
    val hand: InteractionHand
) : CancellableEventArg()

/**
 * Argument for NeoForge player interact item event.
 *
 * Can be cancelled to prevent the interaction.
 *
 * @property player The Player interacting
 * @property hand The hand holding the item
 */
data class NeoPlayerInteractItemArg(
    val player: Player,
    val hand: InteractionHand
) : CancellableEventArg()

/**
 * Argument for NeoForge player left click block event.
 *
 * Can be cancelled to prevent the action.
 *
 * @property player The Player left-clicking
 * @property pos The BlockPos being clicked
 * @property face The Direction of the face being clicked
 */
data class NeoPlayerLeftClickBlockArg(
    val player: Player,
    val pos: BlockPos,
    val face: Direction?
) : CancellableEventArg()

/**
 * Argument for player XP change event.
 *
 * Can be cancelled to prevent the XP change.
 *
 * @property player The Player receiving XP
 * @property amount The amount of XP points being added
 */
data class PlayerXpChangeArg(
    val player: Player,
    val amount: Int
): CancellableEventArg()

/**
 * Argument for player XP level change event.
 *
 * Can be cancelled to prevent the level change.
 *
 * @property player The Player receiving levels
 * @property levels The number of levels being added
 */
data class PlayerXpLevelChangeArg(
    val player: Player,
    val levels: Int
): CancellableEventArg()

/**
 * Argument for player pickup XP orb event.
 *
 * Can be cancelled to prevent the pickup.
 *
 * @property player The Player picking up the orb
 * @property orb The ExperienceOrb being picked up
 */
data class PlayerPickupXpArg(
    val player: Player,
    val orb: ExperienceOrb
): CancellableEventArg()

/**
 * Argument for living entity hurt event.
 *
 * Can be cancelled to prevent the damage.
 *
 * @property entity The LivingEntity being hurt
 * @property source The DamageSource causing the hurt
 * @property amount The damage amount
 */
data class LivingHurtArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val amount: Float
): CancellableEventArg()

/**
 * Argument for NeoForge living damage event.
 *
 * @property entity The LivingEntity receiving damage
 * @property source The DamageSource
 * @property amount The damage amount
 */
data class NeoLivingDamageArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val amount: Float
)

/**
 * Argument for NeoForge living death event.
 *
 * @property entity The LivingEntity that died
 * @property source The DamageSource that caused death
 */
data class NeoLivingDeathArg(
    val entity: LivingEntity,
    val source: DamageSource
)

/**
 * Argument for living entity drops event.
 *
 * Can be cancelled to prevent drops.
 *
 * @property entity The LivingEntity dropping items
 * @property source The DamageSource that caused the drops
 * @property drops The list of ItemStacks being dropped
 */
data class LivingDropsArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val drops: List<ItemStack>
): CancellableEventArg()

/**
 * Argument for living entity fall event.
 *
 * Can be cancelled to prevent fall damage.
 *
 * @property entity The LivingEntity falling
 * @property distance The fall distance in blocks
 * @property damageMultiplier The damage multiplier
 */
data class LivingFallArg(
    val entity: LivingEntity,
    val distance: Double,
    val damageMultiplier: Float
): CancellableEventArg()

/**
 * Argument for living entity jump event.
 *
 * @property entity The LivingEntity jumping
 */
@JvmInline
value class LivingJumpArg(val entity: LivingEntity)

/**
 * Argument for server chat event.
 *
 * Can be cancelled to prevent the message from being sent.
 *
 * @property player The ServerPlayer sending the message
 * @property message The raw message string
 * @property component The message as a Component
 */
data class ServerChatArg(
    val player: ServerPlayer,
    val message: String,
    val component: Component
) : CancellableEventArg()

/**
 * Argument for shield block event.
 *
 * @property entity The LivingEntity blocking with a shield
 * @property source The DamageSource being blocked
 * @property blockedDamage The amount of damage blocked
 * @property originalBlockedState Whether the shield was originally blocking
 */
data class ShieldBlockArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val blockedDamage: Float,
    val originalBlockedState: Boolean
)
