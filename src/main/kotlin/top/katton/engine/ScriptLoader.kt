package top.katton.engine

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import com.mojang.datafixers.util.Pair
import com.mojang.logging.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.tags.TagLoader
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.valueOrThrow

class ScriptLoader : PreparableReloadListener{

    var scripts: Map<Identifier, CompiledScript> = ImmutableMap.of()
    val tagsLoader: TagLoader<CompiledScript> = TagLoader(
        {id, bl -> getScript(id)}, Registries.tagsDirPath(TYPE_KEY)
    )
    var tags: Map<Identifier, List<CompiledScript>> = mapOf()
    @Volatile
    private var latestResourceManager: ResourceManager? = null

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
        latestResourceManager = manager
        val tagProcessor = CompletableFuture.supplyAsync(
    { this.tagsLoader.load(manager) }, executor
        )
        val ktsFileProcessor = loadScripts(KTS_LISTER, manager, executor, ::processScriptId)
        val ktFileProcessor = loadScripts(KT_LISTER, manager, executor, ::processScriptId)
        return tagProcessor
            .thenCombine(ktsFileProcessor) { a, b -> Pair.of(a, b) }
            .thenCombine(ktFileProcessor) { a, c -> Pair.of(a.first, mergeScriptMaps(a.second, c)) }
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
            return resource.openAsReader().use { reader ->
                val lines = reader.readLines()
                stripTopLevelFileAnnotations(lines).joinToString("\n")
            }
        } catch(e: Exception) {
            throw CompletionException(e)
        }
    }

    private fun stripTopLevelFileAnnotations(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines

        val result = lines.toMutableList()
        var index = 0

        while (index < lines.size) {
            val rawLine = lines[index]
            val trimmed = rawLine.trimStart()

            if (trimmed.isEmpty()) {
                index++
                continue
            }

            if (!trimmed.startsWith("@file:")) {
                break
            }

            var depth = trimmed.count { it == '(' } - trimmed.count { it == ')' }
            result[index] = ""
            index++
            while (depth > 0 && index < lines.size) {
                val line = lines[index]
                depth += line.count { it == '(' }
                depth -= line.count { it == ')' }
                result[index] = ""
                index++
            }
        }

        return result
    }

    private fun processScriptId(rawId: Identifier): Identifier {
        return if (rawId.path.endsWith(".main")) {
            Identifier.fromNamespaceAndPath(rawId.namespace, rawId.path.removeSuffix(".main"))
        } else {
            rawId
        }
    }

    private fun mergeScriptMaps(
        map1: Map<Identifier, CompletableFuture<CompiledScript>>,
        map2: Map<Identifier, CompletableFuture<CompiledScript>>
    ): Map<Identifier, CompletableFuture<CompiledScript>> {
        val merged = Maps.newHashMap<Identifier, CompletableFuture<CompiledScript>>()
        merged.putAll(map1)
        merged.putAll(map2)
        return merged
    }

    private fun loadScripts(
        lister: FileToIdConverter,
        manager: ResourceManager,
        executor: Executor,
        idProcessor: (Identifier) -> Identifier
    ): CompletableFuture<Map<Identifier, CompletableFuture<CompiledScript>>> {
        return CompletableFuture.supplyAsync({ lister.listMatchingResources(manager) }, executor)
            .thenCompose { map ->
                val map2 = Maps.newHashMap<Identifier, CompletableFuture<CompiledScript>>()
                for(entry in map.entries){
                    val id1 = entry.key
                    val rawId = lister.fileToId(id1)
                    val id2 = idProcessor(rawId)
                    val scope = CoroutineScope(executor.asCoroutineDispatcher())

                    map2[id2] = scope.future {
                        val script = readScript(entry.value)
                        LOGGER.info("Loading script $id2")
                        val debugSourceName = "${id1.namespace}/${id1.path}"
                        val result = engine.compile(
                            id2.toString(),
                            script,
                            debugSourceName,
                            DEBUG_FORCE_RECOMPILE_ON_RELOAD
                        ).valueOrThrow()
                        LOGGER.info("Loaded script $id2")
                        result
                    }
                }
                val completableFutures = map2.values.toTypedArray()
                CompletableFuture.allOf(*completableFutures)
                    .handle { _,_ -> map2 }
            }
    }

    suspend fun compileScriptNow(identifier: Identifier, debugSession: Boolean = false): ResultWithDiagnostics<CompiledScript> {
        val manager = latestResourceManager ?: return ResultWithDiagnostics.Failure(
            listOf(ScriptDiagnostic(-1, "Resource manager is not ready. Run /reload once before debug compile."))
        )

        val ktsId = Identifier.fromNamespaceAndPath(identifier.namespace, "${Registries.elementsDirPath(TYPE_KEY)}/${identifier.path}.kts")
        val ktId = Identifier.fromNamespaceAndPath(identifier.namespace, "${Registries.elementsDirPath(TYPE_KEY)}/${identifier.path}.kt")

        val ktsResource = manager.getResource(ktsId).orElse(null)
        val ktResource = manager.getResource(ktId).orElse(null)
        val resource = ktsResource ?: ktResource
        val matchedResourceId = if (ktsResource != null) ktsId else if (ktResource != null) ktId else null

        if (resource == null || matchedResourceId == null) {
            return ResultWithDiagnostics.Failure(
                listOf(ScriptDiagnostic(-1, "Script not found: $identifier (.kts/.kt)"))
            )
        }

        val source = readScript(resource)
        val sourceName = "${matchedResourceId.namespace}/${matchedResourceId.path}"
        val compileName = if (debugSession) "$identifier#debug" else identifier.toString()

        return engine.compile(
            compileName,
            source,
            sourceName,
            forceRecompile = debugSession
        )
    }

    companion object {
        var INSTANCE: ScriptLoader? = null
        private val LOGGER = LogUtils.getLogger()
        private val DEBUG_FORCE_RECOMPILE_ON_RELOAD = java.lang.Boolean.getBoolean("katton.debug")
        val TYPE_KEY = ResourceKey.createRegistryKey<Registry<CompiledScript>>(
            Identifier.withDefaultNamespace("scripts")
        )
        val KTS_LISTER = FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".kts")
        val KT_LISTER = FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".kt")
    }
}