package top.katton.engine

import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import top.katton.Katton
import top.katton.api.ClientPhase
import top.katton.api.InvocationReason
import top.katton.api.ReloadCause
import top.katton.api.ServerPhase
import top.katton.api.event.managed.clearManagedByOwnerPrefix
import top.katton.client.ReloadProgressState
import top.katton.client.ReloadProgressTracker
import top.katton.client.ScriptPackResourceManager
import top.katton.config.KattonConfigManager
import top.katton.datapack.ServerDatapackManager
import top.katton.datapack.ScriptPackDataManager
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackManager
import top.katton.pack.ScriptPackScope
import top.katton.pack.ServerPackCacheManager
import top.katton.network.ServerNetworking
import top.katton.platform.ServerTaskScheduler
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
        val future: CompletableFuture<Void> = CompletableFuture(),
        val beforePrepare: (() -> Unit)? = null
    )

    private data class PreparedServerReload(
        val previousWorld: List<ScriptPack>, val candidateWorld: List<ScriptPack>,
        val effectiveWorld: List<ScriptPack>, val previousGlobals: List<ScriptPack>,
        val globalCandidate: List<ScriptPack>?, val plans: List<PackPreparation>,
        val previousPlans: List<PackPreparation>
    ) {
        fun select(packs: List<ScriptPack>): List<PackPreparation> {
            val next = plans.associateBy { it.pack.syncId }
            val old = previousPlans.associateBy { it.pack.syncId }
            val chosen = next.toMutableMap()
            packs.forEach { pack ->
                chosen[pack.syncId] = next[pack.syncId]?.takeIf { it.pack.hash == pack.hash }
                    ?: old[pack.syncId]?.takeIf { it.pack.hash == pack.hash }
                    ?: error("No immutable preparation for ${pack.syncId}")
            }
            val result = mutableListOf<PackPreparation>()
            val seen = hashSetOf<String>()
            fun visit(id: String) {
                if (!seen.add(id)) return
                val plan = chosen[id] ?: old[id] ?: error("Missing prepared dependency: $id")
                plan.visible.forEach(::visit)
                result += plan
            }
            packs.forEach { visit(it.syncId) }
            return result
        }
    }

    private val serverPreparationExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Katton-ServerPreparation").also { it.isDaemon = true }
    }

    private val clientReloadExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Katton-ClientReload").also { it.isDaemon = true }
    }

    private val clientReloadRunning = AtomicBoolean(false)
    private val serverReloadRunning = AtomicBoolean(false)
    private val developmentReload = AtomicBoolean(false)
    private const val DEVELOPMENT_WATCHDOG_ATTEMPTS = 120
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

    internal fun clearClientRuntimeSnapshot() {
        activeClientPacks = emptyList()
        clientJoinedDispatchPending = false
    }

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
        var success = false
        return try {
            reloadClientScriptsPrepared(reason, cause).also { success = it }
        } finally {
            ScriptPackManager.finishGlobalResourceRefresh(success)
            runOnClientThreadAndWait { top.katton.client.scene.ClientSceneManager.finishReload() }
        }
    }

    private fun reloadClientScriptsPrepared(reason: InvocationReason, cause: ReloadCause): Boolean {
        //simple progress bar
        //seems straightforward and brutal, but it works well enough for now.
        //it's making the code a bit messy, but... just consider it as an alternative comment (?
        val tracker = ReloadProgressTracker(17)
        tracker.begin("katton.reload.client.begin")

        // Keep the last-known-good snapshot live until the candidate compiles.
        val previousPacks = activeClientPacks.toList()

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
        val previousGlobals = ScriptPackManager.collectExecutableGlobalPacks()
        ScriptPackManager.refreshGlobalResources()
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
        var globals = ScriptPackManager.collectExecutableGlobalPacks()
        if (globals != previousGlobals) {
            val accepted = ScriptPackResourceManager.activateAndReload(globals + previousPacks)
            ScriptPackManager.finishGlobalResourceRefresh(accepted)
            if (!accepted) globals = previousGlobals
        }
        val activatedPacks = PackReloadBatch.run(previousPacks, effectivePacks,
            activate = { proposed ->
                var ok = ScriptEngine.compileAndExecuteAll(proposed, registryInvocation, tracker::update)
                if (ok && minecraft.player != null && minecraft.level != null) {
                    ok = ScriptEngine.compileAndExecuteAll(proposed,
                        ScriptInvocation.client(ClientPhase.JOINED, reason, cause, minecraft), tracker::update)
                    if (ok) clientJoinedDispatchPending = false
                } else if (ok) clientJoinedDispatchPending = true
                if (!ok) null else {
                    val active = PackRuntime.effectivePacks(ScriptEnvironment.CLIENT,
                        setOf(ScriptPackScope.WORLD, ScriptPackScope.SERVER_CACHE))
                    if (ScriptPackResourceManager.activateAndReload(globals + active)) active else null
                }
            },
            restoreResources = { old ->
                check(ScriptPackResourceManager.activateAndReload(globals + old)) { "Could not restore client asset view" }
                KattonConfigManager.retainPacks(setOf(ScriptPackScope.WORLD, ScriptPackScope.SERVER_CACHE),
                    old.mapTo(hashSetOf(), KattonConfigManager::configId))
            }
        ) ?: run { tracker.finish("katton.reload.client.failed"); return false }
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
        synchronized(clientReloadQueueLock) {
            if (developmentReload.get() || !clientReloadRunning.compareAndSet(false, true)) {
                enqueuePendingClientReload(reason, cause, onComplete)
                return true
            }
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
            if (developmentReload.get() || clientReloadRunning.get()) return
            val next = pendingClientReload ?: return
            pendingClientReload = null
            clientReloadRunning.set(true)
            next
        }
        startClientReload(pending.reason, pending.cause, pending.callbacks)
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
            ok = ScriptEngine.compileAndExecuteAll(packs, invocation)
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
        top.katton.dev.KattonDevBridge.worldChanged()
        // Remote audio handles belong to the stopping server regardless of how far the
        // global phase got; nothing can command them once the server stops.
        top.katton.api.audio.AudioServerTransport.clear()
        if (server == null || globalReadyServer === server) {
            PackRuntime.resetGlobalPhase(ScriptEnvironment.SERVER, ServerPhase.READY.name)
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
        var success = false
        return try {
            reloadServerScriptsPrepared(server, reason, cause).also { success = it }
        } finally { ScriptPackManager.finishGlobalResourceRefresh(success) }
    }

    /** Reads and compiles immutable snapshots without invoking script code or mutating native resources. */
    private fun prepareServerReload(server: MinecraftServer, reason: InvocationReason, cause: ReloadCause): PreparedServerReload {
        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        ScriptPackManager.setWorldDirectory(server.getWorldPath(LevelResource.ROOT))
        ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        val previousWorld = ScriptPackManager.collectExecutableWorldPacks()
        val previousGlobals = ScriptPackManager.collectExecutableGlobalPacks()
        val previousPlans = PackRuntime.preparations(ScriptEnvironment.SERVER)
        ScriptPackManager.refreshGlobalResources()
        try {
            val candidates = ScriptPackManager.scanWorldPacksCandidate()
            val invocation = ScriptInvocation.server(ServerPhase.READY, reason, cause, server)
            val effective = ScriptEngine.prepareWithFallback(candidates.filter { it.enabled }, previousWorld, invocation)
                ?: error("Server script preparation failed")
            val globals = ScriptPackManager.collectExecutableGlobalPacks()
            val plans = ScriptEngine.preparePacks(globals + effective.packs, invocation)
            return PreparedServerReload(previousWorld, candidates, effective.packs, previousGlobals,
                ScriptPackManager.captureGlobalResourceCandidate(), plans, previousPlans)
        } finally { ScriptPackManager.finishGlobalResourceRefresh(false) }
    }

    private fun reloadServerScriptsPrepared(server: MinecraftServer?, reason: InvocationReason, cause: ReloadCause,
                                            snapshot: PreparedServerReload? = null): Boolean {
        if (server == null) return false
        val prepared = snapshot ?: prepareServerReload(server, reason, cause)
        val tracker = ReloadProgressTracker(24)
        tracker.begin("katton.reload.server.begin")
        val previousWorldPacks = prepared.previousWorld
        val candidateWorldPacks = prepared.candidateWorld
        val effectiveWorldPacks = prepared.effectiveWorld
        val previousGlobals = prepared.previousGlobals
        ScriptPackManager.stageGlobalResourceCandidate(prepared.globalCandidate)
        val invocation = ScriptInvocation.server(ServerPhase.READY, reason, cause, server)

        tracker.step("katton.reload.common.compile_execute_scripts")
        var globals = ScriptPackManager.collectExecutableGlobalPacks()
        if (globals != previousGlobals) {
            val accepted = ScriptPackDataManager.activateAndReload(server, globals + previousWorldPacks)
            ScriptPackManager.finishGlobalResourceRefresh(accepted)
            if (!accepted) globals = previousGlobals
        }
        val activatedWorldPacks = PackReloadBatch.run(previousWorldPacks, effectiveWorldPacks,
            activate = { proposed ->
                if (!ScriptEngine.executePreparedPacks(proposed, prepared.select(proposed), invocation)) null else {
                    val active = PackRuntime.effectivePacks(ScriptEnvironment.SERVER, setOf(ScriptPackScope.WORLD))
                    if (!ScriptPackDataManager.activateAndReload(server, globals + active)) null
                    else if (runCatching { ServerDatapackManager.apply(server) }
                            .onFailure { logger.error("Failed to apply scripted datapack resources", it) }.isSuccess) active
                    else null
                }
            },
            restoreResources = { old ->
                check(ScriptPackDataManager.activateAndReload(server, globals + old)) { "Could not restore server datapack view" }
                ServerDatapackManager.apply(server)
                KattonConfigManager.retainPacks(setOf(ScriptPackScope.WORLD), old.mapTo(hashSetOf(), KattonConfigManager::configId))
            }
        ) ?: run { tracker.finish("katton.reload.server.failed"); return false }
        ScriptPackManager.publishWorldPacks(candidateWorldPacks, activatedWorldPacks)
        KattonConfigManager.retainPacks(
            setOf(ScriptPackScope.WORLD),
            activatedWorldPacks.mapTo(hashSetOf(), KattonConfigManager::configId)
        )
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
        serverPreparationExecutor.execute {
            val preparation = runCatching {
                request.beforePrepare?.invoke()
                prepareServerReload(request.server, request.reason, request.cause)
            }
            activateServerReload(request, preparation)
        }
    }

    /** Reserves both reload lanes before installing a development snapshot. Never blocks a game thread. */
    fun tryDevelopmentReload(server: MinecraftServer, beforePrepare: () -> Unit, completed: (Boolean) -> Unit): Boolean {
        val request = synchronized(clientReloadQueueLock) { synchronized(serverReloadStateLock) {
            if (serverReloadRunning.get() || clientReloadRunning.get() || developmentReload.get()) return false
            developmentReload.set(true)
            serverReloadRunning.set(true)
            val callback: (Boolean) -> Unit = { success ->
                if (success && Katton.hasClient && !server.isDedicatedServer && Katton.server === server) {
                    synchronized(clientReloadQueueLock) {
                        clientReloadRunning.set(true)
                        developmentReload.set(false)
                    }
                    startClientReload(InvocationReason.HOT_RELOAD, ReloadCause.SERVER_PACK_SYNC, listOf { clientOk ->
                        try { completed(clientOk) } finally { startPendingServerReloadOrFinish() }
                    })
                } else {
                    try { completed(success) } finally {
                        developmentReload.set(false)
                        startPendingServerReloadOrFinish(); startPendingClientReloadIfPresent()
                    }
                }
            }
            ServerReloadRequest(server, InvocationReason.HOT_RELOAD, ReloadCause.COMMAND,
                mutableListOf(callback), beforePrepare = beforePrepare).also { serverReloadFuture = it.future }
        } }
        try {
            scheduleServerReload(request)
        } catch (rejected: Throwable) {
            // The lanes were claimed before submission; release them so reloads keep working.
            logger.error("Failed to schedule the development reload", rejected)
            synchronized(clientReloadQueueLock) { synchronized(serverReloadStateLock) {
                developmentReload.set(false)
                serverReloadRunning.set(false)
            } }
            request.future.completeExceptionally(rejected)
            request.callbacks.forEach { callback ->
                runCatching { callback(false) }.onFailure { logger.error("Rejected development reload callback failed", it) }
            }
            return false
        }
        return true
    }

    private fun activateServerReload(request: ServerReloadRequest, preparation: Result<PreparedServerReload>) {
        val activationClaimed = AtomicBoolean(false)
        val watchdogAttempts = AtomicInteger(0)
        fun abandonStoppedDevelopmentRequest() {
            if (request.beforePrepare == null || activationClaimed.get()) return
            if (Katton.server !== request.server && activationClaimed.compareAndSet(false, true)) {
                request.future.completeExceptionally(IllegalStateException("World closed before script activation"))
                request.callbacks.forEach { callback ->
                    runCatching { callback(false) }.onFailure { logger.error("Cancelled development reload callback failed", it) }
                }
            } else if (!activationClaimed.get()) {
                // A queued activation may never run. Stop waiting so a development deployment
                // cannot hold both reload lanes forever.
                if (watchdogAttempts.incrementAndGet() > DEVELOPMENT_WATCHDOG_ATTEMPTS && activationClaimed.compareAndSet(false, true)) {
                    logger.error("Abandoning a development reload whose activation never ran on {}", request.server)
                    request.future.completeExceptionally(IllegalStateException("Script activation never started"))
                    request.callbacks.forEach { callback ->
                        runCatching { callback(false) }.onFailure { logger.error("Abandoned development reload callback failed", it) }
                    }
                } else {
                    CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute { abandonStoppedDevelopmentRequest() }
                }
            }
        }
        // Registry, event, command, and datapack mutations must stay on the
        // platform's server-wide mutation thread (the global region on Folia).
        try {
            ServerTaskScheduler.execute(request.server, Runnable {
                if (!activationClaimed.compareAndSet(false, true)) return@Runnable
                var completedSuccessfully = false
                try {
                    check(Katton.server === request.server) { "Server lifecycle ended during script preparation" }
                    val snapshot = preparation.getOrThrow()
                    ScriptPackManager.stageGlobalResourceCandidate(snapshot.globalCandidate)
                    if (globalReadyServer !== request.server) {
                        val globals = snapshot.plans.map { it.pack }.filter { it.scope == ScriptPackScope.GLOBAL }
                        check(ScriptEngine.executePreparedPacks(globals, snapshot.select(globals),
                            ScriptInvocation.server(ServerPhase.READY, InvocationReason.INITIAL_LOAD, ReloadCause.SERVER_START, request.server))) {
                            "Global server READY entrypoints failed"
                        }
                        globalReadyServer = request.server
                    }
                    val ok = reloadServerScriptsPrepared(request.server, request.reason, request.cause, snapshot)
                    if (ok) {
                        if (Katton.hasClient) {
                            ServerNetworking.publishPackRevision(request.server)
                            if (request.beforePrepare == null && !request.server.isDedicatedServer && request.reason == InvocationReason.HOT_RELOAD) {
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
                    top.katton.dev.DevEvents.emit("ERROR", t.stackTraceToString())
                    ReloadProgressState.finish("katton.reload.server.failed")
                    request.future.completeExceptionally(t)
                } finally {
                    ScriptPackManager.finishGlobalResourceRefresh(completedSuccessfully)
                    request.callbacks.forEach { callback ->
                        runCatching { callback(completedSuccessfully) }
                            .onFailure { logger.error("Server script reload completion callback failed", it) }
                    }
                    if (request.beforePrepare == null) startPendingServerReloadOrFinish()
                }
            })
            // A stopped Minecraft executor may silently retain its queued tasks.
            // Claim cancellation before activation and release the deployment lanes exactly once.
            if (request.beforePrepare != null) abandonStoppedDevelopmentRequest()
        } catch (rejected: RuntimeException) {
            if (!activationClaimed.compareAndSet(false, true)) return
            logger.error("Server scheduler rejected script reload setup", rejected)
            request.future.completeExceptionally(rejected)
            request.callbacks.forEach { callback ->
                runCatching { callback(false) }
                    .onFailure { logger.error("Rejected server reload callback failed", it) }
            }
            if (request.beforePrepare == null) startPendingServerReloadOrFinish()
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
