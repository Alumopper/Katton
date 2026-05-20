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
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createUnit

/**
 * Server player events for Paper (Bukkit) platform.
 *
 * This object provides events related to server player lifecycle including
 * join/leave/respawn, XP events, item picking events, and Paper-specific
 * events such as jump and projectile launch.
 */
@Suppress("unused")
object ServerPlayerEvent {

    /**
     * Event triggered when a player joins the server.
     */
    @JvmField
    val onPlayerJoin = createUnit<PlayerArg>()

    /**
     * Event triggered when a player leaves the server.
     */
    @JvmField
    val onPlayerLeave = createUnit<PlayerArg>()

    /**
     * Event triggered after a player respawns.
     */
    @JvmField
    val onAfterPlayerRespawn = createUnit<ServerPlayerAfterRespawnArg>()

    /**
     * Event triggered when player data is copied (e.g., on respawn or dimension change).
     */
    @JvmField
    val onPlayerCopy = createUnit<ServerPlayerCopyArg>()

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
     * Event triggered when a player picks up experience orbs.
     * Can be cancelled to prevent the pickup.
     */
    @JvmField
    val onPlayerPickupXp = createCancellableUnit<PlayerPickupXpArg>()

    /**
     * Event triggered when a player middle-clicks a block (pick block).
     */
    @JvmField
    val onPickFromBlock = createUnit<PlayerPickFromBlockArg>()

    /**
     * Event triggered when a player middle-clicks an entity (pick entity).
     */
    @JvmField
    val onPickFromEntity = createUnit<PlayerPickFromEntityArg>()

    /**
     * Event triggered when a player jumps (Paper-specific event).
     */
    @JvmField
    val onPlayerJump = createUnit<Any>() // PlayerJumpEvent (Paper-specific)

    /**
     * Event triggered when a player launches a projectile (Paper-specific event).
     */
    @JvmField
    val onLaunchProjectile = createUnit<Any>() // PlayerLaunchProjectileEvent (Paper-specific, cancellable via event)

    /**
     * Initializes and registers all Bukkit event listeners for this event object.
     *
     * @param plugin The Paper plugin instance used to register listeners.
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