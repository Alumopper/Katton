package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents
import net.minecraft.world.item.ItemStack
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * ServerPlayer specific events (join/leave/respawn/copy/allow death legacy).
 * Also includes player XP events and item picking events.
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
    val onPlayerJoin = createUnit<PlayerArg>()

    val onPlayerLeave = createUnit<PlayerArg>()

    val onAfterPlayerRespawn = createUnit<ServerPlayerAfterRespawnArg>()

    val onPlayerCopy = createUnit<ServerPlayerCopyArg>()

    // === Player XP Events ===
    @JvmField
    val onPlayerXpChange = createCancellableUnit<PlayerXpChangeArg>()

    @JvmField
    val onPlayerXpLevelChange = createCancellableUnit<PlayerXpLevelChangeArg>()

    @JvmField
    val onPlayerPickupXp = createCancellableUnit<PlayerPickupXpArg>()

    // === Item Picking Events ===
    val onPickFromBlock = createFirstNotNullOfOrNull<PlayerPickFromBlockArg, ItemStack>()

    val onPickFromEntity = createFirstNotNullOfOrNull<PlayerPickFromEntityArg, ItemStack>()
}