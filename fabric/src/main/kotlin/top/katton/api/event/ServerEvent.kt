package top.katton.api.event

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import top.katton.network.ServerItemRenderMarkerManager
import top.katton.util.createUnit

/**
 * %en
 * Server lifecycle events for Fabric platform.
 *
 * This object provides events related to server lifecycle including
 * server start/stop, datapack reload, save hooks, and tick events.
 *
 * %zh
 * Fabric 平台的服务器生命周期事件。
 * 此对象提供与服务器生命周期相关的事件，包括启动、停止、数据包重载、保存钩子和刻事件。
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
        ServerTickEvents.END_SERVER_TICK.register {
            ServerItemRenderMarkerManager.tick()
            onEndServerTick(ServerTickArg(it))
        }
        ServerTickEvents.START_LEVEL_TICK.register { onStartWorldTick(WorldTickArg(it)) }
        ServerTickEvents.END_LEVEL_TICK.register { onEndWorldTick(WorldTickArg(it)) }
    }

    /**
 * %en
 * Event triggered when the server is starting (before worlds are loaded).
 *
 * %zh
 * 当服务器启动中（世界加载前）时触发。
 */
    val onServerStarting = createUnit<ServerArg>()

    /**
 * %en
 * Event triggered when the server has started (after worlds are loaded).
 *
 * %zh
 * 当服务器已启动（世界加载后）时触发。
 */
    val onServerStarted = createUnit<ServerArg>()

    /**
 * %en
 * Event triggered when the server is stopping.
 *
 * %zh
 * 当服务器正在停止时触发。
 */
    val onServerStopping = createUnit<ServerArg>()

    /**
 * %en
 * Event triggered when the server has stopped.
 *
 * %zh
 * 当服务器已停止时触发。
 */
    val onServerStopped = createUnit<ServerArg>()

    /**
 * %en
 * Event triggered when datapack contents are being synced to players.
 *
 * %zh
 * 当数据包内容正在同步给玩家时触发。
 */
    val onSyncDatapackContents = createUnit<SyncDatapackContentsArg>()

    /**
 * %en
 * Event triggered when a datapack reload is starting.
 *
 * %zh
 * 当数据包重载开始时触发。
 */
    val onStartDatapackReload = createUnit<StartDatapackReloadArg>()

    /**
 * %en
 * Event triggered when a datapack reload has completed.
 *
 * %zh
 * 当数据包重载完成时触发。
 */
    val onEndDatapackReload = createUnit<EndDatapackReloadArg>()

    /**
 * %en
 * Event triggered before the server saves data.
 *
 * %zh
 * 当服务器保存数据之前触发。
 */
    val onBeforeSave = createUnit<ServerSaveArg>()

    /**
 * %en
 * Event triggered after the server has saved data.
 *
 * %zh
 * 当服务器保存数据之后触发。
 */
    val onAfterSave = createUnit<ServerSaveArg>()

    /**
 * %en
 * Event triggered at the start of each server tick.
 *
 * %zh
 * 当每个服务器 tick 开始时触发。
 */
    val onStartServerTick = createUnit<ServerTickArg>()

    /**
 * %en
 * Event triggered at the end of each server tick.
 *
 * %zh
 * 当每个服务器 tick 结束时触发。
 */
    val onEndServerTick = createUnit<ServerTickArg>()

    /**
 * %en
 * Event triggered at the start of each world/level tick.
 *
 * %zh
 * 当每个世界/维度 tick 开始时触发。
 */
    val onStartWorldTick = createUnit<WorldTickArg>()

    /**
 * %en
 * Event triggered at the end of each world/level tick.
 *
 * %zh
 * 当每个世界/维度 tick 结束时触发。
 */
    val onEndWorldTick = createUnit<WorldTickArg>()
}
