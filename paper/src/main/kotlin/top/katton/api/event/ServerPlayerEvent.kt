package top.katton.api.event

import net.minecraft.server.level.ServerPlayer
import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit; import top.katton.util.createCancellableUnit

object ServerPlayerEvent {
    @JvmField val onPlayerJoin = createUnit<PlayerArg>()
    @JvmField val onPlayerLeave = createUnit<PlayerArg>()
    @JvmField val onAfterPlayerRespawn = createUnit<Any>()
    @JvmField val onPlayerCopy = createUnit<Any>()
    @JvmField val onPlayerXpChange = createCancellableUnit<PlayerXpChangeArg>()
    @JvmField val onPlayerXpLevelChange = createCancellableUnit<PlayerXpLevelChangeArg>()
    @JvmField val onPlayerPickupXp = createUnit<Any>()
    @JvmField val onPickFromBlock = createUnit<Any>()
    @JvmField val onPickFromEntity = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onJoin(e: PlayerJoinEvent) {
                val p: ServerPlayer = PaperNmsBridge.toNmsPlayer(e.player); onPlayerJoin(PlayerArg(p))
            }
            @EventHandler fun onQuit(e: PlayerQuitEvent) {
                val p: ServerPlayer = PaperNmsBridge.toNmsPlayer(e.player); onPlayerLeave(PlayerArg(p))
            }
            @EventHandler fun onRespawn(e: PlayerRespawnEvent) { onAfterPlayerRespawn(PaperNmsBridge.toNmsPlayer(e.player)) }
            @EventHandler fun onExpChange(e: PlayerExpChangeEvent) {
                val p: ServerPlayer = PaperNmsBridge.toNmsPlayer(e.player); onPlayerXpChange(PlayerXpChangeArg(p, e.amount))
            }
            @EventHandler fun onLevelChange(e: PlayerLevelChangeEvent) {
                val p: ServerPlayer = PaperNmsBridge.toNmsPlayer(e.player); onPlayerXpLevelChange(PlayerXpLevelChangeArg(p, e.newLevel - e.oldLevel))
            }
        }, plugin)
    }
}




