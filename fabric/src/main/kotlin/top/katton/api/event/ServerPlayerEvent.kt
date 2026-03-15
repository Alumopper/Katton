package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents
import net.minecraft.world.item.ItemStack
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * Server player events for Fabric platform.
 *
 * This object provides events related to server player lifecycle including
 * join/leave/respawn, XP events, and item picking events.
 */
@Suppress("unused")
object ServerPlayerEvent {

    fun initialize() {
        ServerPlayerEvents.JOIN.register { onPlayerJoin(PlayerArg(it)) }
        ServerPlayerEvents.LEAVE.register { onPlayerLeave(PlayerArg(it)) }
        ServerPlayerEvents.AFTER_RESPAWN.register { a, b, c -> onAfterPlayerRespawn(ServerPlayerAfterRespawnArg(a, b, c)) }
        ServerPlayerEvents.COPY_FROM.register { a, b, c -> onPlayerCopy(ServerPlayerCopyArg(a, b, c)) }

        PlayerPickItemEvents.BLOCK.register { a, b, c1, d -> onPickFromBlock(PlayerPickFromBlockArg(a, b, c1, d)).getOrNull() }
        PlayerPickItemEvents.ENTITY.register { a, b, c1 -> onPickFromEntity(PlayerPickFromEntityArg(a, b, c1)).getOrNull() }
    }

    // === Player Lifecycle Events ===

    /**
     * Event triggered when a player joins the server.
     */
    val onPlayerJoin = createUnit<PlayerArg>()

    /**
     * Event triggered when a player leaves the server.
     */
    val onPlayerLeave = createUnit<PlayerArg>()

    /**
     * Event triggered after a player respawns.
     */
    val onAfterPlayerRespawn = createUnit<ServerPlayerAfterRespawnArg>()

    /**
     * Event triggered when player data is copied (e.g., on respawn or dimension change).
     */
    val onPlayerCopy = createUnit<ServerPlayerCopyArg>()

    // === Player XP Events ===

    /**
     * Event triggered when a player's XP changes.
     * Can be cancelled to prevent the change.
     */
    @JvmField
    val onPlayerXpChange = createCancellableUnit<PlayerXpChangeArg>()

    /**
     * Event triggered when a player's XP level changes.
     * Can be cancelled to prevent the change.
     */
    @JvmField
    val onPlayerXpLevelChange = createCancellableUnit<PlayerXpLevelChangeArg>()

    /**
     * Event triggered when a player picks up an XP orb.
     * Can be cancelled to prevent pickup.
     */
    @JvmField
    val onPlayerPickupXp = createCancellableUnit<PlayerPickupXpArg>()

    // === Item Picking Events ===

    /**
     * Event triggered when a player picks an item from a block (middle-click).
     *
     * @return The ItemStack to be picked, or null for default behavior.
     */
    val onPickFromBlock = createFirstNotNullOfOrNull<PlayerPickFromBlockArg, ItemStack>()

    /**
     * Event triggered when a player picks an item from an entity (middle-click).
     *
     * @return The ItemStack to be picked, or null for default behavior.
     */
    val onPickFromEntity = createFirstNotNullOfOrNull<PlayerPickFromEntityArg, ItemStack>()
}
