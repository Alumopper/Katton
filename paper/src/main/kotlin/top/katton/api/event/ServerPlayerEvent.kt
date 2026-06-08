package top.katton.api.event

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import io.papermc.paper.event.player.PlayerPickBlockEvent
import io.papermc.paper.event.player.PlayerPickEntityEvent
import net.minecraft.world.item.ItemStack
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLevelChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * %en
 * Server player events for Paper (Bukkit) platform.
 *
 * This object provides events related to server player lifecycle including
 * join/leave/respawn, XP events, item picking events, and Paper-specific
 * events such as jump and projectile launch.
 *
 * %zh
 * Paper (Bukkit) 平台的服务端玩家事件。
 * 此对象提供与服务端玩家生命周期相关的事件，包括加入、离开、重生、经验变化、物品选取事件，以及跳跃和投射物发射等 Paper 特有事件。
 */
@Suppress("unused")
object ServerPlayerEvent {

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
    val onPlayerLeave = createUnit<PlayerArg>()

    /**
     * %en
     * Event triggered after a player respawns.
     *
     * %zh
     * 当玩家重生之后触发。
     */
    @JvmField
    val onAfterPlayerRespawn = createUnit<ServerPlayerAfterRespawnArg>()

    /**
     * %en
     * Event triggered when player data is copied (e.g., on respawn or dimension change).
     *
     * %zh
     * 当复制玩家数据时触发（例如重生或维度切换）。
     */
    @JvmField
    val onPlayerCopy = createUnit<ServerPlayerCopyArg>()

    /**
     * %en
     * Event triggered when a player's XP changes.
     * Can be cancelled to prevent the change.
     *
     * %zh
     * 当玩家经验变化时触发。
     * 可取消以阻止这次变化。
     */
    @JvmField
    val onPlayerXpChange = createCancellableUnit<PlayerXpChangeArg>()

    /**
     * %en
     * Event triggered when a player's XP level changes.
     * Can be cancelled to prevent the change.
     *
     * %zh
     * 当玩家经验等级变化时触发。
     * 可取消以阻止这次变化。
     */
    @JvmField
    val onPlayerXpLevelChange = createCancellableUnit<PlayerXpLevelChangeArg>()

    /**
     * %en
     * Event triggered when a player picks up experience orbs.
     * Can be cancelled to prevent the pickup.
     *
     * %zh
     * 当玩家拾取经验球时触发。
     * 可取消以阻止拾取。
     */
    @JvmField
    val onPlayerPickupXp = createCancellableUnit<PlayerPickupXpArg>()

    /**
     * %en
     * Event triggered when a player picks an item from a block (middle-click).
     *
     * %zh
     * 当玩家用鼠标中键从方块选取物品时触发。
     */
    @JvmField
    val onPickFromBlock = createUnit<PlayerPickFromBlockArg>()

    /**
     * %en
     * Event triggered when a player picks an item from an entity (middle-click).
     *
     * %zh
     * 当玩家用鼠标中键从实体选取物品时触发。
     */
    @JvmField
    val onPickFromEntity = createUnit<PlayerPickFromEntityArg>()

    /**
     * %en
     * Event triggered when a player jumps (Paper-specific event).
     *
     * %zh
     * 当玩家跳跃时触发（Paper 特有事件）。
     */
    @JvmField
    val onPlayerJump = createUnit<Any>() // PlayerJumpEvent (Paper-specific)

    /**
     * %en
     * Event triggered when a player launches a projectile (Paper-specific event).
     *
     * %zh
     * 当玩家发射投射物时触发（Paper 特有事件）。
     */
    @JvmField
    val onLaunchProjectile = createUnit<Any>() // PlayerLaunchProjectileEvent (Paper-specific, cancellable via event)

    /**
     * %en
     * Initializes and registers all Bukkit event listeners for this event object.
     *
     * %zh
     * 初始化并注册此事件对象的所有 Bukkit 监听器。
     * @param plugin
     * %en The Paper plugin instance used to register listeners.
     * %zh 用于注册监听器的 Paper 插件实例。
     */
    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {

            @EventHandler
            fun onJoin(event: PlayerJoinEvent) {
                onPlayerJoin(PlayerArg(PaperNmsBridge.toNmsPlayer(event.player)))
            }

            @EventHandler
            fun onQuit(event: PlayerQuitEvent) {
                onPlayerLeave(PlayerArg(PaperNmsBridge.toNmsPlayer(event.player)))
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onRespawn(event: PlayerPostRespawnEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                onAfterPlayerRespawn(ServerPlayerAfterRespawnArg(player, player, false))
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onPlayerCopy(event: PlayerRespawnEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                onPlayerCopy(ServerPlayerCopyArg(player, player, true))
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onExpChange(event: PlayerExpChangeEvent) {
                val arg = PlayerXpChangeArg(PaperNmsBridge.toNmsPlayer(event.player), event.amount)
                onPlayerXpChange(arg)
                if (arg.isCancelled()) {
                    event.amount = 0
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onLevelChange(event: PlayerLevelChangeEvent) {
                val arg = PlayerXpLevelChangeArg(
                    PaperNmsBridge.toNmsPlayer(event.player),
                    event.newLevel - event.oldLevel
                )
                onPlayerXpLevelChange(arg)
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onPickupXp(event: PlayerPickupExperienceEvent) {
                val arg = PlayerPickupXpArg(
                    PaperNmsBridge.toNmsPlayer(event.player),
                    PaperNmsBridge.toNmsExpOrb(event.experienceOrb)
                )
                onPlayerPickupXp(arg)
                if (arg.isCancelled()) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onPickBlock(event: PlayerPickBlockEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val pos = PaperNmsBridge.toNmsBlockPos(event.block.location)
                val state = PaperNmsBridge.toNmsWorld(event.player.world).getBlockState(pos)
                onPickFromBlock(
                    PlayerPickFromBlockArg(player, pos, state, event.isIncludeData)
                )
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onPickEntity(event: PlayerPickEntityEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val entity = PaperNmsBridge.toNmsEntity(event.entity)
                onPickFromEntity(
                    PlayerPickFromEntityArg(player, entity, event.isIncludeData)
                )
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onPlayerJump(event: PlayerJumpEvent) {
                this@ServerPlayerEvent.onPlayerJump(event)
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onLaunchProjectile(event: PlayerLaunchProjectileEvent) {
                this@ServerPlayerEvent.onLaunchProjectile(event)
            }

        }, plugin)
    }
}
