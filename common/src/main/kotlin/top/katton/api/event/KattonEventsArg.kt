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


@JvmInline
value class ServerArg(val server: MinecraftServer)

data class SyncDatapackContentsArg(
    val player: ServerPlayer,
    val joined: Boolean
)

data class StartDatapackReloadArg(
    val server: MinecraftServer,
    val resourceManager: ResourceManager
)

data class EndDatapackReloadArg(
    val server: MinecraftServer,
    val resourceManager: ResourceManager,
    val success: Boolean
)

data class ServerSaveArg(
    val server: MinecraftServer,
    val flush: Boolean,
    val force: Boolean
)

@JvmInline
value class ServerTickArg(val server: MinecraftServer)

@JvmInline
value class WorldTickArg(val world: ServerLevel)

data class EntityLoadArg(
    val entity: Entity,
    val world: ServerLevel
): CancellableEventArg()

data class EntityUnloadArg(
    val entity: Entity,
    val world: ServerLevel
)

data class EquipmentChangeArg(
    val entity: LivingEntity,
    val slot: EquipmentSlot,
    val from: ItemStack,
    val to: ItemStack
)

data class ChunkLoadArg(
    val world: ServerLevel,
    val chunk: LevelChunk,
    val generated: Boolean
)

data class ChunkUnloadArg(
    val world: ServerLevel,
    val chunk: LevelChunk
)


data class ChunkStatusChangeArg(
    val world: ServerLevel,
    val chunk: LevelChunk,
    val oldStatus: FullChunkStatus,
    val newStatus: FullChunkStatus
)

data class BlockEntityLoadArg(
    val blockEntity: BlockEntity,
    val world: ServerLevel
)

data class BlockBreakArg(
    val world: Level,
    val player: Player,
    val pos: BlockPos,
    val state: BlockState,
    val blockEntity: BlockEntity?,
) : CancellableEventArg()

data class BlockPlaceArg(
    val world: Level,
    val player: Player?,
    val pos: BlockPos,
    val state: BlockState,
    val blockEntity: BlockEntity?
): CancellableEventArg()

data class UseItemOnArg(
    val stack: ItemStack,
    val state: BlockState,
    val world: Level,
    val pos: BlockPos,
    val player: Player,
    val hand: InteractionHand,
    val hitResult: BlockHitResult
)

data class UseWithoutItemOnArg(
    val state: BlockState,
    val world: Level,
    val pos: BlockPos,
    val player: Player,
    val hitResult: BlockHitResult
)

data class AllowEnchantingArg(
    val enchantment: Holder<Enchantment>,
    val target: ItemStack,
    val context: EnchantingContext
)

data class ModifyEnchantmentArg(
    val key: ResourceKey<Enchantment>,
    val builder: Enchantment.Builder
)

data class ElytraAllowArg(val entity: LivingEntity)

data class ElytraCustomArg(
    val entity: LivingEntity,
    val tickElytra: Boolean
)

data class AllowSleepingArg(
    val entity: LivingEntity,
    val pos: BlockPos
)

data class SleepingArg(
    val entity: LivingEntity,
    val pos: BlockPos
)

data class AllowBedArg(
    val entity: LivingEntity,
    val pos: BlockPos,
    val state: BlockState,
    val vanillaResult: Boolean
)

data class AllowNearbyMonstersArg(
    val entity: Player,
    val pos: BlockPos,
    val vanillaResult: Boolean
)

data class AllowResettingTimeArg(val player: Player)

data class ModifySleepingDirectionArg(
    val entity: LivingEntity,
    val pos: BlockPos,
    val direction: Direction?
)

data class AllowSettingSpawnArg(
    val entity: LivingEntity,
    val pos: BlockPos
)

data class SetBedOccupationStateArg(
    val entity: LivingEntity,
    val pos: BlockPos,
    val state: BlockState,
    val occupied: Boolean
)

