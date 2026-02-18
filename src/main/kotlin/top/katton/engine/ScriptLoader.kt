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
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.tags.TagLoader
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.absolutePathString
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.valueOrThrow

object ScriptLoader : PreparableReloadListener {

    private val LOGGER = LogUtils.getLogger()
    private val DEBUG_FORCE_RECOMPILE_ON_RELOAD = java.lang.Boolean.getBoolean("katton.debug")
    val TYPE_KEY = ResourceKey.createRegistryKey<Registry<CompiledScript>>(
        Identifier.withDefaultNamespace("scripts")
    )
    val KTS_LISTER = FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".kts")
    val KT_LISTER = FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".kt")

    var scripts: Map<Identifier, KattonScript> = ImmutableMap.of()
    val tagsLoader: TagLoader<KattonScript> = TagLoader(
        {id, bl -> getScript(id)}, Registries.tagsDirPath(TYPE_KEY)
    )
    var tags: Map<Identifier, List<KattonScript>> = mapOf()
    @Volatile
    private var latestResourceManager: ResourceManager? = null


    fun getScript(identifier: Identifier): Optional<out KattonScript?> {
        return Optional.ofNullable(scripts[identifier])
    }

    @JvmStatic
    var mainScript: Map<Identifier, KattonScript> = mapOf()
    private val scriptSourceCache = ConcurrentHashMap<String, kotlin.Pair<String, String>>()
    private val pendingRelinkScripts = ConcurrentHashMap.newKeySet<String>()

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
        ScriptEngine.resetAllCachesForReload()
        val tagProcessor = CompletableFuture.supplyAsync({ this.tagsLoader.load(manager) }, executor)
        //Load and compile scripts. Script dependencies are also handled in this step.
        //Although kt files are recommended, we still remain compatible with kts files.
        val ktsFileProcessor = loadScripts(KTS_LISTER, manager, executor)
        val ktFileProcessor = loadScripts(KT_LISTER, manager, executor)
        return tagProcessor
            .thenCombine(ktsFileProcessor) { a, b -> Pair.of(a, b) }
            .thenCombine(ktFileProcessor) { a, c -> Pair.of(a.first, mergeScriptMaps(a.second, c)) }
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync ({ pair ->
                val map = pair.second

                // Synchronize the cache: remove scripts that are no longer in the loaded map
                val activeScriptNames = map.keys.map { it.toString() }.toSet()
                ScriptEngine.updateCache(activeScriptNames)

                val builder = ImmutableMap.builder<Identifier, KattonScript>()
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

                // we stipulate that all kt files under the same directory must in the same package
                // main.kt under each directory will be considered as the main script of that directory
                // It means that different directories may refer to the same package, but each directory
                //can have its own main script.
                this.mainScript = scripts.filter { it.key.path == "main" || it.key.path.endsWith("/main") }
            }, executor2)
    }

    private fun readScript(resource: Resource): kotlin.Pair<String, HashSet<String>> {
        resource.openAsReader().use { reader ->
            val script = StringBuilder()
            val imported = HashSet<String>()
            var scriptPackage: String? = null
            var firstNonTrivialLine = false

            reader.forEachLine { line ->
                val l = line.trim()
                // 跳过首个非实质行（注解或空行），直到遇到第一个实质性行再检测 package
                if (!firstNonTrivialLine && (l.startsWith("@") || l.isEmpty())) {
                    script.appendLine(line)
                    return@forEachLine
                }
                if (!firstNonTrivialLine) {
                    if (l.startsWith("package")) {
                        scriptPackage = l.removePrefix("package").trim()
                    }
                    firstNonTrivialLine = true
                }
                if (l.startsWith("import")) {
                    parseImportedIdentifiers(l)?.let { imported.add(it) }
                }
                script.appendLine(line)
            }
            scriptPackage?.let { ScriptEngine.scriptPackages.add(it) }
            return script.toString() to imported
        }
    }

    private fun mergeScriptMaps(
        map1: Map<Identifier, CompletableFuture<KattonScript>>,
        map2: Map<Identifier, CompletableFuture<KattonScript>>
    ): Map<Identifier, CompletableFuture<KattonScript>> {
        val merged = Maps.newHashMap<Identifier, CompletableFuture<KattonScript>>()
        merged.putAll(map1)
        merged.putAll(map2)
        return merged
    }

    private fun loadScripts(
        lister: FileToIdConverter,
        manager: ResourceManager,
        executor: Executor
    ): CompletableFuture<Map<Identifier, CompletableFuture<KattonScript>>> {
        return CompletableFuture.supplyAsync({ lister.listMatchingResources(manager) }, executor)
            .thenCompose { map ->
                val scope = CoroutineScope(executor.asCoroutineDispatcher())
                scope.future {
                    val sources = map.mapValues { readScript(it.value) to getResourceAbsolutePath(it.value, it.key) }
                    val requests = mutableListOf<ScriptEngine.CompileRequest>()
                    val pathByName = HashMap<String, Identifier>()

                    sources.forEach { (pathId, contentAndImports) ->
                        val rawId = lister.fileToId(pathId)
                        val compileName = rawId.toString()
                        LOGGER.info("Queueing script {} for dependency-aware compile", rawId)
                        requests.add(
                            ScriptEngine.CompileRequest(
                                name = compileName,
                                sourceCode = contentAndImports.first.first,
                                imported = contentAndImports.first.second,
                                path = contentAndImports.second
                            )
                        )
                        pathByName[compileName] = rawId
                    }

                    val compileResults = ScriptEngine.compileBatch(
                        requests = requests,
                        forceRecompile = DEBUG_FORCE_RECOMPILE_ON_RELOAD
                    )

                    val futureMap = Maps.newHashMap<Identifier, CompletableFuture<KattonScript>>()
                    compileResults.forEach { (name, result) ->
                        val id = pathByName[name] ?: return@forEach
                        when (result) {
                            is ResultWithDiagnostics.Success -> {
                                futureMap[id] = CompletableFuture.completedFuture(result.value)
                                LOGGER.info("Loaded script {}", id)
                            }

                            is ResultWithDiagnostics.Failure -> {
                                val message = result.reports.joinToString("\n") { report ->
                                    "${report.severity}: ${report.message}"
                                }
                                val future = CompletableFuture<KattonScript>()
                                future.completeExceptionally(
                                    IllegalStateException("Failed to compile script $id\n$message")
                                )
                                futureMap[id] = future
                            }
                        }
                    }

                    futureMap
                }.thenCompose { map2 ->
                    val completableFutures = map2.values.toTypedArray()
                    CompletableFuture.allOf(*completableFutures)
                        .handle { _, _ -> map2 }
                }
            }
    }

    private fun parseImportedIdentifiers(rawLine: String): String? {
        val line = rawLine.trim()
        val body = line.removePrefix("import ").trim()
        val identifier = body.substringBefore(" as ").trim()
        if (identifier.isNotEmpty()) {
            return identifier
        }
        return null
    }

    private fun getResourceAbsolutePath(resource: Resource, identifier: Identifier): String {
        val rootPath = (resource.source() as PathPackResources).root
        return rootPath.resolve("data").resolve(identifier.namespace).resolve(identifier.path).absolutePathString()
    }
}