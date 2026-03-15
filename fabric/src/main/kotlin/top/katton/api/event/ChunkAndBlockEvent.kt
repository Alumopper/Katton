package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * Chunk, block entity, and block events for Fabric platform.
 *
 * This object provides events related to chunk loading/unloading,
 * block entity lifecycle, block breaking, and explosions.
 */
@Suppress("unused")
object ChunkAndBlockEvent {

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

    // === Chunk Events ===

    /**
     * Event triggered when a chunk is loaded.
     */
    val onChunkLoad = createUnit<ChunkLoadArg>()

    /**
     * Event triggered when a chunk is unloaded.
     */
    val onChunkUnload = createUnit<ChunkUnloadArg>()

    /**
     * Event triggered when a chunk's full status changes.
     */
    val onChunkLevelTypeChange = createUnit<ChunkStatusChangeArg>()

    // === Block Entity Events ===

    /**
     * Event triggered when a block entity is loaded.
     */
    val onBlockEntityLoad = createUnit<BlockEntityLoadArg>()

    /**
     * Event triggered when a block entity is unloaded.
     */
    val onBlockEntityUnload = createUnit<BlockEntityLoadArg>()

    // === Block Break Events ===

    /**
     * Event triggered before a player breaks a block.
     *
     * @return true to allow the break, false to cancel it.
     */
    val onBeforeBlockBreak = createAll<BlockBreakArg>()

    /**
     * Event triggered after a player breaks a block.
     */
    val onAfterBlockBreak = createUnit<BlockBreakArg>()

    /**
     * Event triggered when a block break is canceled.
     */
    val onCanceledBlockBreak = createUnit<BlockBreakArg>()

    // === Explosion Events ===

    /**
     * Event triggered when an explosion starts.
     * Can be cancelled to prevent the explosion.
     */
    @JvmField
    val onExplosionStart = createCancellableUnit<ExplosionStartArg>()

    /**
     * Event triggered when an explosion detonates.
     * Use this to modify affected blocks/entities.
     */
    @JvmField
    val onExplosionDetonate = createUnit<ExplosionDetonateArg>()
}
