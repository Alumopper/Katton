package top.katton.api.event

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents
import net.minecraft.world.item.ItemStack
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * %en
 * Server player events for Fabric platform.
 *
 * This object provides events related to server player lifecycle including
 * join/leave/respawn, XP events, and item picking events.
 *
 * %zh
 * Fabric 平台的服务器玩家事件。
 * 此对象提供与服务器玩家生命周期相关的事件，包括加入、离开、重生、经验事件和物品选取事件。
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
 * %en
 * Event triggered when a player joins the server.
 *
 * %zh
 * 在玩家加入服务器时触发。
 */
    val onPlayerJoin = createUnit<PlayerArg>()

    /**
 * %en
 * Event triggered when a player leaves the server.
 *
 * %zh
 * 在玩家离开服务器时触发。
 */
    val onPlayerLeave = createUnit<PlayerArg>()

    /**
 * %en
 * Event triggered after a player respawns.
 *
 * %zh
 * 在玩家重生之后触发。
 */
    val onAfterPlayerRespawn = createUnit<ServerPlayerAfterRespawnArg>()

    /**
 * %en
 * Event triggered when player data is copied (e.g., on respawn or dimension change).
 *
 * %zh
 * 在复制玩家数据 (例如 on 重生 或 维度切换)时触发。
 */
    val onPlayerCopy = createUnit<ServerPlayerCopyArg>()

    // === Player XP Events ===

    /**
 * %en
 * Event triggered when a player's XP changes.
 * Can be cancelled to prevent the change.
 *
 * %zh
 * 在玩家经验变化时触发。
 * 可以取消，用来阻止对应的默认行为。
 */
    @JvmField
    val onPlayerXpChange = createCancellableUnit<PlayerXpChangeArg>()

    /**
 * %en
 * Event triggered when a player's XP level changes.
 * Can be cancelled to prevent the change.
 *
 * %zh
 * 在玩家经验等级变化时触发。
 * 可以取消，用来阻止对应的默认行为。
 */
    @JvmField
    val onPlayerXpLevelChange = createCancellableUnit<PlayerXpLevelChangeArg>()

    /**
 * %en
 * Event triggered when a player picks up an XP orb.
 * Can be cancelled to prevent pickup.
 *
 * %zh
 * 在玩家拾取经验球时触发。
 * 可以取消，用来阻止对应的默认行为。
 */
    @JvmField
    val onPlayerPickupXp = createCancellableUnit<PlayerPickupXpArg>()

    // === Item Picking Events ===

    /**
 * %en
 * Event triggered when a player picks an item from a block (middle-click).
 *
 * %zh
 * 在玩家用鼠标中键从方块选取物品时触发。
 * @return
 * %en ItemStack to be picked, or null for default behavior.
 * %zh 要拾取的 ItemStack，返回 null 则使用默认行为。
 */
    val onPickFromBlock = createFirstNotNullOfOrNull<PlayerPickFromBlockArg, ItemStack>()

    /**
 * %en
 * Event triggered when a player picks an item from an entity (middle-click).
 *
 * %zh
 * 在玩家用鼠标中键从实体选取物品时触发。
 * @return
 * %en ItemStack to be picked, or null for default behavior.
 * %zh 要拾取的 ItemStack，返回 null 则使用默认行为。
 */
    val onPickFromEntity = createFirstNotNullOfOrNull<PlayerPickFromEntityArg, ItemStack>()
}
