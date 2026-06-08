package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * %en
 * Chunk, block entity, and block events for Fabric platform.
 *
 * This object provides events related to chunk loading/unloading,
 * block entity lifecycle, block breaking, and explosions.
 *
 * %zh
 * Fabric 平台的区块、方块实体和方块事件。
 * 此对象提供与区块加载/卸载、方块实体生命周期、方块破坏以及爆炸相关的事件。
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
 * %en
 * Event triggered when a chunk is loaded.
 *
 * %zh
 * 在区块加载时触发。
 */
    val onChunkLoad = createUnit<ChunkLoadArg>()

    /**
 * %en
 * Event triggered when a chunk is unloaded.
 *
 * %zh
 * 在区块卸载时触发。
 */
    val onChunkUnload = createUnit<ChunkUnloadArg>()

    /**
 * %en
 * Event triggered when a chunk's full status changes.
 *
 * %zh
 * 当区块的完整状态发生变化时触发。
 */
    val onChunkLevelTypeChange = createUnit<ChunkStatusChangeArg>()

    // === Block Entity Events ===

    /**
 * %en
 * Event triggered when a block entity is loaded.
 *
 * %zh
 * 当方块实体加载时触发。
 */
    val onBlockEntityLoad = createUnit<BlockEntityLoadArg>()

    /**
 * %en
 * Event triggered when a block entity is unloaded.
 *
 * %zh
 * 当方块实体卸载时触发。
 */
    val onBlockEntityUnload = createUnit<BlockEntityLoadArg>()

    // === Block Break Events ===

    /**
 * %en
 * Event triggered before a player breaks a block.
 *
 * %zh
 * 当玩家破坏方块之前触发。
 * @return
 * %en to allow the break, false to cancel it.
 * %zh 返回值为允许破坏，false 表示取消。
 */
    val onBeforeBlockBreak = createAll<BlockBreakArg>()

    /**
 * %en
 * Event triggered after a player breaks a block.
 *
 * %zh
 * 当玩家破坏方块之后触发。
 */
    val onAfterBlockBreak = createUnit<BlockBreakArg>()

    /**
 * %en
 * Event triggered when a block break is canceled.
 *
 * %zh
 * 当方块破坏被取消时触发。
 */
    val onCanceledBlockBreak = createUnit<BlockBreakArg>()

    // === Explosion Events ===

    /**
 * %en
 * Event triggered when an explosion starts.
 * Can be cancelled to prevent the explosion.
 *
 * %zh
 * 当爆炸开始时触发。
 * 可取消以阻止该爆炸。
 */
    @JvmField
    val onExplosionStart = createCancellableUnit<ExplosionStartArg>()

    /**
 * %en
 * Event triggered when an explosion detonates.
 * Use this to modify affected blocks/entities.
 *
 * %zh
 * 当爆炸引爆时触发。
 * 可用于修改受影响的方块/实体。
 */
    @JvmField
    val onExplosionDetonate = createUnit<ExplosionDetonateArg>()
}
