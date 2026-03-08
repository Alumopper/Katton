package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * Chunk, block entity, and block events (load/unload/break), plus explosion events.
 */
@Suppress("unused")
object ChunkAndBlockEvent {
    // === Chunk Events ===
    fun initialize() {
        ServerChunkEvents.CHUNK_LOAD.register { a, b, c ->
            onChunkLoad(ChunkLoadArg(a, b, c))
        }

        ServerChunkEvents.CHUNK_UNLOAD.register { a, b ->
            onChunkUnload(ChunkUnloadArg(a, b))
        }

        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register { a, b, c, d ->
            onChunkLevelTypeChange(ChunkStatusChangeArg(a, b, c, d))
        }

        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register { a, b ->
            onBlockEntityLoad(BlockEntityLoadArg(a, b))
        }

        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register { a, b ->
            onBlockEntityUnload(BlockEntityLoadArg(a, b))
        }

        PlayerBlockBreakEvents.BEFORE.register { a, b, c, d, e ->
            onBeforeBlockBreak(BlockBreakArg(a, b, c, d, e)).getOrElse { true }
        }

        PlayerBlockBreakEvents.AFTER.register { a, b, c, d, e ->
            onAfterBlockBreak(BlockBreakArg(a, b, c, d, e))
        }

        PlayerBlockBreakEvents.CANCELED.register { a, b, c, d, e ->
            onCanceledBlockBreak(BlockBreakArg(a, b, c, d, e))
        }
    }

    val onChunkLoad = createUnit<ChunkLoadArg>()

    val onChunkUnload = createUnit<ChunkUnloadArg>()

    val onChunkLevelTypeChange = createUnit<ChunkStatusChangeArg>()

    // === Block Entity Events ===
    val onBlockEntityLoad = createUnit<BlockEntityLoadArg>()

    val onBlockEntityUnload = createUnit<BlockEntityLoadArg>()

    // === Block Break Events ===
    val onBeforeBlockBreak = createAll<BlockBreakArg>()

    val onAfterBlockBreak = createUnit<BlockBreakArg>()

    val onCanceledBlockBreak = createUnit<BlockBreakArg>()

    // === Explosion Events ===
    @JvmField
    val onExplosionStart = createCancellableUnit<ExplosionStartArg>()

    @JvmField
    val onExplosionDetonate = createUnit<ExplosionDetonateArg>()
}
