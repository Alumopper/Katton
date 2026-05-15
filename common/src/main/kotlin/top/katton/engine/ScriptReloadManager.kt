package top.katton.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import top.katton.Katton
import top.katton.api.clearClientRenderers
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

        val tracker = ReloadProgressTracker(16)
        tracker.begin("Reloading client scripts")

        val preserveIntegratedServerState = Katton.server != null && !Katton.server!!.isDedicatedServer
        if (!preserveIntegratedServerState) {
            Event.clearHandlersByScope(ScriptPackScope.WORLD)
            tracker.step("Clearing world handlers")
            InjectionManager.beginReload()
            tracker.step("Resetting injections")
        } else {
            tracker.step("Preserving server handlers")
        }
        clearClientRenderers()
        tracker.step("Clearing client renderers")
        KattonRegistry.ENTITY_RENDERERS.beginReload()
        tracker.step("Resetting entity renderers")

        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        tracker.step("Setting game directory")
        if (Katton.server != null) {
            ScriptPackManager.setWorldDirectory(Katton.server!!.getWorldPath(LevelResource.ROOT))
            ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        } else {
            ScriptPackManager.clearWorldDirectory()
        }
        tracker.step("Setting world directory")
        ScriptPackManager.refreshWorldPacks()
        tracker.step("Scanning world packs")

        val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
        tracker.step("Collecting world packs")
        val mergedPacks = mutableListOf<ScriptPack>().apply {
            addAll(worldOnlyPacks)
            addAll(ServerPackCacheManager.collectExecutablePacks())
        }
        tracker.step("Merging server cache packs")
        ScriptEngine.compileAndExecuteAll(mergedPacks, ScriptEnvironment.CLIENT)
        tracker.step("Compiling & executing scripts")
        tracker.finish("Client scripts reloaded")
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
                reloadClientScripts()
                future.complete(null)
            } catch (t: Throwable) {
                future.completeExceptionally(t)
                logger.error("Failed to reload client scripts asynchronously", t)
                ReloadProgressState.finish("Client script reload failed")
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
            ScriptEngine.compileAndExecuteAll(globalPacks, ScriptEnvironment.SERVER)
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
        tracker.begin("Reloading server scripts")

        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        tracker.step("Setting game directory")
        ScriptPackManager.setWorldDirectory(server.getWorldPath(LevelResource.ROOT))
        tracker.step("Setting world directory")
        ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        ScriptPackManager.refreshWorldPacks()
        tracker.step("Scanning world packs")

        ScriptCommandRegistry.beginReload(server)
        tracker.step("Resetting command registry")
        if (Katton.registrationEnabled) {
            KattonRegistry.ITEMS.beginReload()
            tracker.step("Resetting item registry")
            KattonRegistry.EFFECTS.beginReload()
            KattonRegistry.BLOCKS.beginReload()
            tracker.step("Resetting effect/block registries")
            KattonRegistry.ENTITY_TYPES.beginReload()
            tracker.step("Resetting entity type registry")
            KattonRegistry.SOUND_EVENTS.beginReload()
            KattonRegistry.PARTICLE_TYPES.beginReload()
            tracker.step("Resetting sound/particle registries")
            KattonRegistry.BLOCK_ENTITY_TYPES.beginReload()
            tracker.step("Resetting block entity type registry")
            KattonRegistry.CREATIVE_TABS.beginReload()
            KattonRegistry.DATA_COMPONENT_TYPES.beginReload()
            tracker.step("Resetting creative tabs & components")
        }
        if (Katton.hasClient) {
            KattonRegistry.ENTITY_RENDERERS.beginReload()
            tracker.step("Resetting entity renderers")
        }
        ServerDatapackManager.beginReload()
        tracker.step("Resetting datapack manager")
        Event.clearHandlersByScope(ScriptPackScope.WORLD)
        tracker.step("Clearing event handlers")
        InjectionManager.beginReload()
        tracker.step("Resetting injections")

        val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
        tracker.step("Collecting world packs")
        ScriptEngine.compileAndExecuteAll(worldOnlyPacks, ScriptEnvironment.SERVER)
        tracker.step("Compiling & executing scripts")
        ServerDatapackManager.apply(server)
        tracker.step("Applying datapacks")
        tracker.finish("Server scripts reloaded")
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
        serverReloadExecutor.execute {
            val tracker = ReloadProgressTracker(22)
            tracker.begin("Reloading server scripts")

            try {
                // Fast setup
                ScriptPackManager.setGameDirectory(Katton.gameDirectory)
                tracker.step("Setting game directory")
                ScriptPackManager.setWorldDirectory(server.getWorldPath(LevelResource.ROOT))
                tracker.step("Setting world directory")
                ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
                ScriptPackManager.refreshWorldPacks()
                tracker.step("Scanning world packs")

                ScriptCommandRegistry.beginReload(server)
                tracker.step("Resetting command registry")
                if (Katton.registrationEnabled) {
                    KattonRegistry.ITEMS.beginReload()
                    tracker.step("Resetting item registry")
                    KattonRegistry.EFFECTS.beginReload()
                    KattonRegistry.BLOCKS.beginReload()
                    tracker.step("Resetting effect/block registries")
                    KattonRegistry.ENTITY_TYPES.beginReload()
                    tracker.step("Resetting entity type registry")
                    KattonRegistry.SOUND_EVENTS.beginReload()
                    KattonRegistry.PARTICLE_TYPES.beginReload()
                    tracker.step("Resetting sound/particle registries")
                    KattonRegistry.BLOCK_ENTITY_TYPES.beginReload()
                    tracker.step("Resetting block entity type registry")
                    KattonRegistry.CREATIVE_TABS.beginReload()
                    KattonRegistry.DATA_COMPONENT_TYPES.beginReload()
                    tracker.step("Resetting creative tabs & components")
                }
                if (Katton.hasClient) {
                    KattonRegistry.ENTITY_RENDERERS.beginReload()
                    tracker.step("Resetting entity renderers")
                }
                ServerDatapackManager.beginReload()
                tracker.step("Resetting datapack manager")
                Event.clearHandlersByScope(ScriptPackScope.WORLD)
                tracker.step("Clearing event handlers")
                InjectionManager.beginReload()
                tracker.step("Resetting injections")

                val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
                tracker.step("Collecting world packs")

                // Heavy compilation + execution
                ScriptEngine.compileAndExecuteAll(worldOnlyPacks, ScriptEnvironment.SERVER)
                tracker.step("Compiling & executing scripts")

                // Post-compilation must run on server thread (registry mutations).
                val reloadFuture = future
                server.execute {
                    try {
                        ServerDatapackManager.apply(server)
                        tracker.step("Applying datapacks")
                        tracker.finish("Server scripts reloaded")
                        reloadFuture.complete(null)
                        onComplete(true)
                    } catch (t: Throwable) {
                        logger.error("Failed to apply datapacks after async server reload", t)
                        ReloadProgressState.finish("Server script reload failed")
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
                    ReloadProgressState.finish("Server script reload failed")
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
