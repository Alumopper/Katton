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
 * Server player events for NeoForge platform.
 *
 * This object provides events related to server player lifecycle including
 * join/leave/respawn, XP events, item pickup/toss, crafting, and more.
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerPlayerEvent {

    private val respawnStateByPlayerId = mutableMapOf<java.util.UUID, RespawnState>()

    @SubscribeEvent
    private fun handlePlayerJoin(e: PlayerEvent.PlayerLoggedInEvent) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerJoin(PlayerArg(player))
    }

    @SubscribeEvent
    private fun handlePlayerLeave(e: PlayerEvent.PlayerLoggedOutEvent) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerLeave(PlayerArg(player))
    }

    @SubscribeEvent
    private fun handlePlayerCopy(e: PlayerEvent.Clone) {
        val oldPlayer = e.original as? ServerPlayer ?: return
        val newPlayer = e.entity as? ServerPlayer ?: return
        val alive = !e.isWasDeath
        respawnStateByPlayerId[newPlayer.uuid] = RespawnState(oldPlayer, alive)
        onPlayerCopy(ServerPlayerCopyArg(oldPlayer, newPlayer, alive))
    }

    @SubscribeEvent
    private fun handleAfterPlayerRespawn(e: PlayerEvent.PlayerRespawnEvent) {
        val newPlayer = e.entity as? ServerPlayer ?: return
        val state = respawnStateByPlayerId.remove(newPlayer.uuid)
        val oldPlayer = state?.oldPlayer ?: newPlayer
        val alive = state?.alive ?: e.isEndConquered
        onAfterPlayerRespawn(ServerPlayerAfterRespawnArg(oldPlayer, newPlayer, alive))
    }

    @SubscribeEvent
    private fun handlePlayerXpChange(e: PlayerXpEvent.XpChange) {
        onPlayerXpChange(
            PlayerXpChangeArg(e.entity, e.amount)
        )
        setCancel(onPlayerXpChange, e)
    }

    @SubscribeEvent
    private fun handlePlayerXpLevelChange(e: PlayerXpEvent.LevelChange) {
        onPlayerXpLevelChange(
            PlayerXpLevelChangeArg(e.entity, e.levels)
        )
        setCancel(onPlayerXpLevelChange, e)
    }

    @SubscribeEvent
    private fun handlePlayerPickupXp(e: PlayerXpEvent.PickupXp) {
        onPlayerPickupXp(
            PlayerPickupXpArg(e.entity, e.orb)
        )
        setCancel(onPlayerPickupXp, e)
    }

    @SubscribeEvent
    private fun handleStartTracking(e: PlayerEvent.StartTracking) {
        val player = e.entity as? ServerPlayer ?: return
        onStartTracking(PlayerTrackingArg(player, e.target))
    }

    @SubscribeEvent
    private fun handleStopTracking(e: PlayerEvent.StopTracking) {
        val player = e.entity as? ServerPlayer ?: return
        onStopTracking(PlayerTrackingArg(player, e.target))
    }

    @SubscribeEvent
    private fun handlePlayerLoadFromFile(e: PlayerEvent.LoadFromFile) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerLoadFromFile(PlayerFileArg(player, e.playerDirectory, e.playerUUID))
    }

    @SubscribeEvent
    private fun handlePlayerSaveToFile(e: PlayerEvent.SaveToFile) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerSaveToFile(PlayerFileArg(player, e.playerDirectory, e.playerUUID))
    }

    @SubscribeEvent
    private fun handleItemToss(e: ItemTossEvent) {
        val player = e.player as? ServerPlayer ?: return
        onItemToss(ItemTossArg(player, e.entity))
    }

    @SubscribeEvent
    private fun handleItemPickupPre(e: ItemEntityPickupEvent.Pre) {
        val player = e.player as? ServerPlayer ?: return
        val arg = PlayerItemPickupPreArg(player, e.itemEntity, e.canPickup())
        onItemPickupPre(arg)
        e.setCanPickup(arg.canPickup)
    }

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

    @SubscribeEvent
    private fun handlePlayerItemCrafted(e: PlayerEvent.ItemCraftedEvent) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerItemCrafted(PlayerCraftedItemArg(player, e.crafting, e.inventory))
    }

    @SubscribeEvent
    private fun handlePlayerItemSmelted(e: PlayerEvent.ItemSmeltedEvent) {
        val player = e.entity as? ServerPlayer ?: return
        onPlayerItemSmelted(PlayerSmeltedItemArg(player, e.smelting, e.amountRemoved))
    }

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
     * Event triggered when a player joins the server.
     */
    @JvmField
    val onPlayerJoin = createUnit<PlayerArg>()

    /**
     * Event triggered when a player leaves the server.
     */
    @JvmField
    val onPlayerLeave =  createUnit<PlayerArg>()

    /**
     * Event triggered after a player respawns.
     */
    @JvmField
    val onAfterPlayerRespawn =  createUnit<ServerPlayerAfterRespawnArg>()

    /**
     * Event triggered when player data is copied (e.g., on respawn or dimension change).
     */
    @JvmField
    val onPlayerCopy = createUnit<ServerPlayerCopyArg>()

    // === Player XP Events ===

    /**
     * Event triggered when a player's XP changes.
     * Can be cancelled to prevent the change.
     */
    val onPlayerXpChange = createCancellableUnit<PlayerXpChangeArg>()

    /**
     * Event triggered when a player's XP level changes.
     * Can be cancelled to prevent the change.
     */
    val onPlayerXpLevelChange = createCancellableUnit<PlayerXpLevelChangeArg>()

    /**
     * Event triggered when a player picks up an XP orb.
     * Can be cancelled to prevent pickup.
     */
    val onPlayerPickupXp = createCancellableUnit<PlayerPickupXpArg>()

    /**
     * Event triggered when a player starts tracking an entity.
     */
    val onStartTracking = createUnit<PlayerTrackingArg>()

    /**
     * Event triggered when a player stops tracking an entity.
     */
    val onStopTracking = createUnit<PlayerTrackingArg>()

    /**
     * Event triggered when a player data is loaded from file.
     */
    val onPlayerLoadFromFile = createUnit<PlayerFileArg>()

    /**
     * Event triggered when a player data is saved to file.
     */
    val onPlayerSaveToFile = createUnit<PlayerFileArg>()

    /**
     * Event triggered when a player tosses an item.
     */
    val onItemToss = createUnit<ItemTossArg>()

    /**
     * Event triggered before a player picks up an item.
     * Can modify whether the pickup is allowed.
     */
    val onItemPickupPre = createUnit<PlayerItemPickupPreArg>()

    /**
     * Event triggered after a player picks up an item.
     */
    val onItemPickupPost = createUnit<PlayerItemPickupPostArg>()

    /**
     * Event triggered when a player crafts an item.
     */
    val onPlayerItemCrafted = createUnit<PlayerCraftedItemArg>()

    /**
     * Event triggered when a player smelts an item.
     */
    val onPlayerItemSmelted = createUnit<PlayerSmeltedItemArg>()

    /**
     * Event triggered when phantoms are about to spawn for a player.
     * Can modify the number of phantoms and the spawn result.
     */
    val onPlayerSpawnPhantoms = createUnit<PlayerSpawnPhantomsArg>()

    // === Item Picking Events  ===

    /**
     * Event triggered when a player picks an item from a block (middle-click).
     *
     * @return The ItemStack to be picked, or null for default behavior.
     * Note: This is a placeholder for NeoForge compatibility.
     */
    @JvmField
    val onPickFromBlock = createFirstNotNullOfOrNull<PlayerPickFromBlockArg, ItemStack>()

    /**
     * Event triggered when a player picks an item from an entity (middle-click).
     *
     * @return The ItemStack to be picked, or null for default behavior.
     * Note: This is a placeholder for NeoForge compatibility.
     */
    @JvmField
    val onPickFromEntity = createFirstNotNullOfOrNull<PlayerPickFromEntityArg, ItemStack>()

    /**
     * Argument class for player tracking events.
     *
     * @property player The player tracking the target
     * @property target The entity being tracked
     */
    data class PlayerTrackingArg(
        val player: ServerPlayer,
        val target: Entity
    )

    /**
     * Argument class for player file operations.
     *
     * @property player The player being loaded/saved
     * @property playerDirectory The directory containing player data
     * @property playerUUID The UUID of the player
     */
    data class PlayerFileArg(
        val player: ServerPlayer,
        val playerDirectory: File,
        val playerUUID: String
    )

    /**
     * Argument class for item pickup pre events.
     *
     * @property player The player picking up the item
     * @property item The item entity being picked up
     * @property canPickup Whether the pickup is allowed (modifiable)
     */
    data class PlayerItemPickupPreArg(
        val player: ServerPlayer,
        val item: ItemEntity,
        var canPickup: TriState
    )

    /**
     * Argument class for item pickup post events.
     *
     * @property player The player who picked up the item
     * @property item The item entity that was picked up
     * @property originalStack The original item stack
     * @property currentStack The current item stack after pickup
     */
    data class PlayerItemPickupPostArg(
        val player: ServerPlayer,
        val item: ItemEntity,
        val originalStack: ItemStack,
        val currentStack: ItemStack
    )

    /**
     * Argument class for player crafted item events.
     *
     * @property player The player who crafted the item
     * @property item The crafted item stack
     * @property inventory The inventory where the item was crafted
     */
    data class PlayerCraftedItemArg(
        val player: ServerPlayer,
        val item: ItemStack,
        val inventory: Container
    )

    /**
     * Argument class for player smelted item events.
     *
     * @property player The player who smelted the item
     * @property item The smelted item stack
     * @property amountRemoved The amount removed from the input
     */
    data class PlayerSmeltedItemArg(
        val player: ServerPlayer,
        val item: ItemStack,
        val amountRemoved: Int
    )

    /**
     * Argument class for phantom spawn events.
     *
     * @property player The player for whom phantoms are spawning
     * @property phantomsToSpawn The number of phantoms to spawn (modifiable)
     * @property result The spawn result (modifiable)
     */
    data class PlayerSpawnPhantomsArg(
        val player: ServerPlayer,
        var phantomsToSpawn: Int,
        var result: PlayerSpawnPhantomsEvent.Result
    )

    /**
     * Internal data class to track respawn state.
     *
     * @property oldPlayer The player before respawn
     * @property alive Whether the respawn was due to death or dimension change
     */
    private data class RespawnState(
        val oldPlayer: ServerPlayer,
        val alive: Boolean
    )
}
