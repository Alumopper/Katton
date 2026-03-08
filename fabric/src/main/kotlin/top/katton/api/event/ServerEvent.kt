package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import top.katton.util.createUnit

/**
 * Server lifecycle related events: server start/stop, datapack reload, save hooks.
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
        ServerLifecycleEvents.BEFORE_SAVE.register { a, b, c -> onBeforeSave(SaveArg(a,b,c)) }
        ServerLifecycleEvents.AFTER_SAVE.register { a, b, c -> onAfterSave(SaveArg(a,b,c)) }
        ServerTickEvents.START_SERVER_TICK.register { onStartServerTick(ServerTickArg(it)) }
        ServerTickEvents.END_SERVER_TICK.register { onEndServerTick(ServerTickArg(it)) }
        ServerTickEvents.START_LEVEL_TICK.register { onStartWorldTick(WorldTickArg(it)) }
        ServerTickEvents.END_LEVEL_TICK.register { onEndWorldTick(WorldTickArg(it)) }
    }

    val onServerStarting = createUnit<ServerArg>()

    val onServerStarted = createUnit<ServerArg>()

    val onServerStopping = createUnit<ServerArg>()

    val onServerStopped = createUnit<ServerArg>()

    val onSyncDatapackContents = createUnit<SyncDatapackContentsArg>()

    val onStartDatapackReload = createUnit<StartDatapackReloadArg>()

    val onEndDatapackReload = createUnit<EndDatapackReloadArg>()

    val onBeforeSave = createUnit<SaveArg>()

    val onAfterSave = createUnit<SaveArg>()

    val onStartServerTick = createUnit<ServerTickArg>()

    val onEndServerTick = createUnit<ServerTickArg>()

    val onStartWorldTick = createUnit<WorldTickArg>()

    val onEndWorldTick = createUnit<WorldTickArg>()
}