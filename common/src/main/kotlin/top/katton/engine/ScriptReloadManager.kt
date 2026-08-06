package top.katton.engine

import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import top.katton.Katton
import top.katton.api.ClientPhase
import top.katton.api.InvocationReason
import top.katton.api.ReloadCause
import top.katton.api.ServerPhase
import top.katton.api.clearClientPostEffects
import top.katton.api.clearClientRenderers
import top.katton.api.mod.clearItemModifications
import top.katton.api.event.managed.clearManagedByScopeAndEnvironment
import top.katton.api.event.managed.clearManagedByOwnerPrefix
import top.katton.client.ReloadProgressState
import top.katton.client.ReloadProgressTracker
import top.katton.client.ScriptPackResourceManager
import top.katton.datapack.ServerDatapackManager
import top.katton.datapack.ScriptPackDataManager
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackManager
import top.katton.pack.ScriptPackScope
import top.katton.pack.ServerPackCacheManager
import top.katton.network.ServerNetworking
import top.katton.registry.KattonRegistry
import top.katton.registry.ScriptCommandRegistry
import top.katton.util.Event
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object ScriptReloadManager {
    private val logger: Logger = LoggerFactory.getLogger(ScriptReloadManager::class.java)

    private val clientReloadExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Katton-ClientReload").also { it.isDaemon = true }
    }

    private val clientReloadRunning = AtomicBoolean(false)
    private val serverReloadRunning = AtomicBoolean(false)
    private val globalClientInitialized = AtomicBoolean(false)

    @Volatile
    private var clientJoinedDispatchPending = false

    @Volatile
    private var globalReadyServer: MinecraftServer? = null

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
        return reloadClientScripts(InvocationReason.HOT_RELOAD, ReloadCause.COMMAND)
    }

    @JvmStatic
    fun reloadClientScripts(reason: InvocationReason, cause: ReloadCause): Boolean {
        if (!Katton.hasClient) {
            return true
        }
        //simple progress bar
        //seems straightforward and brutal, but it works well enough for now.
        //it's making the code a bit messy, but... just consider it as an alternative comment (?
        val tracker = ReloadProgressTracker(17)
        tracker.begin("katton.reload.client.begin")

        // Client and integrated-server registrations share one JVM, so only clear the client-owned entries.
        runOnClientThreadAndWait {
            for (scope in CLIENT_RELOAD_SCOPES) {
                Event.clearHandlersByScopeAndEnvironment(scope, ScriptEnvironment.CLIENT)
                clearManagedByScopeAndEnvironment(scope, ScriptEnvironment.CLIENT)
                InjectionManager.beginReload(scope, ScriptEnvironment.CLIENT)
            }
            tracker.step("katton.reload.client.clear_world_handlers")
            tracker.step("katton.reload.common.reset_injections")
            clearClientRenderers()
            tracker.step("katton.reload.client.clear_renderers")
            clearClientPostEffects()
            tracker.step("katton.reload.client.clear_post_effects")
            clearItemModifications()
            tracker.step("katton.reload.common.clear_item_modifications")
            KattonRegistry.ENTITY_RENDERERS.beginReload()
            tracker.step("katton.reload.common.reset_entity_renderers")
        }

        //set world and game directories for script packs
        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        tracker.step("katton.reload.common.set_game_directory")
        if (Katton.server != null) {
            ScriptPackManager.setWorldDirectory(Katton.server!!.getWorldPath(LevelResource.ROOT))
            ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        } else {
            ScriptPackManager.clearWorldDirectory()
        }
        tracker.step("katton.reload.common.set_world_directory")

        //scan and collect world script packs and resources
        ScriptPackManager.refreshWorldPacks()
        tracker.step("katton.reload.common.scan_world_packs")
        val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
        tracker.step("katton.reload.common.collect_world_packs")
        val mergedPacks = mutableListOf<ScriptPack>().apply {
            addAll(worldOnlyPacks)
            addAll(ServerPackCacheManager.collectExecutablePacks())
        }
        tracker.step("katton.reload.client.merge_server_cache_packs")
        val resourcePacks = mutableListOf<ScriptPack>().apply {
            addAll(ScriptPackManager.collectExecutableGlobalPacks())
            addAll(mergedPacks)
        }

        //compile and execute all scripts, then activate any resource packs
        tracker.step("katton.reload.common.compile_execute_scripts")
        val registryInvocation = ScriptInvocation.client(ClientPhase.REGISTRY_SETUP, reason, cause)
        var scriptsOk = ScriptEngine.compileAndExecuteAll(mergedPacks, registryInvocation, tracker::update)
        val minecraft = Minecraft.getInstance()
        if (scriptsOk && minecraft.player != null && minecraft.level != null) {
            scriptsOk = ScriptEngine.compileAndExecuteAll(
                mergedPacks,
                ScriptInvocation.client(ClientPhase.JOINED, reason, cause, minecraft),
                tracker::update
            )
            clientJoinedDispatchPending = false
        } else if (scriptsOk) {
            clientJoinedDispatchPending = true
        }
        if (!scriptsOk) {
            tracker.finish("katton.reload.client.failed")
            return false
        }
        val resourcePacksChanged = ScriptPackResourceManager.activateForClient(resourcePacks)
        if (resourcePacksChanged && ScriptPackResourceManager.hasActiveResources()) {
            ScriptPackResourceManager.reloadActiveResources()
        }
        tracker.finish("katton.reload.client.finished")
        return true
    }

    /** Preflights a candidate server-cache snapshot while the current one remains active. */
    fun prepareClientPacks(candidateServerPacks: Collection<ScriptPack>): Boolean {
        val merged = mutableListOf<ScriptPack>().apply {
            addAll(ScriptPackManager.collectExecutableWorldPacks())
            addAll(candidateServerPacks)
        }
        return ScriptEngine.prepareAll(
            merged,
            ScriptInvocation.client(
                ClientPhase.REGISTRY_SETUP,
                InvocationReason.HOT_RELOAD,
                ReloadCause.SERVER_PACK_SYNC
            )
        )
    }

    /**
     * Reload client scripts asynchronously without a completion callback.
     */
    @JvmStatic
    fun reloadClientScriptsAsync(): Boolean {
        return reloadClientScriptsAsync(InvocationReason.HOT_RELOAD, ReloadCause.COMMAND, null)
    }

    /**
     * Reload client scripts asynchronously
     *
     * @param onComplete callback invoked on the client thread after reload finishes
     */
    fun reloadClientScriptsAsync(onComplete: ((Boolean) -> Unit)?): Boolean =
        reloadClientScriptsAsync(InvocationReason.HOT_RELOAD, ReloadCause.COMMAND, onComplete)

    @JvmStatic
    fun reloadClientScriptsAsync(
        reason: InvocationReason,
        cause: ReloadCause,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (!clientReloadRunning.compareAndSet(false, true)) {
            val future = clientReloadFuture
            if (future != null) {
                future.whenComplete { _, _ -> reloadClientScriptsAsync(reason, cause, onComplete) }
            } else {
                onComplete?.invoke(false)
            }
            return true
        }
        val future = CompletableFuture<Void>()
        clientReloadFuture = future
        if (onComplete != null) {
            future.whenComplete { _, error -> onComplete(error == null) }
        }
        clientReloadExecutor.execute {
            var failure: Throwable? = null
            try {
                if (!reloadClientScripts(reason, cause)) {
                    failure = IllegalStateException("Client script reload failed")
                }
            } catch (t: Throwable) {
                failure = t
                logger.error("Failed to reload client scripts asynchronously", t)
                ReloadProgressState.finish("katton.reload.client.failed")
            } finally {
                clientReloadRunning.set(false)
            }
            failure?.let(future::completeExceptionally) ?: future.complete(null)
        }
        return true
    }

    /**
     * Run a task on the Minecraft client thread and block current thread until it completes.
     */
    private fun runOnClientThreadAndWait(action: () -> Unit) {
        val minecraft = runCatching { Minecraft.getInstance() }.getOrNull()
        if (minecraft == null || minecraft.isSameThread) {
            action()
            return
        }

        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        minecraft.execute {
            try {
                action()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }

        try {
            latch.await()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while waiting for client main-thread reload setup", interrupted)
        }

        failure?.let { throw it }
    }

    @JvmStatic
    fun isClientReloadRunning(): Boolean = clientReloadRunning.get()

    /** Executes GLOBAL client READY entrypoints exactly once per client process. */
    @JvmStatic
    fun initializeGlobalClientPacks() {
        if (!Katton.hasClient || !globalClientInitialized.compareAndSet(false, true)) return
        ScriptPackManager.refreshGlobalPacks()
        val packs = ScriptPackManager.collectExecutableGlobalPacks()
        if (packs.isEmpty()) return
        val ok = ScriptEngine.compileAndExecuteAll(
            packs,
            ScriptInvocation.client(ClientPhase.READY, InvocationReason.INITIAL_LOAD, ReloadCause.CLIENT_JOIN)
        )
        if (!ok) {
            globalClientInitialized.set(false)
            logger.error("Failed to initialize global client script packs")
        }
    }

    /** Requests JOINED dispatch once Minecraft exposes a player and client level. */
    @JvmStatic
    fun requestClientJoinedDispatch() {
        clientJoinedDispatchPending = true
    }

    /** Called from the platform client tick to finish a deferred JOINED phase. */
    @JvmStatic
    fun tickClientLifecycle() {
        initializeGlobalClientPacks()
        if (!clientJoinedDispatchPending || clientReloadRunning.get()) return
        val minecraft = Minecraft.getInstance()
        if (minecraft.player == null || minecraft.level == null) return
        clientJoinedDispatchPending = false
        val packs = mutableListOf<ScriptPack>().apply {
            addAll(ScriptPackManager.collectExecutableWorldPacks())
            addAll(ServerPackCacheManager.collectExecutablePacks())
        }
        if (packs.isNotEmpty()) {
            val ok = ScriptEngine.compileAndExecuteAll(
                packs,
                ScriptInvocation.client(
                    ClientPhase.JOINED,
                    InvocationReason.INITIAL_LOAD,
                    ReloadCause.CLIENT_JOIN,
                    minecraft
                )
            )
            if (!ok) logger.error("Failed to execute client JOINED entrypoints")
        }
    }

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
            val ok = ScriptEngine.compileAndExecuteAll(
                globalPacks,
                ScriptInvocation.server(
                    ServerPhase.BOOTSTRAP,
                    InvocationReason.INITIAL_LOAD,
                    ReloadCause.SERVER_START
                )
            )
            if (!ok) {
                logger.error("Failed to initialize global script packs")
            }
        }
    }

    /** Executes GLOBAL server READY entrypoints once for the supplied server lifecycle. */
    @JvmStatic
    fun initializeGlobalReadyPacks(server: MinecraftServer): Boolean {
        if (globalReadyServer === server) return true
        val packs = ScriptPackManager.collectExecutableGlobalPacks()
        val ok = packs.isEmpty() || ScriptEngine.compileAndExecuteAll(
            packs,
            ScriptInvocation.server(
                ServerPhase.READY,
                InvocationReason.INITIAL_LOAD,
                ReloadCause.SERVER_START,
                server
            )
        )
        if (ok) globalReadyServer = server
        return ok
    }

    @JvmStatic
    fun resetServerLifecycle(server: MinecraftServer?) {
        if (server == null || globalReadyServer === server) {
            val ownerPrefix = "${ScriptPackScope.GLOBAL.serializedName}:${ServerPhase.READY.name}:"
            Event.clearHandlersByOwnerPrefix(ownerPrefix)
            clearManagedByOwnerPrefix(ownerPrefix)
            InjectionManager.rollbackByOwnerPrefix(ownerPrefix)
            ServerNetworking.resetPackRevisions()
            globalReadyServer = null
        }
    }

    /**
     * Reloads all server-side world scripts
     */
    @JvmStatic
    fun reloadScripts(server: MinecraftServer?): Boolean {
        return reloadScripts(server, InvocationReason.HOT_RELOAD, ReloadCause.COMMAND)
    }

    @JvmStatic
    fun reloadScripts(
        server: MinecraftServer?,
        reason: InvocationReason,
        cause: ReloadCause
    ): Boolean {
        if (server == null) {
            return false
        }

        val tracker = ReloadProgressTracker(24)
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
        Event.clearHandlersByScopeAndEnvironment(ScriptPackScope.WORLD, ScriptEnvironment.SERVER)
        tracker.step("katton.reload.server.clear_event_handlers")
        clearManagedByScopeAndEnvironment(ScriptPackScope.WORLD, ScriptEnvironment.SERVER)
        tracker.step("katton.reload.server.clear_managed_event_listeners")
        InjectionManager.beginReload(ScriptPackScope.WORLD, ScriptEnvironment.SERVER)
        tracker.step("katton.reload.common.reset_injections")

        val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
        tracker.step("katton.reload.common.collect_world_packs")
        val serverDataPacks = mutableListOf<ScriptPack>().apply {
            addAll(ScriptPackManager.collectExecutableGlobalPacks())
            addAll(worldOnlyPacks)
        }
        tracker.step("katton.reload.server.collect_data_packs")
        tracker.step("katton.reload.common.compile_execute_scripts")
        val scriptsOk = ScriptEngine.compileAndExecuteAll(
            worldOnlyPacks,
            ScriptInvocation.server(ServerPhase.READY, reason, cause, server),
            tracker::update
        )
        if (!scriptsOk) {
            tracker.finish("katton.reload.server.failed")
            return false
        }
        val dataPacksChanged = ScriptPackDataManager.activateForServer(server, serverDataPacks)
        tracker.step("katton.reload.server.mount_script_data")
        if (dataPacksChanged && !ScriptPackDataManager.reloadServerResources(server)) {
            tracker.finish("katton.reload.server.failed")
            return false
        }
        tracker.step("katton.reload.server.reload_script_data")
        ServerDatapackManager.apply(server)
        tracker.step("katton.reload.server.apply_datapacks")
        tracker.finish("katton.reload.server.finished")
        return true
    }

    /**
     * Async entry point for [reloadScripts].
     *
     * The request returns immediately, but the actual reload is scheduled on
     * the server thread because script entrypoints can mutate Minecraft
     * registries, command trees, events, and datapacks.
     *
     * @param server the server instance
     * @param onComplete callback invoked on the server thread after reload finishes
     */
    @JvmStatic
    fun reloadScriptsAsync(server: MinecraftServer, onComplete: (Boolean) -> Unit) {
        reloadScriptsAsync(server, InvocationReason.HOT_RELOAD, ReloadCause.COMMAND, onComplete)
    }

    @JvmStatic
    fun reloadScriptsAsync(
        server: MinecraftServer,
        reason: InvocationReason,
        cause: ReloadCause,
        onComplete: (Boolean) -> Unit
    ) {
        if (!serverReloadRunning.compareAndSet(false, true)) {
            onComplete(false)
            return
        }
        val future = CompletableFuture<Void>()
        serverReloadFuture = future

        // Registry, event, command, and datapack mutations must stay on the server thread.
        server.execute {
            try {
                if (reason == InvocationReason.INITIAL_LOAD && !initializeGlobalReadyPacks(server)) {
                    throw IllegalStateException("Global server READY entrypoints failed")
                }
                val ok = reloadScripts(server, reason, cause)
                if (ok) {
                    if (Katton.hasClient) {
                        ServerNetworking.publishPackRevision(server)
                        if (!server.isDedicatedServer && reason == InvocationReason.HOT_RELOAD) {
                            reloadClientScriptsAsync(
                                InvocationReason.HOT_RELOAD,
                                ReloadCause.SERVER_PACK_SYNC,
                                null
                            )
                        }
                    }
                    future.complete(null)
                } else {
                    future.completeExceptionally(IllegalStateException("Server script reload failed"))
                }
                onComplete(ok)
            } catch (t: Throwable) {
                logger.error("Failed to reload server scripts", t)
                ReloadProgressState.finish("katton.reload.server.failed")
                future.completeExceptionally(t)
                onComplete(false)
            } finally {
                serverReloadRunning.set(false)
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

    private val CLIENT_RELOAD_SCOPES = arrayOf(
        ScriptPackScope.WORLD,
        ScriptPackScope.SERVER_CACHE
    )
}
