package top.katton.engine

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl
import org.objectweb.asm.*
import top.katton.api.LOGGER
import top.katton.api.ClientPhase
import top.katton.api.ClientScriptEntrypoint
import top.katton.api.InvocationReason
import top.katton.api.ReloadCause
import top.katton.api.ScriptInvocationContext
import top.katton.api.ServerPhase
import top.katton.api.ServerScriptEntrypoint
import top.katton.config.KattonConfigManager
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackFileLimits
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackJarSnapshots
import top.katton.pack.ScriptPackDependencyGraph
import top.katton.pack.ScriptPackScope
import top.katton.pack.ScriptPackScriptFile
import top.katton.registry.KattonRegistry
import top.katton.util.ScriptExecutionContext
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.management.ManagementFactory
import java.net.URL
import java.net.URI
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.notExists
import kotlin.jvm.optionals.getOrNull
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.jvmTarget
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.loadScriptFromJar

/**
 * ScriptEngine compiles source packs together and executes jar packs separately.
 */
object ScriptEngine {
    private const val MAX_SOURCE_COMPILE_CACHE_ENTRIES = 3
    private const val MAX_JAR_LOAD_CACHE_ENTRIES = 16
    private const val MAX_TOP_LEVEL_CLASSES_PER_CACHE = 4_096
    private const val CLIENT_THREAD_WAIT_TIMEOUT_SECONDS = 60L

    private data class CompiledScriptArtifact(
        val compiledScript: CompiledScript,
        val cacheJar: Path?,
        val baseClassLoader: ClassLoader
    )

    private data class SourceCompilationPlan(
        val sourcePacks: List<ScriptPack>,
        val binaryPacks: List<ScriptPack>,
        val scriptSnapshots: List<ScriptSourceSnapshot>,
        val classpathJars: List<Path>,
        val cacheKey: String,
        val classPacks: Map<String, ScriptPack>,
        val baseClassLoader: ClassLoader
    )

    @Suppress("ArrayInDataClass")
    private data class ScriptSourceSnapshot(
        val stagedRelativePath: String,
        val bytes: ByteArray
    )

    @Suppress("ArrayInDataClass")
    private data class ClassFileEntry(
        val className: String,
        val bytes: ByteArray
    )

    private data class EntrypointDescriptor(
        val className: String,
        val methodName: String,
        val methodDescriptor: String,
        val phaseName: String,
        val replay: Boolean
    )

    data class PreparedPackSelection(
        val packs: List<ScriptPack>,
        val rejectedCandidateSyncIds: Set<String>
    )

    private val compiler = JvmScriptCompiler()

    private val externalClasspathJars = linkedSetOf<File>()
    private val hostClasspathLock = Any()

    @Volatile
    private var hostClasspathCache: List<File>? = null

    @JvmStatic
    fun addHostClasspathJar(file: File) {
        if (file.exists() && file.isFile) {
            synchronized(hostClasspathLock) {
                if (externalClasspathJars.add(file.absoluteFile)) hostClasspathCache = null
            }
        }
    }

    @Volatile
    private var cacheDirectory: Path? = null

    private val sourceCompileCache = ConcurrentHashMap<String, CompiledScriptArtifact>()
    private val jarLoadCache = ConcurrentHashMap<String, Optional<CompiledScriptArtifact>>()

    @JvmStatic
    fun setCacheDirectory(path: Path?) {
        cacheDirectory = path?.toAbsolutePath()?.normalize()
    }

