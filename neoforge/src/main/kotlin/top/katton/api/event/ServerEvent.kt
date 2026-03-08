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
import top.katton.util.createUnit

@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerEvent {
    @SubscribeEvent
    private fun onServerStarting(e: ServerStartingEvent) {
        onServerStarting(ServerArg(e.server))
    }

    @SubscribeEvent
    private fun onServerStarted(e: ServerStartedEvent) {
        onServerStarted(ServerArg(e.server))
    }

    @SubscribeEvent
    private fun onServerStopped(e: ServerStoppedEvent) {
        onServerStopped(ServerArg(e.server))
    }

    @SubscribeEvent
    private fun onServerStopping(e: ServerStoppingEvent) {
        onServerStopping(ServerArg(e.server))
    }

    @SubscribeEvent
    private fun onSyncDatapackContents(e: OnDatapackSyncEvent) {
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
    private fun onStartServerTick(e: ServerTickEvent.Pre) {
        onStartServerTick(ServerTickArg(e.server))
    }

    @SubscribeEvent
    private fun onEndServerTick(e: ServerTickEvent.Post) {
        onEndServerTick(ServerTickArg(e.server))
    }

    @SubscribeEvent
    private fun onStartWorldTick(e: LevelTickEvent.Pre) {
        if (e.level is ServerLevel) {
            onStartWorldTick(WorldTickArg(e.level as ServerLevel))
        }
    }

    @SubscribeEvent
    private fun onEndWorldTick(e: LevelTickEvent.Post) {
        if (e.level is ServerLevel) {
            onEndWorldTick(WorldTickArg(e.level as ServerLevel))
        }
    }

    @SubscribeEvent
    private fun onLevelLoad(e: LevelEvent.Load) {
        val level = e.level as? ServerLevel ?: return
        onLevelLoad(ServerLevelArg(level))
    }

    @SubscribeEvent
    private fun onLevelUnload(e: LevelEvent.Unload) {
        val level = e.level as? ServerLevel ?: return
        onLevelUnload(ServerLevelArg(level))
    }

    @SubscribeEvent
    private fun onLevelSave(e: LevelEvent.Save) {
        val level = e.level as? ServerLevel ?: return
        onLevelSave(ServerLevelArg(level))
    }

    val onServerStarting = createUnit<ServerArg>()

    val onServerStarted = createUnit<ServerArg>()

    val onServerStopped = createUnit<ServerArg>()

    val onServerStopping = createUnit<ServerArg>()

    @JvmField
    val onSyncDatapackContents = createUnit<SyncDatapackContentsArg>()

    @JvmField
    val onStartDatapackReload = createUnit<StartDatapackReloadArg>()

    @JvmField
    val onEndDatapackReload = createUnit<EndDatapackReloadArg>()

    @JvmField
    val onBeforeSave = createUnit<SaveArg>()

    @JvmField
    val onAfterSave = createUnit<SaveArg>()

    val onStartServerTick = createUnit<ServerTickArg>()

    val onEndServerTick = createUnit<ServerTickArg>()

    val onStartWorldTick = createUnit<WorldTickArg>()

    val onEndWorldTick = createUnit<WorldTickArg>()

    @JvmField
    val onLevelLoad = createUnit<ServerLevelArg>()

    @JvmField
    val onLevelUnload = createUnit<ServerLevelArg>()

    @JvmField
    val onLevelSave = createUnit<ServerLevelArg>()

    @JvmInline
    value class ServerLevelArg(val level: ServerLevel)
}
