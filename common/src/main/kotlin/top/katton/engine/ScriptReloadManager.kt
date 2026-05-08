package top.katton.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import top.katton.Katton
import top.katton.api.clearClientRenderers
import top.katton.api.dpcaller.EntityEvent
import top.katton.client.ReloadProgressState
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

    private val clientReloadRunning = AtomicBoolean(false)

    @Volatile
    private var clientReloadFuture: CompletableFuture<Void>? = null

    /**
     * Reloads all client-side world scripts.
     */
    @JvmStatic
    fun reloadClientScripts(): Boolean {
        ReloadProgressState.begin("Reloading client scripts", 0.02f)
        val preserveIntegratedServerState = Katton.server != null && !Katton.server!!.isDedicatedServer
        if (!preserveIntegratedServerState) {
            Event.clearHandlersByScope(ScriptPackScope.WORLD)
            ReloadProgressState.update("Clearing handlers", 0.08f)
            InjectionManager.beginReload()
            ReloadProgressState.update("Resetting injections", 0.14f)
        } else {
            ReloadProgressState.update("Preserving server handlers", 0.14f)
        }
        clearClientRenderers()
        KattonRegistry.ENTITY_RENDERERS.beginReload()
        ReloadProgressState.update("Refreshing packs", 0.22f)
        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        if (Katton.server != null) {
            ScriptPackManager.setWorldDirectory(Katton.server!!.getWorldPath(LevelResource.ROOT))
            ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        } else {
            ScriptPackManager.clearWorldDirectory()
        }
        ScriptPackManager.refreshWorldPacks()
        ReloadProgressState.update("Compiling client scripts", 0.42f)
        val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
        val mergedPacks = mutableListOf<ScriptPack>().apply {
            addAll(worldOnlyPacks)
            addAll(ServerPackCacheManager.collectExecutablePacks())
        }
        ScriptEngine.compileAndExecuteAll(mergedPacks, ScriptEnvironment.CLIENT)
        ReloadProgressState.finish("Client scripts reloaded")
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

        ReloadProgressState.begin("Reloading server scripts", 0.02f)

        ScriptPackManager.setGameDirectory(Katton.gameDirectory)
        ScriptPackManager.setWorldDirectory(server.getWorldPath(LevelResource.ROOT))
        ensureDirectory(ScriptPackManager.getWorldScriptDirectory())
        ScriptPackManager.refreshWorldPacks()
        ReloadProgressState.update("Preparing registries", 0.12f)

        ScriptCommandRegistry.beginReload(server)
        KattonRegistry.ITEMS.beginReload()
        KattonRegistry.EFFECTS.beginReload()
        KattonRegistry.BLOCKS.beginReload()
        KattonRegistry.ENTITY_TYPES.beginReload()
        KattonRegistry.SOUND_EVENTS.beginReload()
        KattonRegistry.PARTICLE_TYPES.beginReload()
        KattonRegistry.BLOCK_ENTITY_TYPES.beginReload()
        KattonRegistry.CREATIVE_TABS.beginReload()
        KattonRegistry.DATA_COMPONENT_TYPES.beginReload()
        KattonRegistry.ENTITY_RENDERERS.beginReload()
        ServerDatapackManager.beginReload()
        EntityEvent.beginReload()
        Event.clearHandlersByScope(ScriptPackScope.WORLD)
        InjectionManager.beginReload()
        ReloadProgressState.update("Compiling server scripts", 0.48f)
        val worldOnlyPacks = ScriptPackManager.collectExecutableWorldPacks()
        ScriptEngine.compileAndExecuteAll(worldOnlyPacks, ScriptEnvironment.SERVER)
        ReloadProgressState.update("Applying datapacks", 0.82f)
        ServerDatapackManager.apply(server)
        EntityEvent.rebindLoadedEntities(server)
        ReloadProgressState.finish("Server scripts reloaded")
        return true
    }

    private fun ensureDirectory(path: Path?) {
        if (path == null) return
        try {
            Files.createDirectories(path)
        } catch (_: Exception) {
        }
    }
}