    private fun cleanStaleScriptCaches(cacheKey: String) {
        val dir = cacheDirectory ?: return
        runCatching {
            Files.newDirectoryStream(dir).use { stream ->
                stream.filter { it.fileName.toString().let { n -> n.startsWith("source-") && n != "source-$cacheKey" } }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun cleanStaleJavaCaches(cache: Path) {
        val dir = cacheDirectory ?: return
        runCatching {
            Files.newDirectoryStream(dir).use { stream ->
                stream.filter { it.fileName.toString().let { n -> n.startsWith("java-") && n != cache.fileName.toString() } }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun <V> trimCache(cache: ConcurrentHashMap<String, V>, currentKey: String, maxEntries: Int) {
        val overflow = cache.size - maxEntries
        if (overflow <= 0) {
            return
        }
        cache.keys
            .asSequence()
            .filter { it != currentKey }
            .take(overflow)
            .forEach(cache::remove)
    }

    /**
     * Writes the compiled class files from an in-memory compiled module into a jar
     * for inspection. These jars are write-only — the Kotlin scripting compiler's
     * lazily-loaded-from-classpath mechanism is incompatible with importScripts
     * compilations, so we always compile fresh in memory.
     */
    private fun writeCompiledScriptToJar(script: CompiledScript, targetJar: Path) {
        val module = (script as? KJvmCompiledScript)?.getCompiledModule() as? KJvmCompiledModuleInMemoryImpl ?: return
        runCatching {
            Files.createDirectories(targetJar.parent)
            val manifest = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes.putValue("Created-By", "Katton ScriptEngine")
            }
            JarOutputStream(
                Files.newOutputStream(targetJar, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                manifest
            ).use { jos ->
                for ((entryName, bytes) in module.compilerOutputFiles) {
                    jos.putNextEntry(JarEntry(entryName))
                    jos.write(bytes)
                    jos.closeEntry()
                }
            }
            LOGGER.info("Persisted compiled source jar to {}", targetJar)
        }.onFailure {
            LOGGER.warn("Failed to persist compiled source jar to {}", targetJar, it)
        }
    }

    @JvmStatic
    fun compileAndExecuteAll(packs: Collection<ScriptPack>, environment: ScriptEnvironment): Boolean {
        return compileAndExecuteAll(packs, legacyInvocation(packs, environment), null)
    }

    @JvmStatic
    fun compileAndExecuteAll(
        packs: Collection<ScriptPack>,
        environment: ScriptEnvironment,
        progressReporter: ((String) -> Unit)?
    ): Boolean {
        return compileAndExecuteAll(packs, legacyInvocation(packs, environment), progressReporter)
    }

    /** Compiles and resolves a candidate snapshot without invoking any entrypoints. */
    fun prepareAll(packs: Collection<ScriptPack>, invocation: ScriptInvocation): Boolean {
        val discoveredPacks = packs.filter { it.enabled }.toList()
        if (discoveredPacks.isEmpty()) return true
        val graph = ScriptPackDependencyGraph.resolve(discoveredPacks)
        graph.errors.forEach(LOGGER::error)
        if (graph.invalidPacks.any { it.scope == ScriptPackScope.SERVER_CACHE }) return false
        val enabledPacks = graph.orderedPacks
        if (enabledPacks.isEmpty()) return true
        val dependencySelection = ScriptDependencyManager.resolve(
            enabledPacks,
            invocation.environment,
            invocation.phaseName
        )
        if (dependencySelection.errors.isNotEmpty()) {
            dependencySelection.errors.forEach(LOGGER::error)
        }
        if (enabledPacks.filterNot(dependencySelection.validPacks::contains)
                .any { it.scope == ScriptPackScope.SERVER_CACHE }) return false

        val validPacks = dependencySelection.validPacks
        if (validPacks.isEmpty()) return true
        val globalJarPacks = validPacks.filter { it.scope == ScriptPackScope.GLOBAL && it.kind == ScriptPackKind.JAR }
        val plan = try {
            buildSourceCompilationPlan(validPacks, globalJarPacks, dependencySelection)
        } catch (failure: JavaSourceCompilationException) {
            LOGGER.error(failure.message)
            return false
        }
        if (plan != null && loadCompiledSourceArtifact(plan, invocation.environment, null) == null) return false
        val baseLoader = plan?.baseClassLoader ?: createBaseClassLoader(dependencySelection)
        return validPacks.asSequence()
            .filter { it.kind == ScriptPackKind.JAR }
            .all { pack ->
                ScriptPackJarSnapshots.materialize(pack) != null &&
                    runCatching { loadJarPack(pack, baseLoader, dependencySelection.fingerprints) }.getOrNull() != null
            }
    }

    /**
     * Prepares independent dependency components separately. A broken local
     * component falls back to its last-known-good pack snapshots without
     * preventing unrelated components from updating.
     */
    fun prepareWithFallback(
        candidates: Collection<ScriptPack>,
        previous: Collection<ScriptPack>,
        invocation: ScriptInvocation
    ): PreparedPackSelection? {
        val enabledCandidates = candidates.filter { it.enabled }
        val graph = ScriptPackDependencyGraph.resolve(enabledCandidates)
        if (graph.errors.isNotEmpty()) {
            graph.errors.forEach(LOGGER::error)
            ScriptIssueReporter.report(
                "Katton script pack dependencies are unavailable",
                graph.errors.joinToString("\n")
            )
        }
        if (graph.invalidPacks.any { it.scope == ScriptPackScope.SERVER_CACHE }) return null

        val accepted = mutableListOf<ScriptPack>()
        val rejected = graph.invalidPacks.mapTo(linkedSetOf()) { it.syncId }
        ScriptPackDependencyGraph.compilationGroups(graph.orderedPacks).forEach { group ->
            val platformSelection = ScriptDependencyManager.resolve(
                group,
                invocation.environment,
                invocation.phaseName
            )
            if (platformSelection.validPacks.size != group.size) {
                platformSelection.errors.forEach(LOGGER::error)
                if (platformSelection.errors.isNotEmpty()) {
                    ScriptIssueReporter.report(
                        "Katton script dependencies are unavailable",
                        platformSelection.errors.joinToString("\n")
                    )
                }
                if (group.any { it.scope == ScriptPackScope.SERVER_CACHE }) return null
                rejected += group.map { it.syncId }
                return@forEach
            }
            if (prepareAll(group, invocation)) {
                accepted += group
                return@forEach
            }
            if (group.any { it.scope == ScriptPackScope.SERVER_CACHE }) return null

            val groupIds = group.mapTo(hashSetOf()) { it.syncId }
            rejected += groupIds
        }

        val fallbackCandidates = previous.filter { it.enabled && it.syncId in rejected }
        val fallbackIds = fallbackCandidates.mapTo(hashSetOf()) { it.syncId }
        val fallbackGraph = ScriptPackDependencyGraph.resolve(accepted + fallbackCandidates)
        if (fallbackGraph.errors.isNotEmpty()) {
            fallbackGraph.errors.forEach(LOGGER::error)
        }
        ScriptPackDependencyGraph.compilationGroups(fallbackGraph.orderedPacks)
            .filter { group -> group.any { it.syncId in fallbackIds } }
            .forEach { group ->
                val platformSelection = ScriptDependencyManager.resolve(
                    group,
                    invocation.environment,
                    invocation.phaseName
                )
                if (platformSelection.validPacks.size == group.size && prepareAll(group, invocation)) {
                    accepted += group.filter { it.syncId in fallbackIds }
                }
        }

        if (rejected.isNotEmpty()) {
            ScriptIssueReporter.report(
                "Katton retained last-known-good script packs",
                "Rejected candidate components: ${rejected.sorted().joinToString()}"
            )
        }
        val finalSelection = ScriptPackDependencyGraph.resolve(accepted.distinctBy { it.syncId })
        if (finalSelection.errors.isNotEmpty()) {
            finalSelection.errors.forEach(LOGGER::error)
            ScriptIssueReporter.report(
                "Katton script pack fallback dependencies are unavailable",
                finalSelection.errors.joinToString("\n")
            )
        }
        val ordered = finalSelection.orderedPacks
        return PreparedPackSelection(ordered, rejected)
    }

    fun compileAndExecuteAll(
        packs: Collection<ScriptPack>,
        invocation: ScriptInvocation,
        progressReporter: ((String) -> Unit)? = null
    ): Boolean {
        val discoveredPacks = packs.filter { it.enabled }.toList()
        if (discoveredPacks.isEmpty()) return true

        val graph = ScriptPackDependencyGraph.resolve(discoveredPacks)
        if (graph.errors.isNotEmpty()) {
            graph.errors.forEach(LOGGER::error)
            ScriptIssueReporter.report(
                title = "Katton script pack dependencies are unavailable",
                detail = graph.errors.joinToString("\n")
            )
        }
        if (graph.invalidPacks.any { it.scope == ScriptPackScope.SERVER_CACHE }) return false
        val enabledPacks = graph.orderedPacks
        if (enabledPacks.isEmpty()) return true

        val dependencySelection = ScriptDependencyManager.resolve(
            enabledPacks,
            invocation.environment,
            invocation.phaseName
        )
        if (dependencySelection.errors.isNotEmpty()) {
            dependencySelection.errors.forEach(LOGGER::error)
            ScriptIssueReporter.report(
                title = "Katton script dependencies are unavailable",
                detail = dependencySelection.errors.joinToString("\n")
            )
        }
        val strictFailure = enabledPacks
            .filterNot(dependencySelection.validPacks::contains)
            .any { it.scope == ScriptPackScope.SERVER_CACHE }
        if (strictFailure) return false
        if (dependencySelection.validPacks.isEmpty()) return true

        val globalJarPacks = dependencySelection.validPacks
            .filter { it.scope == ScriptPackScope.GLOBAL && it.kind == ScriptPackKind.JAR }
        return compileAndExecute(
            dependencySelection.validPacks,
            invocation,
            globalJarPacks,
            dependencySelection,
            progressReporter
        )
    }

    private fun compileAndExecute(
        packs: List<ScriptPack>,
        invocation: ScriptInvocation,
        extraClasspathJars: List<ScriptPack>,
        dependencySelection: ScriptDependencySelection,
        progressReporter: ((String) -> Unit)?
    ): Boolean {
        val environment = invocation.environment
        val sourcePackCount = packs.count { it.scripts.isNotEmpty() }
        val jarPackCount = packs.count { it.kind == ScriptPackKind.JAR }
        var ok = true
        LOGGER.info(
            "Preparing {} {} script packs in scope {} (source={}, jar={})",
            packs.size,
            environment.name.lowercase(Locale.ROOT),
            packs.first().scope,
            sourcePackCount,
            jarPackCount
        )

        reportProgress(progressReporter, "katton.reload.common.prepare_scripts")
        registerConfigs(packs)

        val sourcePlan = try {
            buildSourceCompilationPlan(
                packs,
                extraClasspathJars,
                dependencySelection,
                progressReporter
            )
        } catch (failure: JavaSourceCompilationException) {
            LOGGER.error(failure.message)
            return false
        }
        val sourceArtifact = if (sourcePlan != null) {
            LOGGER.info(
                "Compiling {} source packs together with {} jar dependencies for {}",
                sourcePlan.sourcePacks.size,
                sourcePlan.binaryPacks.size,
                environment.name.lowercase(Locale.ROOT)
            )
            val artifact = loadCompiledSourceArtifact(sourcePlan, environment, progressReporter)
            if (artifact == null) ok = false
            artifact
        } else null

        // Execute entrypoints in the dependency graph's topological pack order.
        // Source packs share one compiler artifact, but each invocation filters
        // that artifact to the classes owned by the current pack so JAR and
        // source entrypoints can be interleaved correctly.
        packs.forEach { pack ->
            if (pack.scripts.isNotEmpty() && sourcePlan != null && sourceArtifact != null) {
                reportProgress(progressReporter, "katton.reload.common.execute_source_scripts")
                runBlocking {
                    val executionResult = executeCombined(
                        artifact = sourceArtifact,
                        invocation = invocation,
                        scope = pack.scope,
                        classPacks = sourcePlan.classPacks,
                        includedPackSyncId = pack.syncId,
                        label = "source pack ${pack.manifest.name}"
                    )
                    ok = logExecutionResult(pack.manifest.name, environment, executionResult) && ok
                }
            }
            if (pack.kind == ScriptPackKind.JAR) {
                reportProgress(progressReporter, "katton.reload.common.load_jar_scripts")
                val artifact = loadJarPack(
                    pack,
                    sourcePlan?.baseClassLoader ?: createBaseClassLoader(dependencySelection),
                    dependencySelection.fingerprints
                )
                if (artifact == null) {
                    ok = false
                    return@forEach
                }
                reportProgress(progressReporter, "katton.reload.common.execute_jar_scripts")
                runBlocking {
                    val executionResult = executeCombined(
                        artifact = artifact,
                        invocation = invocation,
                        scope = pack.scope,
                        defaultPack = pack,
                        label = "jar pack ${pack.manifest.name}"
                    )
                    ok = logExecutionResult(pack.manifest.name, environment, executionResult) && ok
                }
            }
        }
        return ok
    }

    private fun legacyInvocation(packs: Collection<ScriptPack>, environment: ScriptEnvironment): ScriptInvocation {
        val firstScope = packs.firstOrNull()?.scope ?: ScriptPackScope.WORLD
        return when (environment) {
            ScriptEnvironment.SERVER -> ScriptInvocation.server(
                if (firstScope == ScriptPackScope.GLOBAL) ServerPhase.BOOTSTRAP else ServerPhase.READY,
                InvocationReason.INITIAL_LOAD,
                ReloadCause.SERVER_START,
                top.katton.Katton.server
            )
            ScriptEnvironment.CLIENT -> ScriptInvocation.client(
                if (firstScope == ScriptPackScope.GLOBAL) ClientPhase.READY else ClientPhase.REGISTRY_SETUP,
                InvocationReason.INITIAL_LOAD,
                ReloadCause.CLIENT_JOIN
            )
        }
    }

    private fun registerConfigs(packs: List<ScriptPack>) {
        for (pack in packs) {
            KattonConfigManager.registerPack(pack)
            val configId = KattonConfigManager.configId(pack)

            // Register FQCN → packId mappings for script files
            for (script in pack.scripts) {
                val content = runCatching { String(script.bytes, StandardCharsets.UTF_8) }.getOrNull() ?: continue
                val fqcn = KattonConfigManager.deriveFqcn(content, script.relativePath.substringAfterLast('/'))
                KattonConfigManager.registerFqcnMapping(fqcn, configId)
            }

            // For jar packs: pre-scan compiled jar for FQCN mappings
            if (pack.kind == ScriptPackKind.JAR) {
                ScriptPackJarSnapshots.materialize(pack)?.let { registerJarFqcnMappings(it, configId) }
            }
        }
    }

    private fun registerJarFqcnMappings(jarPath: Path, syncId: String) {
        if (!Files.isRegularFile(jarPath)) return
        runCatching {
            JarFile(jarPath.toFile()).use { jar ->
                jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.contains('$') }
                    .forEach { entry ->
                        val fqcn = entry.name.removeSuffix(".class").replace('/', '.')
                        KattonConfigManager.registerFqcnMapping(fqcn, syncId)
                    }
            }
        }.onFailure {
            LOGGER.warn("Failed to scan jar '{}' for config FQCN mappings", jarPath, it)
        }
    }

    private fun buildSourceCompilationPlan(
        packs: Collection<ScriptPack>,
        extraClasspathJars: List<ScriptPack> = emptyList(),
        dependencySelection: ScriptDependencySelection,
        progressReporter: ((String) -> Unit)? = null
    ): SourceCompilationPlan? {
        val sourcePacks = packs
            .filter { it.scripts.isNotEmpty() }
        if (sourcePacks.isEmpty()) {
            return null
        }

        val binaryPacks = (packs.filter { it.kind == ScriptPackKind.JAR && it.compiledJar != null } + extraClasspathJars)
            .distinctBy { it.syncId }
            .sortedBy { it.syncId }
        val classpathJars = binaryPacks.mapNotNull(ScriptPackJarSnapshots::materialize).toMutableList()
        if (classpathJars.size != binaryPacks.size) {
            LOGGER.warn("Unable to materialize every binary script pack for immutable compilation")
            return null
        }
        classpathJars += dependencySelection.resolved.flatMap { it.classpath }

        // Compile the immutable bytes captured by ScriptPackManager. Reading the
        // original paths again would create a TOCTOU gap: an editor could save a
        // file after hashing but before compilation, poisoning the old cache key
        // with different bytecode.
        val scriptSnapshots = sourcePacks.flatMapIndexed { packIndex, pack ->
            pack.scripts.sortedBy { it.relativePath }.map { script ->
                ScriptSourceSnapshot(
                    stagedRelativePath = "pack-$packIndex/${script.relativePath}",
                    bytes = script.bytes
                )
            }
        }

        val classPacks = buildScriptClassPackMap(sourcePacks)
        val baseClassLoader = createBaseClassLoader(dependencySelection)

        // Compile .java files from enabled directory packs (independent of script collection)
        val javaFingerprints = dependencySelection.fingerprints + binaryPacks.map { pack ->
            "script-pack:${pack.syncId}@${pack.codeHash}"
        }
        val classpathFromJava = compileJavaFromPacks(packs, classpathJars, javaFingerprints, progressReporter)
        if (classpathFromJava != null) classpathJars.add(classpathFromJava)
        // The Java cache jar name contains its source/dependency hash. Including
        // it here prevents reuse of Kotlin bytecode compiled against an older
        // version of helper Java classes.
        val sourceFingerprints = dependencySelection.fingerprints + listOfNotNull(
            classpathFromJava?.fileName?.toString()?.let { "compiled-java:$it" }
        )
        val cacheKey = buildSourceCacheKey(sourcePacks, binaryPacks, sourceFingerprints)

        return SourceCompilationPlan(
            sourcePacks = sourcePacks,
            binaryPacks = binaryPacks,
            scriptSnapshots = scriptSnapshots,
            classpathJars = classpathJars,
            cacheKey = cacheKey,
            classPacks = classPacks,
            baseClassLoader = baseClassLoader
        )
    }

    private fun buildScriptClassPackMap(sourcePacks: List<ScriptPack>): Map<String, ScriptPack> {
        val classPacks = LinkedHashMap<String, ScriptPack>()
        sourcePacks.forEach { pack ->
            pack.scripts.forEach { script ->
                val content = runCatching { String(script.bytes, StandardCharsets.UTF_8) }.getOrNull() ?: return@forEach
                val fqcn = KattonConfigManager.deriveFqcn(content, script.relativePath.substringAfterLast('/'))
                classPacks[fqcn] = pack
            }
        }
        return classPacks
    }

    /**
     * Compiles the `.java` files captured in the immutable pack snapshots and
     * returns the resulting jar (or null if none).
     *
     * Reusing [ScriptPack.contentFiles] is both faster than walking every pack
     * a second time and security-sensitive: compilation must consume exactly
     * the bytes that were bounded, hashed, and (for remote packs) verified.
     */
    private fun compileJavaFromPacks(
        packs: Collection<ScriptPack>,
        classpath: List<Path>,
        dependencyFingerprints: List<String>,
        progressReporter: ((String) -> Unit)?
    ): Path? {
        val javaFiles = packs
            .asSequence()
            .filter { it.kind == ScriptPackKind.DIRECTORY }
            .flatMap { pack -> pack.contentFiles.asSequence() }
            .filter { file ->
                file.relativePath.endsWith(".java", ignoreCase = true) &&
                    !file.relativePath.startsWith("assets/") &&
                    !file.relativePath.startsWith("data/")
            }
            .map { file ->
                ScriptPackScriptFile(file.relativePath, file.absolutePath, file.bytes)
            }
            .toList()
        if (javaFiles.isEmpty()) {
            LOGGER.debug("No .java files found in packs")
            return null
        }
        LOGGER.info("Compiling {} .java files from {} packs", javaFiles.size, packs.size)
        reportProgress(progressReporter, "katton.reload.common.compile_java_sources")
        return when (val result = JavaCompilationUtil.compileToJar(javaFiles, cacheDirectory, classpath, dependencyFingerprints)) {
            JavaCompilationUtil.Result.NoSources -> null
            is JavaCompilationUtil.Result.Success -> result.jar.also {
                cleanStaleJavaCaches(it)
                LOGGER.info("Java compilation result: {}", it)
            }
            is JavaCompilationUtil.Result.Failure -> throw JavaSourceCompilationException(result.detail)
        }
    }

    private class JavaSourceCompilationException(detail: String) :
        RuntimeException("Java source compilation rejected the script candidate: $detail")

    private fun loadCompiledSourceArtifact(
        plan: SourceCompilationPlan,
        environment: ScriptEnvironment,
        progressReporter: ((String) -> Unit)?
    ): CompiledScriptArtifact? {
        sourceCompileCache[plan.cacheKey]?.let {
            LOGGER.info("Reusing in-memory combined source compilation cache {}", plan.cacheKey)
            return it
        }

        val cacheJar = resolveSourceCacheJar(plan.cacheKey)
        // Note: disk cache is write-only for now. The Kotlin scripting compiler's
        // lazily-loaded-from-classpath variant does not work with importScripts
        // compilations — it fails to locate metadata for the dummy script.
        // In-memory cache (above) handles intra-session reuse.
        // On game restart, recompilation is sub-second and unavoidable.

        val dummyScript = "".toScriptSource()
        val stagedSources = runCatching { stageScriptSnapshots(plan.scriptSnapshots) }
            .getOrElse { failure ->
                LOGGER.warn("Failed to stage immutable Kotlin source snapshots", failure)
                return null
            }
        val compileResult = try {
            val compilationConfig = createCompilationConfiguration(
                orderedScriptPaths = stagedSources.second,
                classpathJars = plan.classpathJars
            )
            reportProgress(progressReporter, "katton.reload.common.compile_source_scripts")
            runBlocking {
                compiler(dummyScript, compilationConfig)
            }
        } finally {
            deleteTemporaryTree(stagedSources.first)
        }
        logCompileResult(plan.sourcePacks, compileResult)
        if (compileResult is ResultWithDiagnostics.Failure) {
            ScriptIssueReporter.report(
                title = "Katton script compilation failed",
                detail = buildString {
                    appendLine("Environment: ${environment.name.lowercase(Locale.ROOT)}")
                    appendLine("Packs: ${plan.sourcePacks.joinToString(", ") { it.manifest.name }}")
                    appendLine()
                    append(formatDiagnostics(compileResult.reports))
                }
            )
        }

        val compiledScript = (compileResult as? ResultWithDiagnostics.Success)?.value
        val artifact = compiledScript?.let { CompiledScriptArtifact(it, cacheJar, plan.baseClassLoader) }
        if (artifact != null) {
            LOGGER.info(
                "Stored combined source compilation result for {} packs with cache key {}",
                plan.sourcePacks.size,
                plan.cacheKey
            )
            sourceCompileCache[plan.cacheKey] = artifact
            trimCache(sourceCompileCache, plan.cacheKey, MAX_SOURCE_COMPILE_CACHE_ENTRIES)
            cleanStaleScriptCaches(plan.cacheKey)

            // Persist compiled classes to disk for inspection only.
            // These jars cannot be reloaded directly due to Kotlin compiler format constraints.
            if (cacheJar != null) {
                writeCompiledScriptToJar(compiledScript, cacheJar)
            }
        }
        return artifact
    }

    private fun reportProgress(progressReporter: ((String) -> Unit)?, messageKey: String) {
        if (progressReporter == null) return
        runCatching {
            progressReporter(messageKey)
        }.onFailure {
            LOGGER.warn("Failed to update script reload progress to {}", messageKey, it)
        }
    }

    private fun loadJarPack(
        pack: ScriptPack,
        baseClassLoader: ClassLoader,
        dependencyFingerprints: List<String>
    ): CompiledScriptArtifact? {
        val cacheKey = buildJarLoadCacheKey(pack, dependencyFingerprints)
        if (jarLoadCache.containsKey(cacheKey)) {
            LOGGER.info("Reusing jar load cache for {}", pack.manifest.name)
            return jarLoadCache[cacheKey]?.getOrNull()
        }

        val jarPath = ScriptPackJarSnapshots.materialize(pack)
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            LOGGER.warn("Skipping jar pack {} because its immutable snapshot is unavailable", pack.manifest.name)
            jarLoadCache[cacheKey] = Optional.empty()
            trimCache(jarLoadCache, cacheKey, MAX_JAR_LOAD_CACHE_ENTRIES)
            return null
        }

        LOGGER.info("Loading jar pack {} from {}", pack.manifest.name, jarPath)
        val compiledScript = runCatching { jarPath.toFile().loadScriptFromJar() }
            .getOrElse {
                LOGGER.warn("Failed to load compiled script jar {}", jarPath, it)
                null
            }

        val artifact = compiledScript?.let { CompiledScriptArtifact(it, jarPath, baseClassLoader) }
        if (artifact == null) {
            LOGGER.info("Jar pack {} has no loadable script metadata, using it as classpath only", pack.manifest.name)
        } else {
            LOGGER.info("Loaded executable compiled script jar for pack {}", pack.manifest.name)
        }
        jarLoadCache[cacheKey] = Optional.ofNullable(artifact)
        trimCache(jarLoadCache, cacheKey, MAX_JAR_LOAD_CACHE_ENTRIES)
        return artifact
    }

    private fun buildJarLoadCacheKey(pack: ScriptPack, dependencyFingerprints: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed("katton-jar-load-cache-v2".toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(pack.syncId.toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(pack.codeHash.toByteArray(StandardCharsets.UTF_8))
        digest.updateInt(dependencyFingerprints.size)
        dependencyFingerprints.sorted().forEach { fingerprint ->
            digest.updateFramed(fingerprint.toByteArray(StandardCharsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun logCompileResult(sourcePacks: List<ScriptPack>, compileResult: ResultWithDiagnostics<CompiledScript>) {
        val label = sourcePacks.joinToString(", ") { it.manifest.name }
        val compileReports = compileResult.reports.filter { it.severity >= ScriptDiagnostic.Severity.INFO }
        if (compileReports.isNotEmpty()) {
            LOGGER.info("[{}] {}", label, compileReports.joinToString("\n"))
        }

        when (compileResult) {
            is ResultWithDiagnostics.Success -> LOGGER.info("Compile succeeded for source packs: {}", label)
            is ResultWithDiagnostics.Failure -> LOGGER.error("Compile failed for source packs: {}", label)
        }
    }

    private fun logExecutionResult(
        label: String,
        environment: ScriptEnvironment,
        executionResult: ResultWithDiagnostics<EvaluationResult>
    ): Boolean {
        when (executionResult) {
            is ResultWithDiagnostics.Success -> {
                val summary = (executionResult.value.returnValue as? ResultValue.Value)?.value as? Map<*, *>
                if (summary != null) {
                    LOGGER.info(
                        "[{}] Execution finished: total={}, success={}, failure={}",
                        label,
                        summary["totalAttempted"],
                        summary["successCount"],
                        summary["failureCount"]
                    )
                    val errorMessages = summary["errorMessages"] as? List<*>
                    if (!errorMessages.isNullOrEmpty()) {
                        LOGGER.warn("[{}] Execution errors:\n{}", label, errorMessages.joinToString("\n"))
                        ScriptIssueReporter.report(
                            title = "Katton script execution failed",
                            detail = buildString {
                                appendLine("Environment: ${environment.name.lowercase(Locale.ROOT)}")
                                appendLine("Target: $label")
                                appendLine()
                                append(errorMessages.joinToString("\n") { it.toString() })
                            }
                        )
                        return false
                    }
                } else {
                    LOGGER.info("[{}] Execution finished without summary", label)
                }
                return true
            }

            is ResultWithDiagnostics.Failure -> {
                LOGGER.error("[{}] Execution failed: {}", label, executionResult.reports.joinToString("\n"))
                ScriptIssueReporter.report(
                    title = "Katton script execution failed",
                    detail = buildString {
                        appendLine("Environment: ${environment.name.lowercase(Locale.ROOT)}")
                        appendLine("Target: $label")
                        appendLine()
                        append(formatDiagnostics(executionResult.reports))
                    }
                )
                return false
            }
        }
    }

    private fun formatDiagnostics(reports: List<ScriptDiagnostic>): String {
        val importantReports = reports
            .filter { it.severity >= ScriptDiagnostic.Severity.WARNING }
            .ifEmpty { reports }
        return importantReports
            .joinToString("\n") { it.toString() }
            .ifBlank { "The compiler did not provide diagnostics." }
    }

    private suspend fun executeCombined(
        artifact: CompiledScriptArtifact,
        invocation: ScriptInvocation,
        scope: ScriptPackScope,
        classPacks: Map<String, ScriptPack> = emptyMap(),
        defaultPack: ScriptPack? = null,
        includedPackSyncId: String? = null,
        label: String
    ): ResultWithDiagnostics<EvaluationResult> {
        val script = artifact.compiledScript

        val pluginLoader = artifact.baseClassLoader
        val savedCcl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = pluginLoader
        try {
            return executeCombinedWithClassLoader(
                script,
                artifact,
                invocation,
                scope,
                classPacks,
                defaultPack,
                includedPackSyncId,
                label
            )
        } finally {
            Thread.currentThread().contextClassLoader = savedCcl
        }
    }

    private suspend fun executeCombinedWithClassLoader(
        script: CompiledScript,
        artifact: CompiledScriptArtifact,
        invocation: ScriptInvocation,
        scope: ScriptPackScope,
        classPacks: Map<String, ScriptPack>,
        defaultPack: ScriptPack?,
        includedPackSyncId: String?,
        label: String
    ): ResultWithDiagnostics<EvaluationResult> {
        val environment = invocation.environment
        val evaluationConfig = ScriptEvaluationConfiguration {
            enableScriptsInstancesSharing()
            jvm {
                baseClassLoader(artifact.baseClassLoader)
            }
        }
        val rootClass = when (val res = script.getClass(evaluationConfig)) {
            is ResultWithDiagnostics.Success -> res.value
            is ResultWithDiagnostics.Failure -> return res
        }

        val loader = rootClass.java.classLoader
        val rootName = rootClass.qualifiedName
        val entrypointsByClass = collectEntrypoints(script, artifact.cacheJar, environment)
            .filterKeys { it != rootName }
            .filterKeys { fqcn ->
                includedPackSyncId == null || classPacks[fqcn]?.syncId == includedPackSyncId
            }
        LOGGER.info(
            "Discovered {} top-level compiled classes for {} in {} environment",
            entrypointsByClass.size,
            label,
            environment.name.lowercase(Locale.ROOT)
        )
        var successCount = 0
        var failureCount = 0
        val errorMessages = mutableListOf<String>()

        val packOrder = classPacks.values.distinct().withIndex().associate { (index, pack) -> pack to index }
        val orderedEntrypoints = entrypointsByClass.entries.sortedWith(
            compareBy<Map.Entry<String, List<EntrypointDescriptor>>> {
                classPacks[it.key]?.let(packOrder::get) ?: Int.MAX_VALUE
            }.thenBy { it.key }
        )
        for ((fqcn, entrypoints) in orderedEntrypoints) {
            runCatching {
                val entryPack = classPacks[fqcn] ?: defaultPack
                val entryScope = entryPack?.scope ?: scope
                val clazz = Class.forName(fqcn, false, loader)
                if (entrypoints.isNotEmpty()) {
                    LOGGER.info(
                        "Executing {} entrypoints from {} for {}",
                        entrypoints.size,
                        fqcn,
                        label
                    )
                }

                for (entrypoint in entrypoints) {
                    val validationError = validateEntrypoint(entrypoint, entryScope, environment)
                    if (validationError != null) {
                        failureCount++
                        errorMessages += "pack '${entryPack?.manifest?.id ?: "unknown"}' $fqcn.${entrypoint.methodName}: $validationError"
                        continue
                    }
                    if (entrypoint.phaseName != invocation.phaseName) continue
                    if (!ScriptLifecyclePolicy.shouldInvoke(entryScope, invocation.reason, entrypoint.replay)) continue
                    val pack = entryPack ?: error("Cannot resolve owning pack for $fqcn.${entrypoint.methodName}")
                    val context = invocation.contextFor(pack)
                    val methodType = MethodType.fromMethodDescriptorString(entrypoint.methodDescriptor, loader)
                    if (methodType.parameterCount() !in 0..1 ||
                        (methodType.parameterCount() == 1 && !methodType.parameterType(0).isAssignableFrom(context.javaClass))) {
                        failureCount++
                        errorMessages += "pack '${pack.manifest.id}' $fqcn.${entrypoint.methodName}: ${environment.annotationClassName.substringAfterLast('.')} functions must take no parameters or one compatible ${context.javaClass.simpleName} parameter"
                        continue
                    }

                    ScriptExecutionContext.withEnvironment(environment) {
                        ScriptExecutionContext.withScope(entryScope) {
                            ScriptExecutionContext.withOwner("${entryScope.serializedName}:${invocation.phaseName}:$fqcn") {
                                ScriptExecutionContext.recordCurrentRevision(pack.codeHash)
                                invokeEntrypoint(clazz, entrypoint, methodType, environment, context)
                            }
                        }
                    }
                    successCount++
                }
            }.onFailure {
                failureCount++
                errorMessages += "$fqcn: ${it.message ?: "Unknown error"}"
                LOGGER.warn("Failed to execute {} entrypoints from class: {}", label, fqcn, it)
            }
        }

        val executionSummary = mapOf(
            "successCount" to successCount,
            "failureCount" to failureCount,
            "totalAttempted" to (successCount + failureCount),
            "errorMessages" to errorMessages
        )

        return ResultWithDiagnostics.Success(
            EvaluationResult(
                returnValue = ResultValue.Value(
                    name = "executionSummary",
                    value = executionSummary,
                    type = "Map<String, Any>",
                    scriptClass = rootClass,
                    scriptInstance = executionSummary
                ),
                configuration = evaluationConfig
            )
        )
    }

    private fun validateEntrypoint(
        entrypoint: EntrypointDescriptor,
        scope: ScriptPackScope,
        environment: ScriptEnvironment
    ): String? {
        val valid = ScriptLifecyclePolicy.isValid(scope, environment, entrypoint.phaseName)
        return if (valid) null else "phase ${entrypoint.phaseName} is not valid for ${scope.serializedName} ${environment.name.lowercase(Locale.ROOT)} packs"
    }

    private fun createCompilationConfiguration(
        orderedScriptPaths: List<Path>,
        classpathJars: List<Path>
    ): ScriptCompilationConfiguration {
        // Source packs are compiled as one unit so Kotlin symbols can be referenced across pack boundaries.
        val currentHostClasspath = resolveHostClasspath()
        return ScriptCompilationConfiguration {
            importScripts(orderedScriptPaths.map { path -> path.toFile().toScriptSource() })
            jvm {
                jvmTarget("25")
                dependenciesFromCurrentContext(wholeClasspath = true)
                updateClasspath(currentHostClasspath)
                if (classpathJars.isNotEmpty()) {
                    updateClasspath(classpathJars.map(Path::toFile))
                }
            }
        }
    }

    /**
     * Kotlin's importScripts compiler currently requires file-backed sources.
     * Stage the already scanned bytes in a fresh tree so compilation cannot
     * reopen mutable or remotely cached pack paths after hash verification.
     */
    private fun stageScriptSnapshots(snapshots: List<ScriptSourceSnapshot>): Pair<Path, List<Path>> {
        val root = Files.createTempDirectory("katton-kotlin-sources-")
        return try {
            val paths = snapshots.map { snapshot ->
                val target = root.resolve(snapshot.stagedRelativePath).normalize()
                require(target.startsWith(root) && target != root) {
                    "Kotlin source snapshot escaped its staging root: ${snapshot.stagedRelativePath}"
                }
                Files.createDirectories(target.parent)
                Files.write(target, snapshot.bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                target
            }
            root to paths
        } catch (failure: Throwable) {
            deleteTemporaryTree(root)
            throw failure
        }
    }

    private fun deleteTemporaryTree(root: Path) {
        runCatching {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }.onFailure { failure ->
            LOGGER.debug("Failed to clean temporary Kotlin source snapshots at {}", root, failure)
        }
    }

    private fun createBaseClassLoader(selection: ScriptDependencySelection): ClassLoader {
        val parent = selectScriptHostClassLoader()
        val delegates = selection.resolved.mapNotNull { it.classLoader }.filter { it !== parent }.distinct()
        return if (delegates.isEmpty()) parent else DependencyDelegatingClassLoader(parent, delegates)
    }

    /**
     * Keep compiled scripts parented to the loader that owns Katton's runtime
     * classes. Paper performs the asynchronous initial reload on a worker whose
     * context loader can be the application loader; using it directly causes the
     * script loader to define a second copy of entrypoint context classes from the
     * plugin jar, making otherwise valid parameters fail identity checks.
     */
    internal fun selectScriptHostClassLoader(
        contextLoader: ClassLoader? = Thread.currentThread().contextClassLoader
    ): ClassLoader {
        val hostClass = ScriptEngine::class.java
        val hostLoader = hostClass.classLoader ?: return contextLoader ?: ClassLoader.getSystemClassLoader()
        val contextOwnsHostIdentity = contextLoader != null && runCatching {
            Class.forName(hostClass.name, false, contextLoader) === hostClass
        }.getOrDefault(false)
        return if (contextOwnsHostIdentity) contextLoader else hostLoader
    }

    private fun resolveHostClasspath(): List<File> {
        hostClasspathCache?.let { return it }
        return synchronized(hostClasspathLock) {
            hostClasspathCache?.let { return@synchronized it }
            val files = LinkedHashSet<File>()

            fun addFile(file: File?) {
                if (file == null || !file.exists()) return
                files += runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
            }

            fun addUrl(url: URL?) {
                if (url == null) return
                when (url.protocol) {
                    "file" -> runCatching { addFile(Paths.get(url.toURI()).toFile()) }
                    "jar" -> {
                        val spec = url.file.substringBefore("!/")
                        runCatching { addUrl(URI.create(spec).toURL()) }
                    }
                }
            }

            fun addClassSource(clazz: Class<*>) {
                val codeUrl = clazz.protectionDomain?.codeSource?.location
                if (codeUrl != null) {
                    addUrl(codeUrl)
                    return
                }
                val resourcePath = "/${clazz.name.replace('.', '/')}.class"
                val resourceUrl = runCatching { clazz.getResource(resourcePath) }.getOrNull()
                if (resourceUrl != null) {
                    addUrl(resourceUrl)
                }
            }

            fun addClassLoader(classLoader: ClassLoader?) {
                val seen = Collections.newSetFromMap(IdentityHashMap<ClassLoader, Boolean>())
                var current = classLoader
                while (current != null && seen.add(current)) {
                    if (current is URLClassLoader) {
                        current.urLs.forEach(::addUrl)
                    }
                    current = current.parent
                }
            }

            ManagementFactory.getRuntimeMXBean().classPath
                .split(File.pathSeparatorChar)
                .asSequence()
                .filter { it.isNotBlank() }
                .map(::File)
                .forEach(::addFile)

            val contextLoader = Thread.currentThread().contextClassLoader
            val scriptEngineLoader = ScriptEngine::class.java.classLoader
            addClassLoader(contextLoader)
            addClassLoader(scriptEngineLoader)

            listOf(
                ScriptEngine::class.java,
                KattonRegistry::class.java,
                Unit::class.java,
                Suppress::class.java,
                JvmScriptCompiler::class.java,
                CompiledScript::class.java
            ).forEach(::addClassSource)

            val preferredLoader = contextLoader ?: scriptEngineLoader
            listOf(
                "top.katton.Katton",
                "top.katton.paper.KattonPaperPlugin",
                "kotlin.collections.CollectionsKt",
                "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                "org.bukkit.Bukkit",
                "org.bukkit.event.Event",
                "net.minecraft.server.MinecraftServer"
            ).forEach { className ->
                runCatching { Class.forName(className, false, preferredLoader) }
                    .recoverCatching { Class.forName(className, false, scriptEngineLoader) }
                    .getOrNull()
                    ?.let(::addClassSource)
            }

            externalClasspathJars.forEach(::addFile)

            files.toList().also {
                hostClasspathCache = it
                LOGGER.info("Resolved {} host classpath entries for script compilation", it.size)
            }
        }
    }

    private fun resolveSourceCacheJar(cacheKey: String): Path? {
        val root = cacheDirectory ?: return null
        return runCatching {
            if(root.notExists()) Files.createDirectories(root)
            root.resolve("source-$cacheKey.jar")
        }.getOrElse {
            LOGGER.warn("Failed to initialize script compile cache directory {}", root, it)
            null
        }
    }

    private fun buildSourceCacheKey(
        sourcePacks: List<ScriptPack>,
        binaryPacks: List<ScriptPack>,
        dependencyFingerprints: List<String>
    ): String {
        // Both source pack content and binary jar hashes affect the combined compilation result.
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed("katton-source-pack-cache-v3".toByteArray(StandardCharsets.UTF_8))
        digest.updateInt(sourcePacks.size)
        sourcePacks.forEach { pack ->
            digest.updateFramed(pack.syncId.toByteArray(StandardCharsets.UTF_8))
            digest.updateFramed(pack.codeHash.toByteArray(StandardCharsets.UTF_8))
        }
        digest.updateInt(binaryPacks.size)
        binaryPacks.forEach { pack ->
            digest.updateFramed(pack.syncId.toByteArray(StandardCharsets.UTF_8))
            digest.updateFramed(pack.codeHash.toByteArray(StandardCharsets.UTF_8))
        }
        digest.updateInt(dependencyFingerprints.size)
        dependencyFingerprints.sorted().forEach { fingerprint ->
            digest.updateFramed(fingerprint.toByteArray(StandardCharsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun MessageDigest.updateFramed(bytes: ByteArray) {
        updateInt(bytes.size)
        update(bytes)
    }

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr 24).toByte())
        update((value ushr 16).toByte())
        update((value ushr 8).toByte())
        update(value.toByte())
    }

    private fun collectTopLevelClassFiles(script: CompiledScript, cacheJar: Path?): List<ClassFileEntry> {
        val module = (script as? KJvmCompiledScript)?.getCompiledModule()
        if (module is KJvmCompiledModuleInMemoryImpl) {
            return module.compilerOutputFiles.entries
                .asSequence()
                .filter { (path, _) -> path.endsWith(".class") && !path.contains("$") }
                .map { (path, bytes) ->
                    ClassFileEntry(
                        className = path.removeSuffix(".class").replace('/', '.'),
                        bytes = bytes
                    )
                }
                .toList()
        }

        if (cacheJar != null && Files.isRegularFile(cacheJar)) {
            return runCatching {
                JarFile(cacheJar.toFile()).use { jar ->
                    val entries = jar.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.contains("$") }
                        .take(MAX_TOP_LEVEL_CLASSES_PER_CACHE + 1)
                        .toList()
                    require(entries.size <= MAX_TOP_LEVEL_CLASSES_PER_CACHE) {
                        "Compiled script cache contains too many top-level classes"
                    }
                    var retainedBytes = 0L
                    entries.map { entry ->
                        val bytes = jar.getInputStream(entry).use { input ->
                            val content = input.readNBytes(ScriptPackFileLimits.MAX_FILE_BYTES + 1)
                            require(content.size <= ScriptPackFileLimits.MAX_FILE_BYTES) {
                                "Compiled class '${entry.name}' is too large"
                            }
                            content
                        }
                        retainedBytes += bytes.size
                        require(retainedBytes <= ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES) {
                            "Compiled script cache exceeds the class byte budget"
                        }
                        ClassFileEntry(
                            className = entry.name.removeSuffix(".class").replace('/', '.'),
                            bytes = bytes
                        )
                    }
                }
            }.getOrElse {
                LOGGER.warn("Failed to read compiled script jar {}", cacheJar, it)
                emptyList()
            }
        }

        return emptyList()
    }

    private fun collectEntrypoints(
        script: CompiledScript,
        cacheJar: Path?,
        environment: ScriptEnvironment
    ): Map<String, List<EntrypointDescriptor>> {
        val annotationDescriptor = Type.getDescriptor(environment.annotationClass)
        val defaultPhase = when (environment) {
            ScriptEnvironment.CLIENT -> ClientPhase.READY.name
            ScriptEnvironment.SERVER -> ServerPhase.BOOTSTRAP.name
        }
        return collectTopLevelClassFiles(script, cacheJar)
            .sortedBy { it.className }
            .associate { classFile ->
                classFile.className to scanEntrypoints(
                    classFile.bytes,
                    classFile.className,
                    annotationDescriptor,
                    defaultPhase
                )
            }
            .filterValues { it.isNotEmpty() }
    }

    private fun scanEntrypoints(
        classBytes: ByteArray,
        className: String,
        annotationDescriptor: String,
        defaultPhase: String
    ): List<EntrypointDescriptor> {
        val entrypoints = mutableListOf<EntrypointDescriptor>()
        ClassReader(classBytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor? {
                if ((access and Opcodes.ACC_STATIC) == 0) {
                    return null
                }

                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(descriptorName: String, visible: Boolean): AnnotationVisitor? {
                        if (descriptorName == annotationDescriptor) {
                            return object : AnnotationVisitor(Opcodes.ASM9) {
                                var phaseName = defaultPhase
                                var replay = true

                                override fun visit(name: String, value: Any) {
                                    if (name == "replay") replay = value as Boolean
                                }

                                override fun visitEnum(name: String, descriptor: String, value: String) {
                                    if (name == "phase") phaseName = value
                                }

                                override fun visitEnd() {
                                    entrypoints += EntrypointDescriptor(
                                        className = className,
                                        methodName = name,
                                        methodDescriptor = descriptor,
                                        phaseName = phaseName,
                                        replay = replay
                                    )
                                }
                            }
                        }
                        return null
                    }
                }
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        return entrypoints.sortedBy { it.methodName }
    }

    private fun invokeEntrypoint(
        clazz: Class<*>,
        entrypoint: EntrypointDescriptor,
        methodType: MethodType,
        environment: ScriptEnvironment,
        context: ScriptInvocationContext
    ) {
        val lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup())
        val handle = lookup.findStatic(clazz, entrypoint.methodName, methodType)
        val arguments = if (methodType.parameterCount() == 0) emptyList() else listOf(context)
        if (environment != ScriptEnvironment.CLIENT) {
            handle.invokeWithArguments(arguments)
            KattonRegistry.flushPendingRegistrations()
            return
        }
        runOnClientMainThreadAndWait {
            handle.invokeWithArguments(arguments)
            KattonRegistry.flushPendingRegistrations()
        }
    }

    private fun runOnClientMainThreadAndWait(action: () -> Unit) {
        val owner = ScriptExecutionContext.currentScriptOwner()
        val scope = ScriptExecutionContext.currentScriptScope()
        val environment = ScriptExecutionContext.currentScriptEnvironment()
        val contextualAction = {
            ScriptExecutionContext.withEnvironment(environment) {
                ScriptExecutionContext.withScope(scope) {
                    ScriptExecutionContext.withOwner(owner, action)
                }
            }
        }
        val minecraft = runCatching { net.minecraft.client.Minecraft.getInstance() }.getOrNull()
        if (minecraft == null || minecraft.isSameThread) {
            contextualAction()
            return
        }

        val latch = CountDownLatch(1)
        // Do not let a timeout return while an action that already started is
        // still mutating client state. Only queued work can be cancelled.
        val executionState = AtomicInteger(0) // queued, running, cancelled, finished
        var failure: Throwable? = null
        try {
            minecraft.execute {
                if (!executionState.compareAndSet(0, 1)) {
                    latch.countDown()
                    return@execute
                }
                try {
                    contextualAction()
                } catch (t: Throwable) {
                    failure = t
                } finally {
                    executionState.set(3)
                    latch.countDown()
                }
            }
        } catch (rejected: RuntimeException) {
            executionState.compareAndSet(0, 2)
            throw IllegalStateException("Client executor rejected script execution", rejected)
        }

        try {
            if (!latch.await(CLIENT_THREAD_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                if (executionState.compareAndSet(0, 2)) {
                    throw IllegalStateException(
                        "Timed out waiting for client main-thread script execution after " +
                            "$CLIENT_THREAD_WAIT_TIMEOUT_SECONDS seconds"
                    )
                }
                latch.await()
            }
        } catch (interrupted: InterruptedException) {
            val cancelledBeforeStart = executionState.compareAndSet(0, 2)
            if (!cancelledBeforeStart) awaitLatchUninterruptibly(latch)
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while waiting for client main-thread script execution", interrupted)
        }

        failure?.let { throw it }
    }

    private fun awaitLatchUninterruptibly(latch: CountDownLatch) {
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
}
