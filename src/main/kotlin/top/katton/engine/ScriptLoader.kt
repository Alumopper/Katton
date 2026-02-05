package top.katton.engine

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import com.mojang.datafixers.util.Pair
import com.mojang.logging.LogUtils
import kotlinx.coroutines.runBlocking
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.Resource
import net.minecraft.tags.TagLoader
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.valueOrThrow

class ScriptLoader : PreparableReloadListener{

    var scripts: Map<Identifier, CompiledScript> = ImmutableMap.of()
    val tagsLoader: TagLoader<CompiledScript> = TagLoader(
        {id, bl -> getScript(id)}, Registries.tagsDirPath(TYPE_KEY)
    )
    var tags: Map<Identifier, List<CompiledScript>> = mapOf()

    fun getScript(identifier: Identifier): Optional<out CompiledScript?> {
        return Optional.ofNullable(scripts[identifier])
    }
    val engine = ScriptEngine()
    var mainScript: Map<Identifier, CompiledScript> = mapOf()

    override fun reload(
        sharedState: PreparableReloadListener.SharedState,
        executor: Executor,
        preparationBarrier: PreparableReloadListener.PreparationBarrier,
        executor2: Executor
    ): CompletableFuture<Void> {
        INSTANCE = this
        val manager = sharedState.resourceManager()
        val completableFuture = CompletableFuture.supplyAsync(
    { this.tagsLoader.load(manager) }, executor
        )
        val completableFuture2: CompletableFuture<Map<Identifier, CompletableFuture<CompiledScript>>> = CompletableFuture.supplyAsync(
            {LISTER.listMatchingResources(manager)}, executor
        ).thenCompose { map ->
            val map2 = Maps.newHashMap<Identifier, CompletableFuture<CompiledScript>>()
            for(entry in map.entries){
                val id1 = entry.key
                val rawId = LISTER.fileToId(id1)
                val id2 = if (rawId.path.endsWith(".main")) {
                    Identifier.fromNamespaceAndPath(rawId.namespace, rawId.path.removeSuffix(".main"))
                } else {
                    rawId
                }
                map2.put(id2, CompletableFuture.supplyAsync({
                    val script = readScript(entry.value)
                    val result = runBlocking {
                        engine.compile(id2.toString(), script)
                    }.valueOrThrow()
                    result
                }, executor))
            }
            val completableFutures = map2.values.toTypedArray()
            return@thenCompose CompletableFuture.allOf(*completableFutures)
                .handle { _,_ -> map2 }
        }

        return completableFuture.thenCombine(completableFuture2) { a, b -> Pair.of(a, b) }
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync ({ pair ->
                val map = pair.second
                
                // Synchronize the cache: remove scripts that are no longer in the loaded map
                val activeScriptNames = map.keys.map { it.toString() }.toSet()
                this.engine.updateCache(activeScriptNames)

                val builder = ImmutableMap.builder<Identifier, CompiledScript>()
                map.forEach { id, completableFuturex ->
                    completableFuturex.handle { script, throwable ->
                        if(throwable != null){
                            LOGGER.error("Failed to load script $id", throwable)
                        } else {
                            builder.put(id, script)
                        }
                    }.join()
                }
                this.scripts = builder.build()
                this.tags = this.tagsLoader.build(pair.first)
                this.mainScript = scripts.filter { it.key.path == "main" }
            }, executor2)
    }

    private fun readScript(resource: Resource): String {
        try {
            return resource.openAsReader().use { it.readText() }
        } catch(e: Exception) {
            throw CompletionException(e)
        }
    }

    companion object {
        var INSTANCE: ScriptLoader? = null
        private val LOGGER = LogUtils.getLogger()
        val TYPE_KEY = ResourceKey.createRegistryKey<Registry<CompiledScript>>(
            Identifier.withDefaultNamespace("scripts")
        )
        val LISTER = FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".kts")

    }
}