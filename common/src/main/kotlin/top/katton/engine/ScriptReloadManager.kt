package top.katton.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import top.katton.Katton
import top.katton.api.clearClientPostEffects
import top.katton.api.clearClientRenderers
import top.katton.api.mod.clearItemModifications
import top.katton.api.event.managed.clearManagedByScope
import top.katton.client.ReloadProgressState
import top.katton.client.ReloadProgressTracker
import top.katton.datapack.ServerDatapackManager
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackManager
import top.katton.pack.ScriptPackScope
import top.katton.pack.ServerPackCacheManager
import top.katton.registry.KattonRegistry
import top.katton.registry.ScriptCommandRegistry
import top.katton.util.Event
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object ScriptReloadManager {
    private val logger: Logger = LoggerFactory.getLogger(ScriptReloadManager::class.java)

    private val clientReloadExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Katton-ClientReload").also { it.isDaemon = true }
    }

    private val serverReloadExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Katton-ServerReload").also { it.isDaemon = true }
    }

    private val clientReloadRunning = AtomicBoolean(false)
    private val serverReloadRunning = AtomicBoolean(false)

    @Volatile
    private var clientReloadFuture: CompletableFuture<Void>? = null

    @Volatile
    private var serverReloadFuture: CompletableFuture<Void>? = null

    /**
     * Reloads all client-side world scripts.
     * No-op on server-only platforms (Paper) where hasClient=false.
     */
    @JvmStatic
    fun reloadClientScripts(): Boolean {
        if (!Katton.hasClient) {
            return true
        }

        val tracker = ReloadProgressTracker(17)
        tracker.begin("katton.reload.client.begin")

        val preserveIntegratedServerState = Katton.server != null && !Katton.server!!.isDedicatedServer
        if (!preserveIntegratedServerState) {
            Event.clearHandlersByScope(ScriptPackScope.WORLD)
            tracker.step("katton.reload.client.clear_world_handlers")
            InjectionManager.beginReload()
            tracker.step("katton.reload.common.reset_injections")
        } else {
            tracker.step("katton.reload.client.preserve_server_handlers")
        }
        clearClientRenderers()
        tracker.step("katton.reload.client.clear_renderers")
        clearClientPostEffects()
        tracker.step("katton.reload.client.clear_post_effects")
        clearItemModifications()
        tracker.step("katton.reload.common.clear_item_modifications")
        KattonRegistry.ENTITY_RENDERERS.beginReload()
        tracker.step("katton.reload.common.reset_entity_renderers")

        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        tracker.step("katton.reload.common.set_game_directory")
        if (Katton.server != null) {
            ScriptPackManager.setWorldDirectory(Katton.server!!.getWorldPath(LevelResource.ROOT))
            ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        } else {
            ScriptPackManager.clearWorldDirectory()
        }
        tracker.step("katton.reload.common.set_world_directory")
        ScriptPackManager.refreshWorldPacks()
        tracker.step("katton.reload.common.scan_world_packs")

        val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
        tracker.step("katton.reload.common.collect_world_packs")
        val mergedPacks = mutableListOf<ScriptPack>().apply {
            addAll(worldOnlyPacks)
            addAll(ServerPackCacheManager.collectExecutablePacks())
        }
        tracker.step("katton.reload.client.merge_server_cache_packs")
        tracker.step("katton.reload.common.compile_execute_scripts")
        val scriptsOk = ScriptEngine.compileAndExecuteAll(mergedPacks, ScriptEnvironment.CLIENT, tracker::update)
        if (!scriptsOk) {
            tracker.finish("katton.reload.client.failed")
            return false
        }
        tracker.finish("katton.reload.client.finished")
        return true
    }

    @JvmStatic
    fun reloadClientScriptsAsync(): Boolean {
        if (!clientReloadRunning.compareAndSet(false, true)) {
            return true
        }
        val future = CompletableFuture<Void>()
        clientReloadFuture = future
        clientReloadExecutor.execute {
            try {
                if (reloadClientScripts()) {
                    future.complete(null)
                } else {
                    future.completeExceptionally(IllegalStateException("Client script reload failed"))
                }
            } catch (t: Throwable) {
                future.completeExceptionally(t)
                logger.error("Failed to reload client scripts asynchronously", t)
                ReloadProgressState.finish("katton.reload.client.failed")
            } finally {
                clientReloadRunning.set(false)
                // Keep clientReloadFuture set so awaitClientReloadCompletion
                // can always find a completed future to short-circuit on.
            }
        }
        return true
    }

    @JvmStatic
    fun isClientReloadRunning(): Boolean = clientReloadRunning.get()

    /**
     * Blocks the calling thread until any in-progress server reload completes.
     * Used by registry-sensitive operations (client login, config sync) to ensure
     * they see finalized registries after a script reload. The wait duration is
     * bounded by the compilation time (typically < 2 seconds).
     */
    @JvmStatic
    fun awaitServerReloadCompletion() {
        val future = serverReloadFuture ?: return
        try {
            future.get()
        } catch (_: Exception) {
        }
    }

    /**
     * Compiles and executes all GLOBAL-scoped script packs.
     * Called once during mod initialization, before the server starts.
     * Global packs are never reloaded — they persist for the entire game session.
     */
    @JvmStatic
    fun initializeGlobalPacks() {
        val globalPacks = ScriptPackManager.collectExecutableGlobalPacks()
        if (globalPacks.isNotEmpty()) {
            val ok = ScriptEngine.compileAndExecuteAll(globalPacks, ScriptEnvironment.SERVER)
            if (!ok) {
                logger.error("Failed to initialize global script packs")
            }
        }
    }

    /**
     * Reloads all server-side world scripts
     */
    @JvmStatic
    fun reloadScripts(server: MinecraftServer?): Boolean {
        if (server == null) {
            return false
        }

        val tracker = ReloadProgressTracker(22)
        tracker.begin("katton.reload.server.begin")

        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        tracker.step("katton.reload.common.set_game_directory")
        ScriptPackManager.setWorldDirectory(server.getWorldPath(LevelResource.ROOT))
        tracker.step("katton.reload.common.set_world_directory")
        ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        ScriptPackManager.refreshWorldPacks()
        tracker.step("katton.reload.common.scan_world_packs")

        ScriptCommandRegistry.beginReload(server)
        tracker.step("katton.reload.server.reset_command_registry")
        if (Katton.registrationEnabled) {
            KattonRegistry.ITEMS.beginReload()
            tracker.step("katton.reload.server.reset_item_registry")
            KattonRegistry.EFFECTS.beginReload()
            KattonRegistry.BLOCKS.beginReload()
            tracker.step("katton.reload.server.reset_effect_block_registries")
            KattonRegistry.ENTITY_TYPES.beginReload()
            tracker.step("katton.reload.server.reset_entity_type_registry")
            KattonRegistry.SOUND_EVENTS.beginReload()
            KattonRegistry.PARTICLE_TYPES.beginReload()
            tracker.step("katton.reload.server.reset_sound_particle_registries")
            KattonRegistry.BLOCK_ENTITY_TYPES.beginReload()
            tracker.step("katton.reload.server.reset_block_entity_type_registry")
            KattonRegistry.CREATIVE_TABS.beginReload()
            KattonRegistry.DATA_COMPONENT_TYPES.beginReload()
            tracker.step("katton.reload.server.reset_creative_tabs_components")
        }
        if (Katton.hasClient) {
            KattonRegistry.ENTITY_RENDERERS.beginReload()
            tracker.step("katton.reload.common.reset_entity_renderers")
        }
        ServerDatapackManager.beginReload()
        tracker.step("katton.reload.server.reset_datapack_manager")
        clearItemModifications()
        tracker.step("katton.reload.common.clear_item_modifications")
        Event.clearHandlersByScope(ScriptPackScope.WORLD)
        tracker.step("katton.reload.server.clear_event_handlers")
        clearManagedByScope(ScriptPackScope.WORLD)
        tracker.step("katton.reload.server.clear_managed_event_listeners")
        InjectionManager.beginReload()
        tracker.step("katton.reload.common.reset_injections")

        val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
        tracker.step("katton.reload.common.collect_world_packs")
        tracker.step("katton.reload.common.compile_execute_scripts")
        val scriptsOk = ScriptEngine.compileAndExecuteAll(worldOnlyPacks, ScriptEnvironment.SERVER, tracker::update)
        if (!scriptsOk) {
            tracker.finish("katton.reload.server.failed")
            return false
        }
        ServerDatapackManager.apply(server)
        tracker.step("katton.reload.server.apply_datapacks")
        tracker.finish("katton.reload.server.finished")
        return true
    }

    /**
     * Async variant of [reloadScripts]: runs compilation and execution on a
     * background thread so the server main loop is not blocked.
     *
     * Fast setup (registries, handlers) and post-compilation steps
     * (datapacks, entity rebinding) still run on the server thread via
     * [MinecraftServer.execute].
     *
     * @param server the server instance
     * @param onComplete callback invoked on the server thread after reload finishes
     */
    @JvmStatic
    fun reloadScriptsAsync(server: MinecraftServer, onComplete: (Boolean) -> Unit) {
        if (!serverReloadRunning.compareAndSet(false, true)) {
            onComplete(false)
            return
        }
        val future = CompletableFuture<Void>()
        serverReloadFuture = future

        // Run ALL reload work on background thread — the calling thread
        // (server command thread) returns immediately without blocking.
        serverReloadExecutor.execute reloadTask@{
            val tracker = ReloadProgressTracker(22)
            tracker.begin("katton.reload.server.begin")

            try {
                // Fast setup
                ScriptPackManager.setGameDirectory(Katton.gameDirectory)
                tracker.step("katton.reload.common.set_game_directory")
                ScriptPackManager.setWorldDirectory(server.getWorldPath(LevelResource.ROOT))
                tracker.step("katton.reload.common.set_world_directory")
                ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
                ScriptPackManager.refreshWorldPacks()
                tracker.step("katton.reload.common.scan_world_packs")

                ScriptCommandRegistry.beginReload(server)
                tracker.step("katton.reload.server.reset_command_registry")
                if (Katton.registrationEnabled) {
                    KattonRegistry.ITEMS.beginReload()
                    tracker.step("katton.reload.server.reset_item_registry")
                    KattonRegistry.EFFECTS.beginReload()
                    KattonRegistry.BLOCKS.beginReload()
                    tracker.step("katton.reload.server.reset_effect_block_registries")
                    KattonRegistry.ENTITY_TYPES.beginReload()
                    tracker.step("katton.reload.server.reset_entity_type_registry")
                    KattonRegistry.SOUND_EVENTS.beginReload()
                    KattonRegistry.PARTICLE_TYPES.beginReload()
                    tracker.step("katton.reload.server.reset_sound_particle_registries")
                    KattonRegistry.BLOCK_ENTITY_TYPES.beginReload()
                    tracker.step("katton.reload.server.reset_block_entity_type_registry")
                    KattonRegistry.CREATIVE_TABS.beginReload()
                    KattonRegistry.DATA_COMPONENT_TYPES.beginReload()
                    tracker.step("katton.reload.server.reset_creative_tabs_components")
                }
                if (Katton.hasClient) {
                    KattonRegistry.ENTITY_RENDERERS.beginReload()
                    tracker.step("katton.reload.common.reset_entity_renderers")
                }
                ServerDatapackManager.beginReload()
                tracker.step("katton.reload.server.reset_datapack_manager")
                clearItemModifications()
                tracker.step("katton.reload.common.clear_item_modifications")
                Event.clearHandlersByScope(ScriptPackScope.WORLD)
                tracker.step("katton.reload.server.clear_event_handlers")
                clearManagedByScope(ScriptPackScope.WORLD)
                tracker.step("katton.reload.server.clear_managed_event_listeners")
                InjectionManager.beginReload()
                tracker.step("katton.reload.common.reset_injections")

                val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
                tracker.step("katton.reload.common.collect_world_packs")

                // Heavy compilation + execution
                tracker.step("katton.reload.common.compile_execute_scripts")
                val scriptsOk = ScriptEngine.compileAndExecuteAll(worldOnlyPacks, ScriptEnvironment.SERVER, tracker::update)
                if (!scriptsOk) {
                    val reloadFuture = future
                    server.execute {
                        ReloadProgressState.finish("katton.reload.server.failed")
                        reloadFuture.completeExceptionally(IllegalStateException("Server script reload failed"))
                        onComplete(false)
                        serverReloadRunning.set(false)
                    }
                    return@reloadTask
                }

                // Post-compilation must run on server thread (registry mutations).
                val reloadFuture = future
                server.execute {
                    try {
                        ServerDatapackManager.apply(server)
                        tracker.step("katton.reload.server.apply_datapacks")
                        tracker.finish("katton.reload.server.finished")
                        reloadFuture.complete(null)
                        onComplete(true)
                    } catch (t: Throwable) {
                        logger.error("Failed to apply datapacks after async server reload", t)
                        ReloadProgressState.finish("katton.reload.server.failed")
                        reloadFuture.completeExceptionally(t)
                        onComplete(false)
                    } finally {
                        serverReloadRunning.set(false)
                    }
                }
            } catch (t: Throwable) {
                logger.error("Failed to compile server scripts asynchronously", t)
                val reloadFuture = future
                server.execute {
                    ReloadProgressState.finish("katton.reload.server.failed")
                    reloadFuture.completeExceptionally(t)
                    onComplete(false)
                    serverReloadRunning.set(false)
                }
            }
        }
    }

    private fun ensureDirectory(path: Path?) {
        if (path == null) return
        try {
            Files.createDirectories(path)
        } catch (_: Exception) {
        }
    }
}
