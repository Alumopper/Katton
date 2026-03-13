package top.katton.engine

import com.google.common.collect.ImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.io.path.absolutePathString
import kotlin.script.experimental.api.CompiledScript

/**
 * Discovers Kotlin script files from CLIENT_RESOURCES resource packs.
 *
 * Scripts are located at: assets/<namespace>/scripts/ *.kt
 *
 * This is the client-side analogue of [ScriptLoader].
 * After each reload, [onReloadComplete] is invoked (if set) so the platform
 * entrypoint can trigger script compilation.
 */
object ClientScriptLoader : PreparableReloadListener {

    private val TYPE_KEY = ResourceKey.createRegistryKey<Registry<CompiledScript>>(
        Identifier.withDefaultNamespace("client_scripts")
    )
    private val KT_LISTER = FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".kt")

    @JvmStatic
    var scripts: Map<Identifier, String> = ImmutableMap.of()
        private set

    /** Called on the main thread after scripts have been applied from a resource reload. */
    @JvmField
    var onReloadComplete: Runnable? = null

    @Volatile
    private var latestResourceManager: ResourceManager? = null

    /**
     * Re-scan client resource pack scripts using the latest known resource manager.
     *
     * @return true if a resource manager was available and the script snapshot was refreshed.
     */
    @JvmStatic
    @Synchronized
    fun refreshFromLatestResourceManager(): Boolean {
        val manager = latestResourceManager ?: return false
        scripts = KT_LISTER.listMatchingResources(manager)
            .mapValues { getResourceAbsolutePath(it.value, it.key) }
        return true
    }

    override fun reload(
        sharedState: PreparableReloadListener.SharedState,
        executor: Executor,
        preparationBarrier: PreparableReloadListener.PreparationBarrier,
        executor2: Executor
    ): CompletableFuture<Void> {
        val manager = sharedState.resourceManager()
        latestResourceManager = manager
        return loadScripts(manager, executor)
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync({ loaded ->
                this.scripts = loaded
                onReloadComplete?.run()
            }, executor2)
    }

    private fun loadScripts(
        manager: ResourceManager,
        executor: Executor
    ): CompletableFuture<Map<Identifier, String>> {
        return CompletableFuture.supplyAsync({ KT_LISTER.listMatchingResources(manager) }, executor)
            .thenCompose { map ->
                val scope = CoroutineScope(executor.asCoroutineDispatcher())
                scope.future {
                    map.mapValues { getResourceAbsolutePath(it.value, it.key) }
                }
            }
    }

    private fun getResourceAbsolutePath(resource: Resource, identifier: Identifier): String {
        val rootPath = (resource.source() as PathPackResources).root
        return rootPath.resolve("assets").resolve(identifier.namespace).resolve(identifier.path).absolutePathString()
    }
}
