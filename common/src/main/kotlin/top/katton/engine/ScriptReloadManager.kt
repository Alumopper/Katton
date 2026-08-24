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
import top.katton.api.mod.restoreItemComponents
import top.katton.api.mod.snapshotItemComponents
import top.katton.api.event.managed.clearManagedByScopeAndEnvironment
import top.katton.api.event.managed.clearManagedByOwnerPrefix
import top.katton.client.ReloadProgressState
import top.katton.client.ReloadProgressTracker
import top.katton.client.ScriptPackResourceManager
import top.katton.config.KattonConfigManager
import top.katton.datapack.ServerDatapackManager
import top.katton.datapack.ScriptPackDataManager
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackDependencyGraph
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object ScriptReloadManager {
    private val logger: Logger = LoggerFactory.getLogger(ScriptReloadManager::class.java)
    private const val INTERNAL_WAIT_TIMEOUT_SECONDS = 60L
    private const val SERVER_RELOAD_WAIT_TIMEOUT_SECONDS = 120L

    private data class PendingClientReload(
        var reason: InvocationReason,
        var cause: ReloadCause,
        val callbacks: MutableList<(Boolean) -> Unit>
    )

    private data class ServerReloadRequest(
        var server: MinecraftServer,
        var reason: InvocationReason,
        var cause: ReloadCause,
        val callbacks: MutableList<(Boolean) -> Unit>,
        val future: CompletableFuture<Void> = CompletableFuture()
    )

    private val clientReloadExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Katton-ClientReload").also { it.isDaemon = true }
    }

    private val clientReloadRunning = AtomicBoolean(false)
    private val serverReloadRunning = AtomicBoolean(false)
    private val globalClientInitialized = AtomicBoolean(false)
    private val clientReloadQueueLock = Any()
    private val serverReloadStateLock = Any()

    /** Requests arriving during a reload are collapsed into one follow-up pass. */
    private var pendingClientReload: PendingClientReload? = null

    /** Server mutations stay serialized; only the latest queued state needs another pass. */
    private var pendingServerReload: ServerReloadRequest? = null

    @Volatile
    private var clientJoinedDispatchPending = false

    @Volatile
    private var globalReadyServer: MinecraftServer? = null

    @Volatile
    private var serverReloadFuture: CompletableFuture<Void>? = null

    /** Last client snapshot whose entrypoints and resources both activated successfully. */
    @Volatile
    private var activeClientPacks: List<ScriptPack> = emptyList()

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

        // Keep the last-known-good snapshot live until the candidate compiles.
        val previousPacks = activeClientPacks.toList()
        val previousItemComponents = snapshotItemComponents()

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
        val candidateWorldPacks = ScriptPackManager.scanWorldPacksCandidate()
        val worldOnlyPacks = candidateWorldPacks.filter { it.enabled }
        tracker.step("katton.reload.common.scan_world_packs")
        tracker.step("katton.reload.common.collect_world_packs")
        val mergedPacks = mutableListOf<ScriptPack>().apply {
            addAll(worldOnlyPacks)
            addAll(ServerPackCacheManager.collectExecutablePacks())
        }
        tracker.step("katton.reload.client.merge_server_cache_packs")
        //compile and execute all scripts, then activate any resource packs
        tracker.step("katton.reload.common.compile_execute_scripts")
        val registryInvocation = ScriptInvocation.client(ClientPhase.REGISTRY_SETUP, reason, cause)
        val prepared = ScriptEngine.prepareWithFallback(mergedPacks, previousPacks, registryInvocation)
        if (prepared == null) {
            tracker.finish("katton.reload.client.failed")
            return false
        }
        val effectivePacks = prepared.packs

        val minecraft = Minecraft.getInstance()
        var firstReset = true
        val activatedPacks = executePreparedGroupsWithFallback(
            initialPacks = effectivePacks,
            previousPacks = previousPacks,
            invocation = registryInvocation,
            resetRuntime = {
                resetClientRuntime(if (firstReset) tracker else null)
                check(restoreItemComponents(previousItemComponents)) {
                    "Failed to restore item components before client script activation"
                }
                firstReset = false
            }
        ) { group ->
            var groupOk = ScriptEngine.compileAndExecuteAll(group, registryInvocation, tracker::update)
            if (groupOk && minecraft.player != null && minecraft.level != null) {
                groupOk = ScriptEngine.compileAndExecuteAll(
                    group,
                    ScriptInvocation.client(ClientPhase.JOINED, reason, cause, minecraft),
                    tracker::update
                )
                if (groupOk) clientJoinedDispatchPending = false
            } else if (groupOk) {
                clientJoinedDispatchPending = true
            }
            groupOk
        }
        if (activatedPacks == null) {
            restoreClientRuntime(previousPacks, previousItemComponents, reason, cause)
            tracker.finish("katton.reload.client.failed")
            return false
        }
        val activatedResourcePacks = mutableListOf<ScriptPack>().apply {
            addAll(ScriptPackManager.collectExecutableGlobalPacks())
            addAll(activatedPacks)
        }
        if (!ScriptPackResourceManager.activateAndReload(activatedResourcePacks)) {
            restoreClientRuntime(previousPacks, previousItemComponents, reason, cause)
            tracker.finish("katton.reload.client.failed")
            return false
        }
        ScriptPackManager.publishWorldPacks(
            candidateWorldPacks,
            activatedPacks.filter { it.scope == ScriptPackScope.WORLD }
        )
        KattonConfigManager.retainPacks(
            setOf(ScriptPackScope.WORLD, ScriptPackScope.SERVER_CACHE),
            activatedPacks.mapTo(hashSetOf(), KattonConfigManager::configId)
        )
        activeClientPacks = activatedPacks.toList()
        tracker.finish("katton.reload.client.finished")
        return true
    }

    private fun resetClientRuntime(tracker: ReloadProgressTracker? = null) {
        // Client and integrated-server registrations share one JVM, so only clear client-owned entries.
        runOnClientThreadAndWait {
            for (scope in CLIENT_RELOAD_SCOPES) {
                Event.clearHandlersByScopeAndEnvironment(scope, ScriptEnvironment.CLIENT)
                clearManagedByScopeAndEnvironment(scope, ScriptEnvironment.CLIENT)
                InjectionManager.beginReload(scope, ScriptEnvironment.CLIENT)
            }
            tracker?.step("katton.reload.client.clear_world_handlers")
            tracker?.step("katton.reload.common.reset_injections")
            clearClientRenderers()
            tracker?.step("katton.reload.client.clear_renderers")
            clearClientPostEffects()
            tracker?.step("katton.reload.client.clear_post_effects")
            clearItemModifications()
            tracker?.step("katton.reload.common.clear_item_modifications")
            KattonRegistry.ENTITY_RENDERERS.beginReload()
            tracker?.step("katton.reload.common.reset_entity_renderers")
        }
    }

    private fun restoreClientRuntime(
        previousPacks: List<ScriptPack>,
        previousItemComponents: Map<net.minecraft.world.item.Item, net.minecraft.core.component.DataComponentMap>,
        reason: InvocationReason,
        cause: ReloadCause
    ) {
        logger.warn("Restoring the previous client script snapshot after a rejected reload")
        resetClientRuntime()
        var restored = restoreItemComponents(previousItemComponents)
        val registryInvocation = ScriptInvocation.client(ClientPhase.REGISTRY_SETUP, reason, cause)
        val minecraft = Minecraft.getInstance()
        ScriptPackDependencyGraph.compilationGroups(previousPacks).forEach { group ->
            var groupRestored = ScriptEngine.compileAndExecuteAll(group, registryInvocation)
            if (groupRestored && minecraft.player != null && minecraft.level != null) {
                groupRestored = ScriptEngine.compileAndExecuteAll(
                    group,
                    ScriptInvocation.client(ClientPhase.JOINED, reason, cause, minecraft)
                )
            }
            restored = groupRestored && restored
        }
        clientJoinedDispatchPending = restored && (minecraft.player == null || minecraft.level == null)
        if (!restored) logger.error("Failed to restore the previous client script snapshot")
        KattonConfigManager.retainPacks(
            setOf(ScriptPackScope.WORLD, ScriptPackScope.SERVER_CACHE),
            previousPacks.mapTo(hashSetOf(), KattonConfigManager::configId)
        )
    }

    /** Preflights a candidate server-cache snapshot while the current one remains active. */
    fun prepareClientPacks(candidateServerPacks: Collection<ScriptPack>): Boolean {
        val merged = mutableListOf<ScriptPack>().apply {
            addAll(ScriptPackManager.collectExecutableWorldPacks())
            addAll(candidateServerPacks)
        }
        return ScriptEngine.prepareWithFallback(
            merged,
            activeClientPacks,
            ScriptInvocation.client(
                ClientPhase.REGISTRY_SETUP,
                InvocationReason.HOT_RELOAD,
                ReloadCause.SERVER_PACK_SYNC
            )
        ) != null
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
            enqueuePendingClientReload(reason, cause, onComplete)
            return true
        }
        startClientReload(reason, cause, listOfNotNull(onComplete))
        return true
    }

    private fun startClientReload(
        reason: InvocationReason,
        cause: ReloadCause,
        callbacks: List<(Boolean) -> Unit>
    ) {
        try {
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
                }
                val success = failure == null
                try {
                    dispatchClientReloadCallbacks(callbacks, success)
                } finally {
                    // Callbacks publish or roll back the pack snapshot. Retain the
                    // running slot through that transition so an external request
                    // cannot enter the executor ahead of a callback-scheduled rollback.
                    clientReloadRunning.set(false)
                    startPendingClientReloadIfPresent()
                }
            }
        } catch (rejected: RuntimeException) {
            logger.error("Client reload executor rejected a reload", rejected)
            try {
                dispatchClientReloadCallbacks(callbacks, false)
            } finally {
                clientReloadRunning.set(false)
                startPendingClientReloadIfPresent()
            }
        }
    }

    private fun enqueuePendingClientReload(
        reason: InvocationReason,
        cause: ReloadCause,
        callback: ((Boolean) -> Unit)?
    ) {
        synchronized(clientReloadQueueLock) {
            val pending = pendingClientReload
            if (pending == null) {
                pendingClientReload = PendingClientReload(reason, cause, listOfNotNull(callback).toMutableList())
            } else {
                // The latest cause describes the state that the follow-up pass will observe.
                pending.reason = reason
                pending.cause = cause
                callback?.let(pending.callbacks::add)
            }
        }
    }

    private fun startPendingClientReloadIfPresent() {
        val pending = synchronized(clientReloadQueueLock) {
            pendingClientReload.also { pendingClientReload = null }
        } ?: return

        if (clientReloadRunning.compareAndSet(false, true)) {
            startClientReload(pending.reason, pending.cause, pending.callbacks)
            return
        }

        // Another caller won the transition between the completed pass and this
        // drain. Merge our callbacks back into its single queued follow-up pass.
        synchronized(clientReloadQueueLock) {
            val queued = pendingClientReload
            if (queued == null) {
                pendingClientReload = pending
            } else {
                queued.reason = pending.reason
                queued.cause = pending.cause
                queued.callbacks += pending.callbacks
            }
        }
    }

    private fun dispatchClientReloadCallbacks(callbacks: List<(Boolean) -> Unit>, success: Boolean) {
        if (callbacks.isEmpty()) return
        val action: () -> Unit = {
            callbacks.forEach { callback ->
                runCatching { callback(success) }
                    .onFailure { logger.error("Client script reload completion callback failed", it) }
            }
        }
        // Completion callbacks publish or roll back script-pack state. Wait for
        // their small main-thread transition before draining the next reload.
        // Dispatching the coalesced batch together also avoids one queue/latch
        // round trip per caller when many reload requests arrive at once.
        runCatching { runOnClientThreadAndWait(action) }
            .onFailure {
                logger.warn("Client executor rejected reload callback; running it on the reload thread", it)
                action()
            }
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
        // 0 = queued, 1 = running, 2 = cancelled before start, 3 = finished.
        // A timeout may cancel a queued action, but must never return while an
        // already-running main-thread mutation is still touching reload state.
        val executionState = AtomicInteger(0)
        var failure: Throwable? = null
        try {
            minecraft.execute {
                if (!executionState.compareAndSet(0, 1)) {
                    latch.countDown()
                    return@execute
                }
                try {
                    action()
                } catch (t: Throwable) {
                    failure = t
                } finally {
                    executionState.set(3)
                    latch.countDown()
                }
            }
        } catch (rejected: RuntimeException) {
            executionState.compareAndSet(0, 2)
            throw IllegalStateException("Client executor rejected reload setup", rejected)
        }

        try {
            if (!latch.await(INTERNAL_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                if (executionState.compareAndSet(0, 2)) {
                    throw IllegalStateException(
                        "Timed out waiting for client main-thread reload setup after $INTERNAL_WAIT_TIMEOUT_SECONDS seconds"
                    )
                }
                // The action crossed from queued to running at the timeout
                // boundary. Wait for it so cleanup cannot race its mutations.
                latch.await()
            }
        } catch (interrupted: InterruptedException) {
            val cancelledBeforeStart = executionState.compareAndSet(0, 2)
            if (!cancelledBeforeStart) awaitUninterruptibly(latch)
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while waiting for client main-thread reload setup", interrupted)
        }

        failure?.let { throw it }
    }

    private fun awaitUninterruptibly(latch: CountDownLatch) {
        var interrupted = false
        while (true) {
            try {
                latch.await()
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    @JvmStatic
    fun isClientReloadRunning(): Boolean = clientReloadRunning.get()

    @JvmStatic
    fun isServerReloadRunning(): Boolean = serverReloadRunning.get()

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
        val packs = activeClientPacks
        if (packs.isNotEmpty()) {
            var ok = true
            val invocation = ScriptInvocation.client(
                    ClientPhase.JOINED,
                    InvocationReason.INITIAL_LOAD,
                    ReloadCause.CLIENT_JOIN,
                    minecraft
                )
            ScriptPackDependencyGraph.compilationGroups(packs).forEach { group ->
                ok = ScriptEngine.compileAndExecuteAll(group, invocation) && ok
            }
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
        val future = synchronized(serverReloadStateLock) { serverReloadFuture } ?: return
        try {
            future.get(SERVER_RELOAD_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for the server script reload", interrupted)
        } catch (failure: Exception) {
            // Continuing configuration after a failed/timed-out registry reload
            // can expose a registry snapshot that does not match the scripts.
            throw IllegalStateException(
                "Server script reload did not complete before registry-sensitive work",
                failure
            )
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

        val previousWorldPacks = ScriptPackManager.collectExecutableWorldPacks()
        val previousItemComponents = snapshotItemComponents()

        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        tracker.step("katton.reload.common.set_game_directory")
        ScriptPackManager.setWorldDirectory(server.getWorldPath(LevelResource.ROOT))
        tracker.step("katton.reload.common.set_world_directory")
        ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        val candidateWorldPacks = ScriptPackManager.scanWorldPacksCandidate()
        val worldOnlyPacks = candidateWorldPacks.filter { it.enabled }
        tracker.step("katton.reload.common.scan_world_packs")

        tracker.step("katton.reload.common.collect_world_packs")
        val previousDataPacks = mutableListOf<ScriptPack>().apply {
            addAll(ScriptPackManager.collectExecutableGlobalPacks())
            addAll(previousWorldPacks)
        }
        tracker.step("katton.reload.server.collect_data_packs")
        val invocation = ScriptInvocation.server(ServerPhase.READY, reason, cause, server)
        val prepared = ScriptEngine.prepareWithFallback(worldOnlyPacks, previousWorldPacks, invocation)
        if (prepared == null) {
            tracker.finish("katton.reload.server.failed")
            return false
        }
        val effectiveWorldPacks = prepared.packs

        tracker.step("katton.reload.common.compile_execute_scripts")
        var firstReset = true
        val activatedWorldPacks = executePreparedGroupsWithFallback(
            initialPacks = effectiveWorldPacks,
            previousPacks = previousWorldPacks,
            invocation = invocation,
            resetRuntime = {
                resetServerRuntime(server, if (firstReset) tracker else null)
                check(restoreItemComponents(previousItemComponents)) {
                    "Failed to restore item components before server script activation"
                }
                firstReset = false
            }
        ) { group ->
            ScriptEngine.compileAndExecuteAll(group, invocation, tracker::update)
        }
        if (activatedWorldPacks == null) {
            restoreServerRuntime(server, previousWorldPacks, previousDataPacks, previousItemComponents, reason, cause)
            tracker.finish("katton.reload.server.failed")
            return false
        }
        val activatedServerDataPacks = mutableListOf<ScriptPack>().apply {
            addAll(ScriptPackManager.collectExecutableGlobalPacks())
            addAll(activatedWorldPacks)
        }
        if (!ScriptPackDataManager.activateAndReload(server, activatedServerDataPacks)) {
            restoreServerRuntime(server, previousWorldPacks, previousDataPacks, previousItemComponents, reason, cause)
            tracker.finish("katton.reload.server.failed")
            return false
        }
        tracker.step("katton.reload.server.mount_script_data")
        tracker.step("katton.reload.server.reload_script_data")
        val dataApplied = runCatching { ServerDatapackManager.apply(server) }
            .onFailure { logger.error("Failed to apply scripted data-pack mutations", it) }
            .isSuccess
        if (!dataApplied) {
            restoreServerRuntime(server, previousWorldPacks, previousDataPacks, previousItemComponents, reason, cause)
            tracker.finish("katton.reload.server.failed")
            return false
        }
        ScriptPackManager.publishWorldPacks(candidateWorldPacks, activatedWorldPacks)
        KattonConfigManager.retainPacks(
            setOf(ScriptPackScope.WORLD),
            activatedWorldPacks.mapTo(hashSetOf(), KattonConfigManager::configId)
        )
        tracker.step("katton.reload.server.apply_datapacks")
        tracker.finish("katton.reload.server.finished")
        return true
    }

    private fun resetServerRuntime(server: MinecraftServer, tracker: ReloadProgressTracker? = null) {
        ScriptCommandRegistry.beginReload(server)
        tracker?.step("katton.reload.server.reset_command_registry")
        if (Katton.registrationEnabled) {
            KattonRegistry.ITEMS.beginReload()
            tracker?.step("katton.reload.server.reset_item_registry")
            KattonRegistry.EFFECTS.beginReload()
            KattonRegistry.BLOCKS.beginReload()
            tracker?.step("katton.reload.server.reset_effect_block_registries")
            KattonRegistry.ENTITY_TYPES.beginReload()
            tracker?.step("katton.reload.server.reset_entity_type_registry")
            KattonRegistry.SOUND_EVENTS.beginReload()
            KattonRegistry.PARTICLE_TYPES.beginReload()
            tracker?.step("katton.reload.server.reset_sound_particle_registries")
            KattonRegistry.BLOCK_ENTITY_TYPES.beginReload()
            tracker?.step("katton.reload.server.reset_block_entity_type_registry")
            KattonRegistry.CREATIVE_TABS.beginReload()
            KattonRegistry.DATA_COMPONENT_TYPES.beginReload()
            tracker?.step("katton.reload.server.reset_creative_tabs_components")
        }
        if (Katton.hasClient) {
            KattonRegistry.ENTITY_RENDERERS.beginReload()
            tracker?.step("katton.reload.common.reset_entity_renderers")
        }
        ServerDatapackManager.beginReload()
        tracker?.step("katton.reload.server.reset_datapack_manager")
        clearItemModifications()
        tracker?.step("katton.reload.common.clear_item_modifications")
        Event.clearHandlersByScopeAndEnvironment(ScriptPackScope.WORLD, ScriptEnvironment.SERVER)
        tracker?.step("katton.reload.server.clear_event_handlers")
        clearManagedByScopeAndEnvironment(ScriptPackScope.WORLD, ScriptEnvironment.SERVER)
        tracker?.step("katton.reload.server.clear_managed_event_listeners")
        InjectionManager.beginReload(ScriptPackScope.WORLD, ScriptEnvironment.SERVER)
        tracker?.step("katton.reload.common.reset_injections")
    }

    private fun restoreServerRuntime(
        server: MinecraftServer,
        previousWorldPacks: List<ScriptPack>,
        previousDataPacks: List<ScriptPack>,
        previousItemComponents: Map<net.minecraft.world.item.Item, net.minecraft.core.component.DataComponentMap>,
        reason: InvocationReason,
        cause: ReloadCause
    ) {
        logger.warn("Restoring the previous server script snapshot after a rejected reload")
        resetServerRuntime(server)
        val restoredComponents = restoreItemComponents(previousItemComponents)
        val restoreInvocation = ScriptInvocation.server(ServerPhase.READY, reason, cause, server)
        var restoredScripts = true
        ScriptPackDependencyGraph.compilationGroups(previousWorldPacks).forEach { group ->
            restoredScripts = ScriptEngine.compileAndExecuteAll(group, restoreInvocation) && restoredScripts
        }
        val restoredResources = ScriptPackDataManager.activateAndReload(server, previousDataPacks)
        val restoredData = runCatching { ServerDatapackManager.apply(server) }
            .onFailure { logger.error("Failed to reapply the previous scripted data-pack mutations", it) }
            .isSuccess
        if (!restoredComponents || !restoredScripts || !restoredResources || !restoredData) {
            logger.error("Failed to fully restore the previous server script snapshot")
        }
        KattonConfigManager.retainPacks(
            setOf(ScriptPackScope.WORLD),
            previousWorldPacks.mapTo(hashSetOf(), KattonConfigManager::configId)
        )
    }

    /**
     * Executes weakly connected dependency components independently. A local
     * component that fails at runtime is replaced with its previous immutable
     * snapshot (or omitted when it has no last-known-good version), then the
     * runtime is rebuilt so partial registrations from the failed attempt cannot
     * leak into the activated state.
     */
    private fun executePreparedGroupsWithFallback(
        initialPacks: List<ScriptPack>,
        previousPacks: List<ScriptPack>,
        invocation: ScriptInvocation,
        resetRuntime: () -> Unit,
        executeGroup: (List<ScriptPack>) -> Boolean
    ): List<ScriptPack>? {
        var selected = initialPacks
        val maximumAttempts = (initialPacks.size + previousPacks.size + 1).coerceAtLeast(1)
        repeat(maximumAttempts) {
            if (runCatching(resetRuntime).onFailure {
                    logger.error("Failed to reset script runtime before activating a candidate", it)
                }.isFailure) return null
            val failedGroup = ScriptPackDependencyGraph.compilationGroups(selected)
                .firstOrNull { group -> !executeGroup(group) }
                ?: return selected
            if (failedGroup.any { it.scope == ScriptPackScope.SERVER_CACHE }) return null

            val failedIds = failedGroup.mapTo(hashSetOf()) { it.syncId }
            failedIds.forEach(KattonConfigManager::clearPack)
            val failedVersions = failedGroup.associate { it.syncId to it.codeHash }
            val fallback = previousPacks.filter { previous ->
                previous.enabled && previous.syncId in failedIds &&
                    failedVersions[previous.syncId] != previous.codeHash
            }
            ScriptIssueReporter.report(
                "Katton retained last-known-good script packs",
                if (fallback.isEmpty()) {
                    "Rejected runtime component: ${failedIds.sorted().joinToString()}; no previous snapshot is available"
                } else {
                    "Rejected runtime component: ${failedIds.sorted().joinToString()}; restored: " +
                        fallback.map { it.syncId }.sorted().joinToString()
                }
            )
            val nextCandidates = selected.filterNot { it.syncId in failedIds } + fallback
            val prepared = ScriptEngine.prepareWithFallback(nextCandidates, emptyList(), invocation) ?: return null
            selected = prepared.packs
        }
        logger.error("Script pack fallback did not converge after {} attempts", maximumAttempts)
        return null
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
        val requestToStart = synchronized(serverReloadStateLock) {
            if (serverReloadRunning.get()) {
                val pending = pendingServerReload
                if (pending == null) {
                    ServerReloadRequest(
                        server = server,
                        reason = reason,
                        cause = cause,
                        callbacks = mutableListOf(onComplete)
                    ).also {
                        pendingServerReload = it
                        serverReloadFuture = it.future
                    }
                } else {
                    // The follow-up observes the newest filesystem state. Merge
                    // callbacks so no caller mistakes coalescing for a failure.
                    pending.server = server
                    pending.reason = reason
                    pending.cause = cause
                    pending.callbacks += onComplete
                }
                null
            } else {
                serverReloadRunning.set(true)
                ServerReloadRequest(server, reason, cause, mutableListOf(onComplete)).also {
                    serverReloadFuture = it.future
                }
            }
        }
        requestToStart?.let(::scheduleServerReload)
    }

    private fun scheduleServerReload(request: ServerReloadRequest) {
        // Registry, event, command, and datapack mutations must stay on the server thread.
        try {
            request.server.execute {
                var completedSuccessfully = false
                try {
                    // Calling this on every pass is cheap once initialized and
                    // also repairs an initial-load failure before a queued reload.
                    if (!initializeGlobalReadyPacks(request.server)) {
                        throw IllegalStateException("Global server READY entrypoints failed")
                    }
                    val ok = reloadScripts(request.server, request.reason, request.cause)
                    if (ok) {
                        if (Katton.hasClient) {
                            ServerNetworking.publishPackRevision(request.server)
                            if (!request.server.isDedicatedServer && request.reason == InvocationReason.HOT_RELOAD) {
                                reloadClientScriptsAsync(
                                    InvocationReason.HOT_RELOAD,
                                    ReloadCause.SERVER_PACK_SYNC,
                                    null
                                )
                            }
                        }
                        request.future.complete(null)
                    } else {
                        request.future.completeExceptionally(IllegalStateException("Server script reload failed"))
                    }
                    completedSuccessfully = ok
                } catch (t: Throwable) {
                    logger.error("Failed to reload server scripts", t)
                    ReloadProgressState.finish("katton.reload.server.failed")
                    request.future.completeExceptionally(t)
                } finally {
                    request.callbacks.forEach { callback ->
                        runCatching { callback(completedSuccessfully) }
                            .onFailure { logger.error("Server script reload completion callback failed", it) }
                    }
                    startPendingServerReloadOrFinish()
                }
            }
        } catch (rejected: RuntimeException) {
            request.future.completeExceptionally(rejected)
            request.callbacks.forEach { callback ->
                runCatching { callback(false) }
                    .onFailure { logger.error("Rejected server reload callback failed", it) }
            }
            startPendingServerReloadOrFinish()
        }
    }

    /** Transfers ownership of the running slot directly to the queued pass. */
    private fun startPendingServerReloadOrFinish() {
        val next = synchronized(serverReloadStateLock) {
            pendingServerReload.also { pendingServerReload = null }.also { pending ->
                if (pending == null) {
                    serverReloadRunning.set(false)
                } else {
                    serverReloadFuture = pending.future
                }
            }
        }
        next?.let(::scheduleServerReload)
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
