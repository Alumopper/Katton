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

object ServerPlayerEvent {
    @JvmField
    val onPlayerJoin = createUnit<PlayerArg>()

    @JvmField
    val onPlayerLeave = createUnit<PlayerArg>()

    @JvmField
    val onAfterPlayerRespawn = createUnit<ServerPlayerAfterRespawnArg>()

    @JvmField
    val onPlayerCopy = createUnit<ServerPlayerCopyArg>()

    @JvmField
    val onPlayerXpChange = createCancellableUnit<PlayerXpChangeArg>()

    @JvmField
    val onPlayerXpLevelChange = createCancellableUnit<PlayerXpLevelChangeArg>()

    @JvmField
    val onPlayerPickupXp = createCancellableUnit<PlayerPickupXpArg>()

    @JvmField
    val onPickFromBlock = createUnit<PlayerPickFromBlockArg>()

    @JvmField
    val onPickFromEntity = createUnit<PlayerPickFromEntityArg>()

    @JvmField
    val onPlayerJump = createUnit<Any>() // PlayerJumpEvent (Paper-specific)

    @JvmField
    val onLaunchProjectile = createUnit<Any>() // PlayerLaunchProjectileEvent (Paper-specific, cancellable via event)

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
                onPlayerJump(event)
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onLaunchProjectile(event: PlayerLaunchProjectileEvent) {
                onLaunchProjectile(event)
            }
        }, plugin)
    }
}
