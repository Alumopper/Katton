package top.katton.api.event

import org.bukkit.event.EventHandler; import org.bukkit.event.Listener; import org.bukkit.event.block.*; import org.bukkit.event.entity.*
import org.bukkit.event.world.ChunkLoadEvent; import org.bukkit.plugin.java.JavaPlugin; import top.katton.paper.PaperNmsBridge; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

object ChunkAndBlockEvent {
    @JvmField val onChunkLoad = createUnit<Any>()
    @JvmField val onChunkUnload = createUnit<Any>()
    @JvmField val onChunkLevelTypeChange = createUnit<Any>()
    @JvmField val onBlockEntityLoad = createUnit<Any>()
    @JvmField val onBlockEntityUnload = createUnit<Any>()
    @JvmField val onBeforeBlockBreak = createUnit<Any>()
    @JvmField val onAfterBlockBreak = createUnit<Any>()
    @JvmField val onCanceledBlockBreak = createUnit<Any>()
    @JvmField val onBlockPlace = createUnit<Any>()
    @JvmField val onExplosionStart = createUnit<Any>()
    @JvmField val onExplosionDetonate = createUnit<Any>()

    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler fun onBreak(e: BlockBreakEvent) {
                onAfterBlockBreak(PaperNmsBridge.toNmsPlayer(e.player))
            }
            @EventHandler fun onPlace(e: BlockPlaceEvent) {
                onBlockPlace(PaperNmsBridge.toNmsPlayer(e.player))
            }
            @EventHandler fun onChunkLoad(e: ChunkLoadEvent) {
                onChunkLoad(PaperNmsBridge.toNmsLevel(e.world))
            }
            @EventHandler fun onExplosionPrime(e: ExplosionPrimeEvent) { onExplosionStart(e.entity) }
            @EventHandler fun onExplosion(e: EntityExplodeEvent) { onExplosionDetonate(e.entity) }
        }, plugin)
    }
}