data class ModifyWakeUpPositionArg(
    val entity: LivingEntity,
    val sleepingPos: BlockPos,
    val bedState: BlockState,
    val wakeUpPos: Vec3?
)

data class ItemUseOnArg(val context: UseOnContext)

data class ItemUseArg(
    val world: Level,
    val player: Player,
    val hand: InteractionHand
)

data class LootTableReplaceArg(
    val key: ResourceKey<LootTable>,
    val original: LootTable,
    val registries: HolderLookup.Provider
)

data class LootTableModifyArg(
    val key: ResourceKey<LootTable>,
    val tableBuilder: LootTable.Builder,
    val registries: HolderLookup.Provider
)

data class LootTableAllLoadArg(
    val resourceManager: ResourceManager,
    val lootDataManager: Registry<LootTable>
)

data class LootTableModifyDropsArg(
    val table: Holder<LootTable>,
    val context: LootContext,
    val drops: List<ItemStack>
)

data class PlayerAttackBlockArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand,
    val pos: BlockPos,
    val direction: Direction,
)

data class PlayerAttackEntityArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand,
    val entity: Entity,
    val hitResult: EntityHitResult?
)

data class PlayerUseBlockArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand,
    val hitResult: BlockHitResult
)

data class PlayerUseEntityArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand,
    val entity: Entity,
    val hitResult: EntityHitResult?
)

data class PlayerUseItemArg(
    val player: Player,
    val world: Level,
    val hand: InteractionHand
)

data class PlayerPickFromBlockArg(
    val player: ServerPlayer,
    val pos: BlockPos,
    val state: BlockState,
    val includeData: Boolean
)

data class PlayerPickFromEntityArg(
    val player: ServerPlayer,
    val entity: Entity,
    val includeData: Boolean
)

data class AfterKilledOtherEntityArg(
    val world: ServerLevel,
    val entity: Entity,
    val killedEntity: LivingEntity,
    val source: DamageSource
)

data class AfterEntityChangeLevelArg(
    val originalEntity: Entity,
    val destinationEntity: Entity,
    val originalLevel: ServerLevel,
    val destinationLevel: ServerLevel
)

data class AfterPlayerChangeLevelArg(
    val player: ServerPlayer,
    val originalLevel: ServerLevel,
    val destinationLevel: ServerLevel
)

data class AllowDamageArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val amount: Float
)

data class AfterDamageArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val initialDamage: Float,
    val finalDamage: Float,
    val handled: Boolean
)

data class AllowDeathArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val amount: Float
)

data class AfterDeathArg(
    val entity: LivingEntity,
    val source: DamageSource
)

data class MobConversionArg(
    val oldEntity: Mob,
    val newEntity: Mob,
    val keepEquipment: ConversionParams?
)

data class AllowChatMessageArg(
    val message: PlayerChatMessage,
    val sender: ServerPlayer,
    val params: Bound
)

data class AllowGameMessageArg(
    val server: MinecraftServer,
    val message: Component,
    val overlay: Boolean
)

data class AllowCommandMessageArg(
    val message: PlayerChatMessage,
    val source: CommandSourceStack,
    val params: Bound
)

data class ChatMessageArg(
    val message: PlayerChatMessage,
    val sender: ServerPlayer,
    val params: Bound
)

data class GameMessageArg(
    val server: MinecraftServer,
    val message: Component,
    val overlay: Boolean
)

data class CommandMessageArg(
    val message: PlayerChatMessage,
    val source: CommandSourceStack,
    val params: Bound
)

data class PlayerArg(val player: ServerPlayer)

data class ServerPlayerAfterRespawnArg(
    val oldPlayer: ServerPlayer,
    val newPlayer: ServerPlayer,
    val alive: Boolean
)

data class ServerPlayerAllowDeathArg(
    val player: ServerPlayer,
    val damageSource: DamageSource,
    val damageAmount: Float
)

data class ServerPlayerCopyArg(
    val oldPlayer: ServerPlayer,
    val newPlayer: ServerPlayer,
    val alive: Boolean
)

