package top.katton.api.event

import net.minecraft.server.level.ServerLevel
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.OnDatapackSyncEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import top.katton.Katton
import top.katton.util.DelegateEvent

@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerEvent {
    @SubscribeEvent
    private fun handleServerStarting(e: ServerStartingEvent) {
        onServerStarting(ServerArg(e.server))
    }

    @SubscribeEvent
    private fun handleServerStarted(e: ServerStartedEvent) {
        onServerStarted(ServerArg(e.server))
    }

    @SubscribeEvent
    private fun handleServerStopped(e: ServerStoppedEvent) {
        onServerStopped(ServerArg(e.server))
    }

    @SubscribeEvent
    private fun handleServerStopping(e: ServerStoppingEvent) {
        onServerStopping(ServerArg(e.server))
    }

    @SubscribeEvent
    private fun handleSyncDatapackContents(e: OnDatapackSyncEvent) {
        val player = e.player
        if (player != null) {
            onSyncDatapackContents(SyncDatapackContentsArg(player, true))
            return
        }
        e.relevantPlayers.forEach { relevantPlayer ->
            onSyncDatapackContents(SyncDatapackContentsArg(relevantPlayer, false))
        }
    }

    @SubscribeEvent
    private fun handleStartServerTick(e: ServerTickEvent.Pre) {
        onStartServerTick(ServerTickArg(e.server))
    }

    @SubscribeEvent
    private fun handleEndServerTick(e: ServerTickEvent.Post) {
        onEndServerTick(ServerTickArg(e.server))
    }

    @SubscribeEvent
    private fun handleStartWorldTick(e: LevelTickEvent.Pre) {
        if (e.level is ServerLevel) {
            onStartWorldTick(WorldTickArg(e.level as ServerLevel))
        }
    }

    @SubscribeEvent
    private fun handleEndWorldTick(e: LevelTickEvent.Post) {
        if (e.level is ServerLevel) {
            onEndWorldTick(WorldTickArg(e.level as ServerLevel))
        }
    }

    @SubscribeEvent
    private fun handleLevelLoad(e: LevelEvent.Load) {
        val level = e.level as? ServerLevel ?: return
        onLevelLoad(ServerLevelArg(level))
    }

    @SubscribeEvent
    private fun handleLevelUnload(e: LevelEvent.Unload) {
        val level = e.level as? ServerLevel ?: return
        onLevelUnload(ServerLevelArg(level))
    }

    @SubscribeEvent
    private fun handleLevelSave(e: LevelEvent.Save) {
        val level = e.level as? ServerLevel ?: return
        onLevelSave(ServerLevelArg(level))
    }

    val onServerStarting = createUnitEvent<ServerArg>()

    val onServerStarted = createUnitEvent<ServerArg>()

    val onServerStopped = createUnitEvent<ServerArg>()

    val onServerStopping = createUnitEvent<ServerArg>()

    @JvmField
    val onSyncDatapackContents = createUnitEvent<SyncDatapackContentsArg>()

    @JvmField
    val onStartDatapackReload = createUnitEvent<StartDatapackReloadArg>()

    @JvmField
    val onEndDatapackReload = createUnitEvent<EndDatapackReloadArg>()

    @JvmField
    val onBeforeSave = createUnitEvent<SaveArg>()

    @JvmField
    val onAfterSave = createUnitEvent<SaveArg>()

    val onStartServerTick = createUnitEvent<ServerTickArg>()

    val onEndServerTick = createUnitEvent<ServerTickArg>()

    val onStartWorldTick = createUnitEvent<WorldTickArg>()

    val onEndWorldTick = createUnitEvent<WorldTickArg>()

    @JvmField
    val onLevelLoad = createUnitEvent<ServerLevelArg>()

    @JvmField
    val onLevelUnload = createUnitEvent<ServerLevelArg>()

    @JvmField
    val onLevelSave = createUnitEvent<ServerLevelArg>()

    @JvmInline
    value class ServerLevelArg(val level: ServerLevel)

    private fun <T> createUnitEvent() = DelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }
}
