package top.katton.api.event

import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.TriState
import net.minecraft.world.Container
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.item.ItemTossEvent
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent
import top.katton.Katton
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit
import top.katton.util.setCancel
import java.io.File

/**
 * %en
 * Server player events for NeoForge platform.
 *
 * This object provides events related to server player lifecycle including
 * join/leave/respawn, XP events, item pickup/toss, crafting, and more.
 *
 * %zh
 * NeoForge 平台的服务端玩家事件。
 * 此对象提供与服务端玩家生命周期相关的事件，包括加入/离开/重生、经验值事件、物品拾取/投掷、合成等。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerPlayerEvent {

    private val respawnStateByPlayerId = mutableMapOf<java.util.UUID, RespawnState>()

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerJoin(e: PlayerEvent.PlayerLoggedInEvent) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerJoin(PlayerArg(player))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerLeave(e: PlayerEvent.PlayerLoggedOutEvent) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerLeave(PlayerArg(player))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerCopy(e: PlayerEvent.Clone) {
        val oldPlayer = e.original as? ServerPlayer ?: return
        val newPlayer = e.entity as? ServerPlayer ?: return
        val alive = !e.isWasDeath
        respawnStateByPlayerId[newPlayer.uuid] = RespawnState(oldPlayer, alive)
        onPlayerCopy(ServerPlayerCopyArg(oldPlayer, newPlayer, alive))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleAfterPlayerRespawn(e: PlayerEvent.PlayerRespawnEvent) {
        val newPlayer = e.entity as? ServerPlayer ?: return
        val state = respawnStateByPlayerId.remove(newPlayer.uuid)
        val oldPlayer = state?.oldPlayer ?: newPlayer
        val alive = state?.alive ?: e.isEndConquered
        onAfterPlayerRespawn(ServerPlayerAfterRespawnArg(oldPlayer, newPlayer, alive))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerXpChange(e: PlayerXpEvent.XpChange) {
        onPlayerXpChange(
            PlayerXpChangeArg(e.entity, e.amount)
        )
        setCancel(onPlayerXpChange, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerXpLevelChange(e: PlayerXpEvent.LevelChange) {
        onPlayerXpLevelChange(
            PlayerXpLevelChangeArg(e.entity, e.levels)
        )
        setCancel(onPlayerXpLevelChange, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerPickupXp(e: PlayerXpEvent.PickupXp) {
        onPlayerPickupXp(
            PlayerPickupXpArg(e.entity, e.orb)
        )
        setCancel(onPlayerPickupXp, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleStartTracking(e: PlayerEvent.StartTracking) {
        val player = e.entity as? ServerPlayer ?: return
        onStartTracking(PlayerTrackingArg(player, e.target))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleStopTracking(e: PlayerEvent.StopTracking) {
        val player = e.entity as? ServerPlayer ?: return
        onStopTracking(PlayerTrackingArg(player, e.target))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerLoadFromFile(e: PlayerEvent.LoadFromFile) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerLoadFromFile(PlayerFileArg(player, e.playerDirectory, e.playerUUID))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerSaveToFile(e: PlayerEvent.SaveToFile) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerSaveToFile(PlayerFileArg(player, e.playerDirectory, e.playerUUID))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleItemToss(e: ItemTossEvent) {
        val player = e.player as? ServerPlayer ?: return
        onItemToss(ItemTossArg(player, e.entity))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleItemPickupPre(e: ItemEntityPickupEvent.Pre) {
        val player = e.player as? ServerPlayer ?: return
        val arg = PlayerItemPickupPreArg(player, e.itemEntity, e.canPickup())
        onItemPickupPre(arg)
        e.setCanPickup(arg.canPickup)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleItemPickupPost(e: ItemEntityPickupEvent.Post) {
        val player = e.player as? ServerPlayer ?: return
        onItemPickupPost(
            PlayerItemPickupPostArg(
                player,
                e.itemEntity,
                e.originalStack,
                e.currentStack
            )
        )
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerItemCrafted(e: PlayerEvent.ItemCraftedEvent) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerItemCrafted(PlayerCraftedItemArg(player, e.crafting, e.inventory))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerItemSmelted(e: PlayerEvent.ItemSmeltedEvent) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerItemSmelted(PlayerSmeltedItemArg(player, e.smelting, e.amountRemoved))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handlePlayerSpawnPhantoms(e: PlayerSpawnPhantomsEvent) {
        val player = e.entity as? ServerPlayer ?: return
        val arg = PlayerSpawnPhantomsArg(player, e.phantomsToSpawn, e.result)
        onPlayerSpawnPhantoms(arg)
        e.setPhantomsToSpawn(arg.phantomsToSpawn)
        e.setResult(arg.result)
    }

    // === Player Lifecycle Events ===

    /**
     * %en
     * Event triggered when a player joins the server.
     *
     * %zh
     * 当玩家加入服务器时触发。
     */
    @JvmField
    val onPlayerJoin = createUnit<PlayerArg>()

    /**
     * %en
     * Event triggered when a player leaves the server.
     *
     * %zh
     * 当玩家离开服务器时触发。
     */
    @JvmField
    val onPlayerLeave =  createUnit<PlayerArg>()

    /**
     * %en
     * Event triggered after a player respawns.
     *
     * %zh
     * 当玩家重生后触发。
     */
    @JvmField
    val onAfterPlayerRespawn =  createUnit<ServerPlayerAfterRespawnArg>()

    /**
     * %en
     * Event triggered when player data is copied (e.g., on respawn or dimension change).
     *
     * %zh
     * 当复制玩家数据时触发，例如在重生或维度切换时。
     */
    @JvmField
    val onPlayerCopy = createUnit<ServerPlayerCopyArg>()

    // === Player XP Events ===

    /**
     * %en
     * Event triggered when a player's XP changes.
     * Can be cancelled to prevent the change.
     *
     * %zh
     * 当玩家经验变化时触发。
     * 可取消以阻止变化。
     */
    val onPlayerXpChange = createCancellableUnit<PlayerXpChangeArg>()

    /**
     * %en
     * Event triggered when a player's XP level changes.
     * Can be cancelled to prevent the change.
     *
     * %zh
     * 当玩家经验等级变化时触发。
     * 可取消以阻止变化。
     */
    val onPlayerXpLevelChange = createCancellableUnit<PlayerXpLevelChangeArg>()

    /**
     * %en
     * Event triggered when a player picks up an XP orb.
     * Can be cancelled to prevent pickup.
     *
     * %zh
     * 当玩家拾取经验球时触发。
     * 可取消以阻止拾取。
     */
    val onPlayerPickupXp = createCancellableUnit<PlayerPickupXpArg>()

    /**
     * %en
     * Event triggered when a player starts tracking an entity.
     *
     * %zh
     * 当玩家开始跟踪某个实体时触发。
     */
    val onStartTracking = createUnit<PlayerTrackingArg>()

    /**
     * %en
     * Event triggered when a player stops tracking an entity.
     *
     * %zh
     * 当玩家停止跟踪某个实体时触发。
     */
    val onStopTracking = createUnit<PlayerTrackingArg>()

    /**
     * %en
     * Event triggered when a player data is loaded from file.
     *
     * %zh
     * 当玩家数据从文件加载时触发。
     */
    val onPlayerLoadFromFile = createUnit<PlayerFileArg>()

    /**
     * %en
     * Event triggered when a player data is saved to file.
     *
     * %zh
     * 当玩家数据保存到文件时触发。
     */
    val onPlayerSaveToFile = createUnit<PlayerFileArg>()

    /**
     * %en
     * Event triggered when a player tosses an item.
     *
     * %zh
     * 当玩家投掷物品时触发。
     */
    val onItemToss = createUnit<ItemTossArg>()

    /**
     * %en
     * Event triggered before a player picks up an item.
     * Can modify whether the pickup is allowed.
     *
     * %zh
     * 当玩家拾取物品前触发。
     * 可修改是否允许拾取。
     */
    val onItemPickupPre = createUnit<PlayerItemPickupPreArg>()

    /**
     * %en
     * Event triggered after a player picks up an item.
     *
     * %zh
     * 当玩家拾取物品后触发。
     */
    val onItemPickupPost = createUnit<PlayerItemPickupPostArg>()

    /**
     * %en
     * Event triggered when a player crafts an item.
     *
     * %zh
     * 当玩家合成物品时触发。
     */
    val onPlayerItemCrafted = createUnit<PlayerCraftedItemArg>()

    /**
     * %en
     * Event triggered when a player smelts an item.
     *
     * %zh
     * 当玩家熔炼物品时触发。
     */
    val onPlayerItemSmelted = createUnit<PlayerSmeltedItemArg>()

    /**
     * %en
     * Event triggered when phantoms are about to spawn for a player.
     * Can modify the number of phantoms and the spawn result.
     *
     * %zh
     * 当幻翼即将为某位玩家生成时触发。
     * 可修改幻翼数量和生成结果。
     */
    val onPlayerSpawnPhantoms = createUnit<PlayerSpawnPhantomsArg>()

    // === Item Picking Events  ===

    /**
     * %en
     * Event triggered when a player picks an item from a block (middle-click).
     *
     * %zh
     * 当玩家中键从方块中获取物品时触发。
     * @return
     * %en ItemStack to be picked, or null for default behavior.
     * %zh 要获取的 ItemStack，或返回 null 使用默认行为。
     * %en
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onPickFromBlock = createFirstNotNullOfOrNull<PlayerPickFromBlockArg, ItemStack>()

    /**
     * %en
     * Event triggered when a player picks an item from an entity (middle-click).
     *
     * %zh
     * 当玩家中键从实体中获取物品时触发。
     * @return
     * %en ItemStack to be picked, or null for default behavior.
     * %zh 要获取的 ItemStack，或返回 null 使用默认行为。
     * %en
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onPickFromEntity = createFirstNotNullOfOrNull<PlayerPickFromEntityArg, ItemStack>()

    /**
     * %en
     * Argument class for player tracking events.
     *
     * %zh
     * 玩家跟踪事件的参数类。
     * @property player
     * %en The player tracking the target
     * %zh 正在跟踪目标的玩家。
     * @property target
     * %en The entity being tracked
     * %zh 被跟踪的实体。
     */
    data class PlayerTrackingArg(
        val player: ServerPlayer,
        val target: Entity
    )

    /**
     * %en
     * Argument class for player file operations.
     *
     * %zh
     * 玩家文件操作的参数类。
     * @property player
     * %en The player being loaded/saved
     * %zh 正在加载/保存的玩家。
     * @property playerDirectory
     * %en The directory containing player data
     * %zh 包含玩家数据的目录。
     * @property playerUUID
     * %en The UUID of the player
     * %zh 玩家 UUID。
     */
    data class PlayerFileArg(
        val player: ServerPlayer,
        val playerDirectory: File,
        val playerUUID: String
    )

    /**
     * %en
     * Argument class for item pickup pre events.
     *
     * %zh
     * 物品拾取前事件的参数类。
     * @property player
     * %en The player picking up the item
     * %zh 正在拾取物品的玩家。
     * @property item
     * %en The item entity being picked up
     * %zh 正在被拾取的物品实体。
     * @property canPickup
     * %en Whether the pickup is allowed (modifiable)
     * %zh 是否允许拾取（可修改）。
     */
    data class PlayerItemPickupPreArg(
        val player: ServerPlayer,
        val item: ItemEntity,
        var canPickup: TriState
    )

    /**
     * %en
     * Argument class for item pickup post events.
     *
     * %zh
     * 物品拾取后事件的参数类。
     * @property player
     * %en The player who picked up the item
     * %zh 已拾取物品的玩家。
     * @property item
     * %en The item entity that was picked up
     * %zh 已被拾取的物品实体。
     * @property originalStack
     * %en The original item stack
     * %zh 原始物品堆栈。
     * @property currentStack
     * %en The current item stack after pickup
     * %zh 拾取后的当前物品堆栈。
     */
    data class PlayerItemPickupPostArg(
        val player: ServerPlayer,
        val item: ItemEntity,
        val originalStack: ItemStack,
        val currentStack: ItemStack
    )

    /**
     * %en
     * Argument class for player crafted item events.
     *
     * %zh
     * 玩家合成物品事件的参数类。
     * @property player
     * %en The player who crafted the item
     * %zh 合成该物品的玩家。
     * @property item
     * %en The crafted item stack
     * %zh 合成得到的物品堆栈。
     * @property inventory
     * %en The inventory where the item was crafted
     * %zh 发生合成的容器。
     */
    data class PlayerCraftedItemArg(
        val player: ServerPlayer,
        val item: ItemStack,
        val inventory: Container
    )

    /**
     * %en
     * Argument class for player smelted item events.
     *
     * %zh
     * 玩家熔炼物品事件的参数类。
     * @property player
     * %en The player who smelted the item
     * %zh 熔炼该物品的玩家。
     * @property item
     * %en The smelted item stack
     * %zh 熔炼得到的物品堆栈。
     * @property amountRemoved
     * %en The amount removed from the input
     * %zh 从输入中移除的数量。
     */
    data class PlayerSmeltedItemArg(
        val player: ServerPlayer,
        val item: ItemStack,
        val amountRemoved: Int
    )

    /**
     * %en
     * Argument class for phantom spawn events.
     *
     * %zh
     * 幻翼生成事件的参数类。
     * @property player
     * %en The player for whom phantoms are spawning
     * %zh 正在为其生成幻翼的玩家。
     * @property phantomsToSpawn
     * %en The number of phantoms to spawn (modifiable)
     * %zh 要生成的幻翼数量（可修改）。
     * @property result
     * %en The spawn result (modifiable)
     * %zh 生成结果（可修改）。
     */
    data class PlayerSpawnPhantomsArg(
        val player: ServerPlayer,
        var phantomsToSpawn: Int,
        var result: PlayerSpawnPhantomsEvent.Result
    )

    /**
     * %en
     * Internal data class to track respawn state.
     *
     * %zh
     * 用于跟踪重生状态的内部数据类。
     * @property oldPlayer
     * %en The player before respawn
     * %zh 重生前的玩家。
     * @property alive
     * %en Whether the respawn was due to death or dimension change
     * %zh 本次重生是否因死亡或维度切换而发生。
     */
    private data class RespawnState(
        val oldPlayer: ServerPlayer,
        val alive: Boolean
    )
}