data class AnimalTameArg(
    val animal: Animal,
    val tamer: Player
): CancellableEventArg()

data class BabySpawnArg(
    val parentA: LivingEntity,
    val parentB: LivingEntity,
    val child: AgeableMob?
): CancellableEventArg()

data class CriticalHitArg(
    val player: Player,
    val target: Entity,
    val isVanillaCritical: Boolean
)

data class PlayerWakeUpArg(
    val player: Player,
    val wakeImmediately: Boolean,
    val updateLevelList: Boolean
)

data class EntityTeleportArg(
    val entity: Entity,
    val fromX: Double,
    val fromY: Double,
    val fromZ: Double,
    val toX: Double,
    val toY: Double,
    val toZ: Double
): CancellableEventArg()

data class EndermanAngerArg(
    val enderman: EnderMan,
    val player: Player
): CancellableEventArg()

data class ExplosionStartArg(
    val level: Level,
    val explosion: Explosion
): CancellableEventArg()

data class ExplosionDetonateArg(
    val level: Level,
    val explosion: Explosion,
    val affectedEntities: List<Entity>
)

data class ItemTossArg(
    val player: Player,
    val item: ItemEntity
)

data class PlayerDestroyItemArg(
    val player: Player,
    val item: ItemStack,
    val hand: InteractionHand?
)

data class LivingUseItemStartArg(
    val entity: LivingEntity,
    val item: ItemStack,
    val hand: InteractionHand,
    val duration: Int
): CancellableEventArg()

data class LivingUseItemTickArg(
    val entity: LivingEntity,
    val item: ItemStack,
    var duration: Int
): CancellableEventArg()

data class LivingUseItemStopArg(
    val entity: LivingEntity,
    val item: ItemStack,
    val duration: Int
): CancellableEventArg()

data class LivingUseItemFinishArg(
    val entity: LivingEntity,
    val item: ItemStack,
    val duration: Int,
    var result: ItemStack
)

data class NeoPlayerAttackEntityArg(
    val player: Player,
    val target: Entity
) : CancellableEventArg()

data class NeoPlayerInteractEntityArg(
    val player: Player,
    val entity: Entity,
    val hand: InteractionHand
) : CancellableEventArg()

data class NeoPlayerInteractBlockArg(
    val player: Player,
    val pos: BlockPos,
    val face: Direction?,
    val hand: InteractionHand
) : CancellableEventArg()

data class NeoPlayerInteractItemArg(
    val player: Player,
    val hand: InteractionHand
) : CancellableEventArg()

data class NeoPlayerLeftClickBlockArg(
    val player: Player,
    val pos: BlockPos,
    val face: Direction?
) : CancellableEventArg()

data class PlayerXpChangeArg(
    val player: Player,
    val amount: Int
): CancellableEventArg()

data class PlayerXpLevelChangeArg(
    val player: Player,
    val levels: Int
): CancellableEventArg()

data class PlayerPickupXpArg(
    val player: Player,
    val orb: ExperienceOrb
): CancellableEventArg()

data class LivingHurtArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val amount: Float
): CancellableEventArg()

data class NeoLivingDamageArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val amount: Float
)

data class NeoLivingDeathArg(
    val entity: LivingEntity,
    val source: DamageSource
)

data class LivingDropsArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val drops: List<ItemStack>
): CancellableEventArg()

data class LivingFallArg(
    val entity: LivingEntity,
    val distance: Double,
    val damageMultiplier: Float
): CancellableEventArg()

@JvmInline
value class LivingJumpArg(val entity: LivingEntity)

data class ServerChatArg(
    val player: ServerPlayer,
    val message: String,
    val component: Component
) : CancellableEventArg()

data class ShieldBlockArg(
    val entity: LivingEntity,
    val source: DamageSource,
    val blockedDamage: Float,
    val originalBlockedState: Boolean
)
