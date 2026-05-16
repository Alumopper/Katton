package top.katton.api.event

import net.minecraft.world.level.chunk.LevelChunk
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

object ChunkAndBlockEvent {
    @JvmField
    val onChunkLoad = createUnit<ChunkLoadArg>()

    @JvmField
    val onChunkUnload = createUnit<ChunkUnloadArg>()

    @JvmField
    val onChunkLevelTypeChange = createUnit<ChunkStatusChangeArg>()

    @JvmField
    val onBlockEntityLoad = createUnit<BlockEntityLoadArg>()

    @JvmField
    val onBlockEntityUnload = createUnit<BlockEntityLoadArg>()

    @JvmField
    val onBeforeBlockBreak = createAll<BlockBreakArg>()

    @JvmField
    val onAfterBlockBreak = createUnit<BlockBreakArg>()

    @JvmField
    val onCanceledBlockBreak = createUnit<BlockBreakArg>()

    @JvmField
    val onBlockPlace = createCancellableUnit<BlockPlaceArg>()

    @JvmField
    val onExplosionStart = createCancellableUnit<ExplosionStartArg>()

    @JvmField
    val onExplosionDetonate = createUnit<ExplosionDetonateArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onChunkLoad(event: ChunkLoadEvent) {
                val level = PaperNmsBridge.toNmsLevel(event.world)
                val chunk = PaperNmsBridge.toNmsChunk(event.chunk) ?: return
                onChunkLoad(ChunkLoadArg(level, chunk, event.isNewChunk))
                chunk.blockEntities.values.forEach { blockEntity ->
                    onBlockEntityLoad(BlockEntityLoadArg(blockEntity, level))
                }
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onChunkUnload(event: ChunkUnloadEvent) {
                val level = PaperNmsBridge.toNmsLevel(event.world)
                val chunk = PaperNmsBridge.toNmsChunk(event.chunk) ?: return
                chunk.blockEntities.values.forEach { blockEntity ->
                    onBlockEntityUnload(BlockEntityLoadArg(blockEntity, level))
                }
                onChunkUnload(ChunkUnloadArg(level, chunk))
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onBreak(event: BlockBreakEvent) {
                val world = PaperNmsBridge.toNmsLevel(event.block.world)
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val pos = PaperNmsBridge.toNmsBlockPos(event.block.location)
                val arg = BlockBreakArg(
                    world,
                    player,
                    pos,
                    world.getBlockState(pos),
                    world.getBlockEntity(pos)
                )
                if (!onBeforeBlockBreak(arg).getOrElse { true }) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onBreakAfter(event: BlockBreakEvent) {
                val world = PaperNmsBridge.toNmsLevel(event.block.world)
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val pos = PaperNmsBridge.toNmsBlockPos(event.block.location)
                onAfterBlockBreak(
                    BlockBreakArg(
                        world,
                        player,
                        pos,
                        world.getBlockState(pos),
                        world.getBlockEntity(pos)
                    )
                )
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
            fun onBreakCancelled(event: BlockBreakEvent) {
                if (!event.isCancelled) {
                    return
                }

                val world = PaperNmsBridge.toNmsLevel(event.block.world)
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val pos = PaperNmsBridge.toNmsBlockPos(event.block.location)
                onCanceledBlockBreak(
                    BlockBreakArg(
                        world,
                        player,
                        pos,
                        world.getBlockState(pos),
                        world.getBlockEntity(pos)
                    )
                )
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onPlace(event: BlockPlaceEvent) {
                val world = PaperNmsBridge.toNmsLevel(event.block.world)
                val pos = PaperNmsBridge.toNmsBlockPos(event.block.location)
                val arg = BlockPlaceArg(
                    world,
                    PaperNmsBridge.toNmsPlayer(event.player),
                    pos,
                    world.getBlockState(pos),
                    world.getBlockEntity(pos)
                )
                onBlockPlace(arg)
                if (arg.isCancelled()) {
                    event.isCancelled = true
                }
            }
        }, plugin)
    }
}
