package top.katton.engine

import com.google.common.collect.ImmutableMap
import com.mojang.datafixers.util.Pair
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
import net.minecraft.tags.TagLoader
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.io.path.absolutePathString
import kotlin.script.experimental.api.CompiledScript

object ScriptLoader : PreparableReloadListener {

    private val TYPE_KEY = ResourceKey.createRegistryKey<Registry<CompiledScript>>(
        Identifier.withDefaultNamespace("scripts")
    )
    private val KT_LISTER = FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".kt")

    @JvmStatic
    var scripts: Map<Identifier, String> = ImmutableMap.of()
    private val tagsLoader: TagLoader<String> = TagLoader(
        {id, bl -> getScript(id)}, Registries.tagsDirPath(TYPE_KEY)
    )
    var tags: Map<Identifier, List<String>> = mapOf()
    @Volatile
    private var latestResourceManager: ResourceManager? = null

    fun getScript(identifier: Identifier): Optional<out String?> {
        return Optional.ofNullable(scripts[identifier])
    }

    /**
     * Reload scripts and tags. Called when a datapack is reloading
     */
    override fun reload(
        sharedState: PreparableReloadListener.SharedState,
        executor: Executor,
        preparationBarrier: PreparableReloadListener.PreparationBarrier,
        executor2: Executor
    ): CompletableFuture<Void> {
        val manager = sharedState.resourceManager()
        latestResourceManager = manager
        val tagProcessor = CompletableFuture.supplyAsync({ this.tagsLoader.load(manager) }, executor)
        //Load and compile scripts. Script dependencies are also handled in this step.
        //Although kt files are recommended, we still remain compatible with kts files.
        val ktFileProcessor = loadScripts(manager, executor)
        return tagProcessor
            .thenCombine(ktFileProcessor) { a, b -> Pair.of(a, b) }
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync ({ pair ->
                this.scripts = pair.second
                this.tags = this.tagsLoader.build(pair.first)
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
                    val sources = map.mapValues { getResourceAbsolutePath(it.value, it.key) }
                    sources
                }
            }
    }

    private fun getResourceAbsolutePath(resource: Resource, identifier: Identifier): String {
        val rootPath = (resource.source() as PathPackResources).root
        return rootPath.resolve("data").resolve(identifier.namespace).resolve(identifier.path).absolutePathString()
    }
}