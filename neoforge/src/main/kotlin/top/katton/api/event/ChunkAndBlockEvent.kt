package top.katton.api.event

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.storage.SerializableChunkData
import net.minecraft.world.level.chunk.status.ChunkType
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkDataEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.ChunkWatchEvent
import net.neoforged.neoforge.event.level.ExplosionEvent
import net.neoforged.neoforge.event.level.block.BreakBlockEvent
import top.katton.Katton
import top.katton.util.CancellableDelegateEvent
import top.katton.util.CancellableEventArg
import top.katton.util.DelegateEvent
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit
import top.katton.util.setCancel

/**
 * %en
 * Chunk, block, and explosion events for NeoForge platform.
 *
 * This object provides events related to chunk loading/unloading,
 * block breaking/placing, and explosions.
 *
 * %zh
 * NeoForge 平台的区块、方块和爆炸事件。
 * 此对象提供与区块加载/卸载、方块破坏/放置以及爆炸相关的事件。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ChunkAndBlockEvent {

    @JvmStatic
    @SubscribeEvent
    private fun handleChunkLoad(e: ChunkEvent.Load) {
        val level = e.level as? ServerLevel ?: return
        val chunk = e.chunk as? LevelChunk ?: return
        onChunkLoad(ChunkLoadArg(level, chunk, e.isNewChunk))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleChunkUnload(e: ChunkEvent.Unload) {
        val level = e.level as? ServerLevel ?: return
        val chunk = e.chunk as? LevelChunk ?: return
        onChunkUnload(ChunkUnloadArg(level, chunk))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleChunkDataLoad(e: ChunkDataEvent.Load) {
        val level = e.level as? ServerLevel ?: return
        onChunkDataLoad(NeoChunkDataLoadArg(level, e.chunk, e.data, e.type))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleChunkDataSave(e: ChunkDataEvent.Save) {
        val level = e.level as? ServerLevel ?: return
        onChunkDataSave(NeoChunkDataSaveArg(level, e.chunk, e.data))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleChunkWatch(e: ChunkWatchEvent.Watch) {
        onChunkWatch(NeoChunkWatchArg(e.player, e.level, e.chunk))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleChunkSent(e: ChunkWatchEvent.Sent) {
        onChunkSent(NeoChunkWatchArg(e.player, e.level, e.chunk))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleChunkUnWatch(e: ChunkWatchEvent.UnWatch) {
        onChunkUnWatch(NeoChunkUnWatchArg(e.player, e.level, e.pos))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleBlockBreak(e: BreakBlockEvent) {
        if (e.level is ServerLevel) {
            val arg = BlockBreakArg(
                e.level as ServerLevel,
                e.player,
                e.pos,
                e.state,
                e.level.getBlockEntity(e.pos)
            )
            onBlockBreak(arg)
            setCancel(onBlockBreak, e)
        }
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleBlockPlace(e: BlockEvent.EntityPlaceEvent) {
        if (e.level is ServerLevel) {
            val arg = BlockPlaceArg(
                e.level as ServerLevel,
                e.entity as? Player,
                e.pos,
                e.state,
                e.level.getBlockEntity(e.pos)
            )
            onBlockPlace(arg)
            setCancel(onBlockPlace, e)
        }
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleExplosionStart(e: ExplosionEvent.Start) {
        if (e.level is ServerLevel) {
            val arg = ExplosionStartArg(
                e.level as ServerLevel,
                e.explosion
            )
            onExplosionStart(arg)
            setCancel(onExplosionStart, e)
        }
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleExplosionDetonate(e: ExplosionEvent.Detonate) {
        if (e.level is ServerLevel) {
            onExplosionDetonate(ExplosionDetonateArg(e.level as ServerLevel, e.explosion, e.affectedEntities))
        }
    }

    /**
     * %en
     * Event triggered when a chunk is loaded.
     *
     * %zh
     * 当区块加载时触发。
     */
    val onChunkLoad = createUnit<ChunkLoadArg>()

    /**
     * %en
     * Event triggered when a chunk is unloaded.
     *
     * %zh
     * 当区块卸载时触发。
     */
    val onChunkUnload = createUnit<ChunkUnloadArg>()

    /**
     * %en
     * Event triggered when chunk data is loaded from disk.
     *
     * %zh
     * 当区块数据从磁盘加载时触发。
     */
    val onChunkDataLoad = createUnit<NeoChunkDataLoadArg>()

    /**
     * %en
     * Event triggered when chunk data is saved to disk.
     *
     * %zh
     * 当区块数据保存到磁盘时触发。
     */
    val onChunkDataSave = createUnit<NeoChunkDataSaveArg>()

    /**
     * %en
     * Event triggered when a player starts watching a chunk.
     *
     * %zh
     * 当玩家开始监视某个区块时触发。
     */
    val onChunkWatch = createUnit<NeoChunkWatchArg>()

    /**
     * %en
     * Event triggered when a chunk is sent to a player.
     *
     * %zh
     * 当区块被发送给玩家时触发。
     */
    val onChunkSent = createUnit<NeoChunkWatchArg>()

    /**
     * %en
     * Event triggered when a player stops watching a chunk.
     *
     * %zh
     * 当玩家停止监视某个区块时触发。
     */
    val onChunkUnWatch = createUnit<NeoChunkUnWatchArg>()

    /**
     * %en
     * Event triggered when a chunk's level type changes.
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 当区块的维度类型发生变化时触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onChunkLevelTypeChange = createUnit<ChunkStatusChangeArg>()

    /**
     * %en
     * Event triggered when a block entity is loaded.
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 当方块实体加载时触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onBlockEntityLoad = createUnit<BlockEntityLoadArg>()

    /**
     * %en
     * Event triggered when a block entity is unloaded.
     * Note: This is a placeholder for NeoForge compatibility.
     *
     * %zh
     * 当方块实体卸载时触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onBlockEntityUnload = createUnit<BlockEntityLoadArg>()

    /**
     * %en
     * Event triggered when a player breaks a block.
     * Can be cancelled to prevent the break.
     *
     * %zh
     * 当玩家破坏方块时触发。
     * 可取消以阻止破坏。
     */
    val onBlockBreak = createCancellableUnit<BlockBreakArg>()

    /**
     * %en
     * Event triggered when a player places a block.
     * Can be cancelled to prevent the placement.
     *
     * %zh
     * 当玩家放置方块时触发。
     * 可取消以阻止放置。
     */
    val onBlockPlace = createCancellableUnit<BlockPlaceArg>()

    /**
     * %en
     * Event triggered when an explosion starts.
     * Can be cancelled to prevent the explosion.
     *
     * %zh
     * 当爆炸开始时触发。
     * 可取消以阻止爆炸。
     */
    val onExplosionStart = createCancellableUnit<ExplosionStartArg>()

    /**
     * %en
     * Event triggered when an explosion detonates.
     * Use this to modify affected blocks/entities.
     *
     * %zh
     * 当爆炸引爆时触发。
     * 可用于修改受影响的方块或实体。
     */
    val onExplosionDetonate = createUnit<ExplosionDetonateArg>()

    /**
     * %en
     * Argument class for NeoForge chunk data load events.
     *
     * %zh
     * NeoForge 区块数据加载事件的参数类。
     * @property level
     * %en The server level
     * %zh 服务端维度。
     * @property chunk
     * %en The chunk being loaded
     * %zh 正在加载的区块。
     * @property data
     * %en The serializable chunk data
     * %zh 可序列化的区块数据。
     * @property type
     * %en The chunk type
     * %zh 区块类型。
     */
    data class NeoChunkDataLoadArg(
        val level: ServerLevel,
        val chunk: ChunkAccess,
        val data: SerializableChunkData,
        val type: ChunkType
    )

    /**
     * %en
     * Argument class for NeoForge chunk data save events.
     *
     * %zh
     * NeoForge 区块数据保存事件的参数类。
     * @property level
     * %en The server level
     * %zh 服务端维度。
     * @property chunk
     * %en The chunk being saved
     * %zh 正在保存的区块。
     * @property data
     * %en The serializable chunk data
     * %zh 可序列化的区块数据。
     */
    data class NeoChunkDataSaveArg(
        val level: ServerLevel,
        val chunk: ChunkAccess,
        val data: SerializableChunkData
    )

    /**
     * %en
     * Argument class for NeoForge chunk watch events.
     *
     * %zh
     * NeoForge 区块监视事件的参数类。
     * @property player
     * %en The player watching the chunk
     * %zh 正在监视该区块的玩家。
     * @property level
     * %en The server level
     * %zh 服务端维度。
     * @property chunk
     * %en The level chunk being watched
     * %zh 正在被监视的区块。
     */
    data class NeoChunkWatchArg(
        val player: ServerPlayer,
        val level: ServerLevel,
        val chunk: LevelChunk
    )

    /**
     * %en
     * Argument class for NeoForge chunk unwatch events.
     *
     * %zh
     * NeoForge 区块取消监视事件的参数类。
     * @property player
     * %en The player stopping watching the chunk
     * %zh 停止监视该区块的玩家。
     * @property level
     * %en The server level
     * %zh 服务端维度。
     * @property pos
     * %en The chunk position
     * %zh 区块坐标。
     */
    data class NeoChunkUnWatchArg(
        val player: ServerPlayer,
        val level: ServerLevel,
        val pos: ChunkPos
    )
}
