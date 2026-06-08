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
import top.katton.network.ServerItemRenderMarkerManager
import top.katton.util.DelegateEvent
import top.katton.util.createUnit

/**
 * %en
 * Server lifecycle events for NeoForge platform.
 *
 * This object provides events related to server lifecycle including
 * server start/stop, datapack sync, level load/unload, and tick events.
 *
 * %zh
 * NeoForge 平台的服务端生命周期事件。
 * 此对象提供与服务端生命周期相关的事件，包括服务端启动/停止、数据包同步、维度加载/卸载和 tick 事件。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerEvent {

    @JvmStatic
    @SubscribeEvent
    private fun handleServerStarting(e: ServerStartingEvent) {
        onServerStarting(ServerArg(e.server))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleServerStarted(e: ServerStartedEvent) {
        onServerStarted(ServerArg(e.server))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleServerStopped(e: ServerStoppedEvent) {
        onServerStopped(ServerArg(e.server))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleServerStopping(e: ServerStoppingEvent) {
        onServerStopping(ServerArg(e.server))
    }

    @JvmStatic
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

    @JvmStatic
    @SubscribeEvent
    private fun handleStartServerTick(e: ServerTickEvent.Pre) {
        onStartServerTick(ServerTickArg(e.server))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleEndServerTick(e: ServerTickEvent.Post) {
        ServerItemRenderMarkerManager.tick()
        onEndServerTick(ServerTickArg(e.server))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleStartWorldTick(e: LevelTickEvent.Pre) {
        if (e.level is ServerLevel) {
            onStartWorldTick(WorldTickArg(e.level as ServerLevel))
        }
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleEndWorldTick(e: LevelTickEvent.Post) {
        if (e.level is ServerLevel) {
            onEndWorldTick(WorldTickArg(e.level as ServerLevel))
        }
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleLevelLoad(e: LevelEvent.Load) {
        val level = e.level as? ServerLevel ?: return
        onLevelLoad(ServerLevelArg(level))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleLevelUnload(e: LevelEvent.Unload) {
        val level = e.level as? ServerLevel ?: return
        onLevelUnload(ServerLevelArg(level))
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleLevelSave(e: LevelEvent.Save) {
        val level = e.level as? ServerLevel ?: return
        onLevelSave(ServerLevelArg(level))
    }

    /**
     * %en
     * Event triggered when the server is starting (before worlds are loaded).
     *
     * %zh
     * 当服务端开始启动、维度加载前触发。
     */
    val onServerStarting = createUnit<ServerArg>()

    /**
     * %en
     * Event triggered when the server has started (after worlds are loaded).
     *
     * %zh
     * 当服务端启动完成、维度加载后触发。
     */
    val onServerStarted = createUnit<ServerArg>()

    /**
     * %en
     * Event triggered when the server has stopped.
     *
     * %zh
     * 当服务端停止时触发。
     */
    val onServerStopped = createUnit<ServerArg>()

    /**
     * %en
     * Event triggered when the server is stopping.
     *
     * %zh
     * 当服务端正在停止时触发。
     */
    val onServerStopping = createUnit<ServerArg>()

    /**
     * %en
     * Event triggered when datapack contents are being synced to players.
     *
     * %zh
     * 当向玩家同步数据包内容时触发。
     */
    @JvmField
    val onSyncDatapackContents = createUnit<SyncDatapackContentsArg>()

    /**
     * %en
     * Event triggered when a datapack reload is starting.
     * Note: NeoForge doesn't have a direct equivalent; this is a placeholder.
     *
     * %zh
     * 当数据包重载开始时触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onStartDatapackReload = createUnit<StartDatapackReloadArg>()

    /**
     * %en
     * Event triggered when a datapack reload has completed.
     * Note: NeoForge doesn't have a direct equivalent; this is a placeholder.
     *
     * %zh
     * 当数据包重载完成时触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onEndDatapackReload = createUnit<EndDatapackReloadArg>()

    /**
     * %en
     * Event triggered before the server saves data.
     * Note: NeoForge doesn't have a direct equivalent; this is a placeholder.
     *
     * %zh
     * 在服务端保存数据之前触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onBeforeSave = createUnit<ServerSaveArg>()

    /**
     * %en
     * Event triggered after the server has saved data.
     * Note: NeoForge doesn't have a direct equivalent; this is a placeholder.
     *
     * %zh
     * 在服务端保存数据之后触发。
     * 这是为了 NeoForge API 兼容性保留的占位事件。
     */
    @JvmField
    val onAfterSave = createUnit<ServerSaveArg>()

    /**
     * %en
     * Event triggered at the start of each server tick.
     *
     * %zh
     * 每个服务端 tick 开始时触发。
     */
    val onStartServerTick = createUnit<ServerTickArg>()

    /**
     * %en
     * Event triggered at the end of each server tick.
     *
     * %zh
     * 每个服务端 tick 结束时触发。
     */
    val onEndServerTick = createUnit<ServerTickArg>()

    /**
     * %en
     * Event triggered at the start of each world/level tick.
     *
     * %zh
     * 每个维度/世界 tick 开始时触发。
     */
    val onStartWorldTick = createUnit<WorldTickArg>()

    /**
     * %en
     * Event triggered at the end of each world/level tick.
     *
     * %zh
     * 每个维度/世界 tick 结束时触发。
     */
    val onEndWorldTick = createUnit<WorldTickArg>()

    /**
     * %en
     * Event triggered when a level is loaded.
     *
     * %zh
     * 当维度加载时触发。
     */
    @JvmField
    val onLevelLoad = createUnit<ServerLevelArg>()

    /**
     * %en
     * Event triggered when a level is unloaded.
     *
     * %zh
     * 当维度卸载时触发。
     */
    @JvmField
    val onLevelUnload = createUnit<ServerLevelArg>()

    /**
     * %en
     * Event triggered when a level is saved.
     *
     * %zh
     * 当维度保存时触发。
     */
    @JvmField
    val onLevelSave = createUnit<ServerLevelArg>()

    /**
     * %en
     * Wrapper class for server level arguments.
     *
     * %zh
     * 服务端维度参数封装。
     * @property level
     * %en The server level
     * %zh 服务端维度。
     */
    @JvmInline
    value class ServerLevelArg(val level: ServerLevel)
}
