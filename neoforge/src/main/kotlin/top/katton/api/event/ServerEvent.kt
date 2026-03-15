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
import top.katton.util.createUnit

/**
 * Server lifecycle events for NeoForge platform.
 *
 * This object provides events related to server lifecycle including
 * server start/stop, datapack sync, level load/unload, and tick events.
 */
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

    /**
     * Event triggered when the server is starting (before worlds are loaded).
     */
    val onServerStarting = createUnit<ServerArg>()

    /**
     * Event triggered when the server has started (after worlds are loaded).
     */
    val onServerStarted = createUnit<ServerArg>()

    /**
     * Event triggered when the server has stopped.
     */
    val onServerStopped = createUnit<ServerArg>()

    /**
     * Event triggered when the server is stopping.
     */
    val onServerStopping = createUnit<ServerArg>()

    /**
     * Event triggered when datapack contents are being synced to players.
     */
    @JvmField
    val onSyncDatapackContents = createUnit<SyncDatapackContentsArg>()

    /**
     * Event triggered when a datapack reload is starting.
     * Note: NeoForge doesn't have a direct equivalent; this is a placeholder.
     */
    @JvmField
    val onStartDatapackReload = createUnit<StartDatapackReloadArg>()

    /**
     * Event triggered when a datapack reload has completed.
     * Note: NeoForge doesn't have a direct equivalent; this is a placeholder.
     */
    @JvmField
    val onEndDatapackReload = createUnit<EndDatapackReloadArg>()

    /**
     * Event triggered before the server saves data.
     * Note: NeoForge doesn't have a direct equivalent; this is a placeholder.
     */
    @JvmField
    val onBeforeSave = createUnit<ServerSaveArg>()

    /**
     * Event triggered after the server has saved data.
     * Note: NeoForge doesn't have a direct equivalent; this is a placeholder.
     */
    @JvmField
    val onAfterSave = createUnit<ServerSaveArg>()

    /**
     * Event triggered at the start of each server tick.
     */
    val onStartServerTick = createUnit<ServerTickArg>()

    /**
     * Event triggered at the end of each server tick.
     */
    val onEndServerTick = createUnit<ServerTickArg>()

    /**
     * Event triggered at the start of each world/level tick.
     */
    val onStartWorldTick = createUnit<WorldTickArg>()

    /**
     * Event triggered at the end of each world/level tick.
     */
    val onEndWorldTick = createUnit<WorldTickArg>()

    /**
     * Event triggered when a level is loaded.
     */
    @JvmField
    val onLevelLoad = createUnit<ServerLevelArg>()

    /**
     * Event triggered when a level is unloaded.
     */
    @JvmField
    val onLevelUnload = createUnit<ServerLevelArg>()

    /**
     * Event triggered when a level is saved.
     */
    @JvmField
    val onLevelSave = createUnit<ServerLevelArg>()

    /**
     * Wrapper class for server level arguments.
     *
     * @property level The server level
     */
    @JvmInline
    value class ServerLevelArg(val level: ServerLevel)
}
