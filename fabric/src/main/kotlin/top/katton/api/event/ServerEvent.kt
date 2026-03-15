package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import top.katton.util.createUnit

/**
 * Server lifecycle events for Fabric platform.
 *
 * This object provides events related to server lifecycle including
 * server start/stop, datapack reload, save hooks, and tick events.
 */
object ServerEvent {

    fun initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register { onServerStarting(ServerArg(it)) }
        ServerLifecycleEvents.SERVER_STARTED.register { onServerStarted(ServerArg(it)) }
        ServerLifecycleEvents.SERVER_STOPPING.register { onServerStopping(ServerArg(it)) }
        ServerLifecycleEvents.SERVER_STOPPED.register { onServerStopped(ServerArg(it)) }
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register { a, b -> onSyncDatapackContents(SyncDatapackContentsArg(a,b)) }
        ServerLifecycleEvents.START_DATA_PACK_RELOAD.register { a, b -> onStartDatapackReload(StartDatapackReloadArg(a,b)) }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { a, b, c -> onEndDatapackReload(EndDatapackReloadArg(a,b,c)) }
        ServerLifecycleEvents.BEFORE_SAVE.register { a, b, c -> onBeforeSave(ServerSaveArg(a,b,c)) }
        ServerLifecycleEvents.AFTER_SAVE.register { a, b, c -> onAfterSave(ServerSaveArg(a,b,c)) }
        ServerTickEvents.START_SERVER_TICK.register { onStartServerTick(ServerTickArg(it)) }
        ServerTickEvents.END_SERVER_TICK.register { onEndServerTick(ServerTickArg(it)) }
        ServerTickEvents.START_LEVEL_TICK.register { onStartWorldTick(WorldTickArg(it)) }
        ServerTickEvents.END_LEVEL_TICK.register { onEndWorldTick(WorldTickArg(it)) }
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
     * Event triggered when the server is stopping.
     */
    val onServerStopping = createUnit<ServerArg>()

    /**
     * Event triggered when the server has stopped.
     */
    val onServerStopped = createUnit<ServerArg>()

    /**
     * Event triggered when datapack contents are being synced to players.
     */
    val onSyncDatapackContents = createUnit<SyncDatapackContentsArg>()

    /**
     * Event triggered when a datapack reload is starting.
     */
    val onStartDatapackReload = createUnit<StartDatapackReloadArg>()

    /**
     * Event triggered when a datapack reload has completed.
     */
    val onEndDatapackReload = createUnit<EndDatapackReloadArg>()

    /**
     * Event triggered before the server saves data.
     */
    val onBeforeSave = createUnit<ServerSaveArg>()

    /**
     * Event triggered after the server has saved data.
     */
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
}
