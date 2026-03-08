package top.katton.api.event

import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
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
import top.katton.Katton
import top.katton.util.CancellableDelegateEvent
import top.katton.util.CancellableEventArg
import top.katton.util.DelegateEvent
import top.katton.util.setCancel

/**
 * Chunk, block entity, and block events (load/unload/break), plus explosion events.
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ChunkAndBlockEvent {

    @SubscribeEvent
    private fun handleChunkLoad(e: ChunkEvent.Load) {
        val level = e.level as? ServerLevel ?: return
        val chunk = e.chunk as? LevelChunk ?: return
        onChunkLoad(ChunkLoadArg(level, chunk, e.isNewChunk))
    }

    @SubscribeEvent
    private fun handleChunkUnload(e: ChunkEvent.Unload) {
        val level = e.level as? ServerLevel ?: return
        val chunk = e.chunk as? LevelChunk ?: return
        onChunkUnload(ChunkUnloadArg(level, chunk))
    }

    @SubscribeEvent
    private fun handleChunkDataLoad(e: ChunkDataEvent.Load) {
        val level = e.level as? ServerLevel ?: return
        onChunkDataLoad(NeoChunkDataLoadArg(level, e.chunk, e.data, e.type))
    }

    @SubscribeEvent
    private fun handleChunkDataSave(e: ChunkDataEvent.Save) {
        val level = e.level as? ServerLevel ?: return
        onChunkDataSave(NeoChunkDataSaveArg(level, e.chunk, e.data))
    }

    @SubscribeEvent
    private fun handleChunkWatch(e: ChunkWatchEvent.Watch) {
        onChunkWatch(NeoChunkWatchArg(e.player, e.level, e.chunk))
    }

    @SubscribeEvent
    private fun handleChunkSent(e: ChunkWatchEvent.Sent) {
        onChunkSent(NeoChunkWatchArg(e.player, e.level, e.chunk))
    }

    @SubscribeEvent
    private fun handleChunkUnWatch(e: ChunkWatchEvent.UnWatch) {
        onChunkUnWatch(NeoChunkUnWatchArg(e.player, e.level, e.pos))
    }

    @SubscribeEvent
    private fun handleBlockBreak(e: BlockEvent.BreakEvent) {
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

    @SubscribeEvent
    private fun handleExplosionDetonate(e: ExplosionEvent.Detonate) {
        if (e.level is ServerLevel) {
            onExplosionDetonate(ExplosionDetonateArg(e.level as ServerLevel, e.explosion, e.affectedEntities))
        }
    }

    val onChunkLoad = createUnitEvent<ChunkLoadArg>()

    val onChunkUnload = createUnitEvent<ChunkUnloadArg>()

    val onChunkDataLoad = createUnitEvent<NeoChunkDataLoadArg>()

    val onChunkDataSave = createUnitEvent<NeoChunkDataSaveArg>()

    val onChunkWatch = createUnitEvent<NeoChunkWatchArg>()

    val onChunkSent = createUnitEvent<NeoChunkWatchArg>()

    val onChunkUnWatch = createUnitEvent<NeoChunkUnWatchArg>()

    @JvmField
    val onChunkLevelTypeChange = createUnitEvent<ChunkStatusChangeArg>()

    @JvmField
    val onBlockEntityLoad = createUnitEvent<BlockEntityLoadArg>()

    @JvmField
    val onBlockEntityUnload = createUnitEvent<BlockEntityLoadArg>()

    val onBlockBreak = createCancellableUnitEvent<BlockBreakArg>()

    val onBlockPlace = createCancellableUnitEvent<BlockPlaceArg>()

    val onExplosionStart = createCancellableUnitEvent<ExplosionStartArg>()

    val onExplosionDetonate = createUnitEvent<ExplosionDetonateArg>()

    data class NeoChunkDataLoadArg(
        val level: ServerLevel,
        val chunk: ChunkAccess,
        val data: SerializableChunkData,
        val type: ChunkType
    )

    data class NeoChunkDataSaveArg(
        val level: ServerLevel,
        val chunk: ChunkAccess,
        val data: SerializableChunkData
    )

    data class NeoChunkWatchArg(
        val player: ServerPlayer,
        val level: ServerLevel,
        val chunk: LevelChunk
    )

    data class NeoChunkUnWatchArg(
        val player: ServerPlayer,
        val level: ServerLevel,
        val pos: ChunkPos
    )

    private fun <T> createUnitEvent() = DelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }

    private fun <T : CancellableEventArg> createCancellableUnitEvent() = CancellableDelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }
}
