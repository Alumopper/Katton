package top.katton.api.event

import java.io.File
import net.minecraft.world.item.ItemStack
import net.minecraft.world.Container
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.TriState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.item.ItemTossEvent
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent
import top.katton.Katton
import top.katton.util.CancellableDelegateEvent
import top.katton.util.CancellableEventArg
import top.katton.util.DelegateEvent
import top.katton.util.setCancel

/**
 * Server player events and player XP events.
 * Note: Player lifecycle events are emulated for NeoForge compatibility, actual XP events available.
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
    @JvmField
    val onPlayerJoin = createUnitEvent<PlayerArg>()

    @JvmField
    val onPlayerLeave =  createUnitEvent<PlayerArg>()

    @JvmField
    val onAfterPlayerRespawn =  createUnitEvent<ServerPlayerAfterRespawnArg>()

    @JvmField
    val onPlayerCopy = createUnitEvent<ServerPlayerCopyArg>()

    // === Player XP Events ===
    val onPlayerXpChange = createCancellableUnitEvent<PlayerXpChangeArg>()

    val onPlayerXpLevelChange = createCancellableUnitEvent<PlayerXpLevelChangeArg>()

    val onPlayerPickupXp = createCancellableUnitEvent<PlayerPickupXpArg>()

    val onStartTracking = createUnitEvent<PlayerTrackingArg>()

    val onStopTracking = createUnitEvent<PlayerTrackingArg>()

    val onPlayerLoadFromFile = createUnitEvent<PlayerFileArg>()

    val onPlayerSaveToFile = createUnitEvent<PlayerFileArg>()

    val onItemToss = createUnitEvent<ItemTossArg>()

    val onItemPickupPre = createUnitEvent<PlayerItemPickupPreArg>()

    val onItemPickupPost = createUnitEvent<PlayerItemPickupPostArg>()

    val onPlayerItemCrafted = createUnitEvent<PlayerCraftedItemArg>()

    val onPlayerItemSmelted = createUnitEvent<PlayerSmeltedItemArg>()

    val onPlayerSpawnPhantoms = createUnitEvent<PlayerSpawnPhantomsArg>()

    // === Item Picking Events  ===

    @JvmField
    val onPickFromBlock = createFirstNotNullOfOrNullEvent<PlayerPickFromBlockArg, ItemStack>()

    @JvmField
    val onPickFromEntity = createFirstNotNullOfOrNullEvent<PlayerPickFromEntityArg, ItemStack>()

    data class PlayerTrackingArg(
        val player: ServerPlayer,
        val target: Entity
    )

    data class PlayerFileArg(
        val player: ServerPlayer,
        val playerDirectory: File,
        val playerUUID: String
    )

    data class PlayerItemPickupPreArg(
        val player: ServerPlayer,
        val item: ItemEntity,
        var canPickup: TriState
    )

    data class PlayerItemPickupPostArg(
        val player: ServerPlayer,
        val item: ItemEntity,
        val originalStack: ItemStack,
        val currentStack: ItemStack
    )

    data class PlayerCraftedItemArg(
        val player: ServerPlayer,
        val item: ItemStack,
        val inventory: Container
    )

    data class PlayerSmeltedItemArg(
        val player: ServerPlayer,
        val item: ItemStack,
        val amountRemoved: Int
    )

    data class PlayerSpawnPhantomsArg(
        val player: ServerPlayer,
        var phantomsToSpawn: Int,
        var result: PlayerSpawnPhantomsEvent.Result
    )

    private data class RespawnState(
        val oldPlayer: ServerPlayer,
        val alive: Boolean
    )

    private fun <T> createUnitEvent() = DelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }

    private fun <T : CancellableEventArg> createCancellableUnitEvent() = CancellableDelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }

    private fun <T, R> createFirstNotNullOfOrNullEvent() = DelegateEvent<T, R?> { events ->
        { arg -> events.firstNotNullOfOrNull { handler -> handler(arg) } }
    }
}
