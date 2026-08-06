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
import top.katton.pack.ScriptPackKind
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

    private data class CompiledScriptArtifact(
        val compiledScript: CompiledScript,
        val cacheJar: Path?,
        val baseClassLoader: ClassLoader
    )

    private data class SourceCompilationPlan(
        val sourcePacks: List<ScriptPack>,
        val binaryPacks: List<ScriptPack>,
        val scriptPaths: List<String>,
        val classpathJars: List<Path>,
        val cacheKey: String,
        val classPacks: Map<String, ScriptPack>,
        val baseClassLoader: ClassLoader
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

    private val compiler = JvmScriptCompiler()

    private val externalClasspathJars = mutableListOf<File>()

    @JvmStatic
    fun addHostClasspathJar(file: File) {
        if (file.exists() && file.isFile) {
            externalClasspathJars.add(file.absoluteFile)
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
        val enabledPacks = packs.filter { it.enabled }.toList()
        if (enabledPacks.isEmpty()) return true
        val dependencySelection = ScriptDependencyManager.resolve(
            enabledPacks,
            invocation.environment,
            invocation.phaseName
        )
        if (dependencySelection.errors.isNotEmpty()) {
            dependencySelection.errors.forEach(LOGGER::error)
        }
        if (dependencySelection.validPacks.size != enabledPacks.size) return false

        val globalJarPacks = enabledPacks.filter { it.scope == ScriptPackScope.GLOBAL && it.kind == ScriptPackKind.JAR }
        val plan = buildSourceCompilationPlan(enabledPacks, globalJarPacks, dependencySelection)
        if (plan != null && loadCompiledSourceArtifact(plan, invocation.environment, null) == null) return false
        val baseLoader = plan?.baseClassLoader ?: createBaseClassLoader(dependencySelection)
        return enabledPacks.asSequence()
            .filter { it.kind == ScriptPackKind.JAR }
            .all { pack ->
                val jar = pack.compiledJar
                jar != null && Files.isRegularFile(jar) &&
                    runCatching { loadJarPack(pack, baseLoader, dependencySelection.fingerprints) }.isSuccess
            }
    }

    fun compileAndExecuteAll(
        packs: Collection<ScriptPack>,
        invocation: ScriptInvocation,
        progressReporter: ((String) -> Unit)? = null
    ): Boolean {
        val enabledPacks = packs.filter { it.enabled }.toList()
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
        if (dependencySelection.validPacks.isEmpty()) return dependencySelection.errors.isEmpty()

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
            environment.name.lowercase(),
            packs.first().scope,
            sourcePackCount,
            jarPackCount
        )

        reportProgress(progressReporter, "katton.reload.common.prepare_scripts")
        registerConfigs(packs)

        val sourcePlan = buildSourceCompilationPlan(
            packs,
            extraClasspathJars,
            dependencySelection,
            progressReporter
        )
        if (sourcePlan != null) {
            LOGGER.info(
                "Compiling {} source packs together with {} jar dependencies for {}",
                sourcePlan.sourcePacks.size,
                sourcePlan.binaryPacks.size,
                environment.name.lowercase()
            )
            val artifact = loadCompiledSourceArtifact(sourcePlan, environment, progressReporter)
            if (artifact != null) {
                reportProgress(progressReporter, "katton.reload.common.execute_source_scripts")
                runBlocking {
                    val executionResult = executeCombined(
                        artifact = artifact,
                        invocation = invocation,
                        scope = packs.first().scope,
                        classPacks = sourcePlan.classPacks,
                        label = "source packs (${sourcePlan.sourcePacks.size})"
                    )
                    ok = logExecutionResult("source packs", environment, executionResult) && ok
                }
            } else {
                ok = false
            }
        }

        packs
            .asSequence()
            .filter { it.kind == ScriptPackKind.JAR }
            .forEach { pack ->
                reportProgress(progressReporter, "katton.reload.common.load_jar_scripts")
                val artifact = loadJarPack(
                    pack,
                    sourcePlan?.baseClassLoader ?: createBaseClassLoader(dependencySelection),
                    dependencySelection.fingerprints
                )
                    ?: return@forEach
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

            // Register FQCN → packId mappings for script files
            for (script in pack.scripts) {
                val content = runCatching { String(script.bytes, StandardCharsets.UTF_8) }.getOrNull() ?: continue
                val fqcn = KattonConfigManager.deriveFqcn(content, script.relativePath.substringAfterLast('/'))
                KattonConfigManager.registerFqcnMapping(fqcn, pack.manifest.id)
            }

            // For jar packs: pre-scan compiled jar for FQCN mappings
            if (pack.kind == ScriptPackKind.JAR) {
                pack.compiledJar?.let { registerJarFqcnMappings(it, pack.manifest.id) }
            }
        }
    }

    private fun registerJarFqcnMappings(jarPath: Path, packId: String) {
        if (!Files.isRegularFile(jarPath)) return
        runCatching {
            JarFile(jarPath.toFile()).use { jar ->
                jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.contains('$') }
                    .forEach { entry ->
                        val fqcn = entry.name.removeSuffix(".class").replace('/', '.')
                        KattonConfigManager.registerFqcnMapping(fqcn, packId)
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
            .sortedBy { it.syncId }
        if (sourcePacks.isEmpty()) {
            return null
        }

        val binaryPacks = (packs.filter { it.kind == ScriptPackKind.JAR && it.compiledJar != null } + extraClasspathJars)
            .distinctBy { it.syncId }
            .sortedBy { it.syncId }
        val classpathJars = binaryPacks.mapNotNull { it.compiledJar?.toAbsolutePath()?.normalize() }.toMutableList()
        classpathJars += dependencySelection.resolved.flatMap { it.classpath }

        val scriptPaths = sourcePacks
            .flatMap { pack -> pack.scripts.sortedBy { it.relativePath }.map { it.absolutePath.toAbsolutePath().normalize().toString() } }

        val cacheKey = buildSourceCacheKey(sourcePacks, binaryPacks, dependencySelection.fingerprints)
        val classPacks = buildScriptClassPackMap(sourcePacks)
        val baseClassLoader = createBaseClassLoader(dependencySelection)

        // Compile .java files from enabled directory packs (independent of script collection)
        val classpathFromJava = compileJavaFromPacks(packs, classpathJars, dependencySelection.fingerprints, progressReporter)
        if (classpathFromJava != null) classpathJars.add(classpathFromJava)

        return SourceCompilationPlan(
            sourcePacks = sourcePacks,
            binaryPacks = binaryPacks,
            scriptPaths = scriptPaths,
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
     * Scans enabled directory packs for `.java` files, compiles them with
     * javac, and returns the path to the resulting jar (or null if none).
     */
    private fun compileJavaFromPacks(
        packs: Collection<ScriptPack>,
        classpath: List<Path>,
        dependencyFingerprints: List<String>,
        progressReporter: ((String) -> Unit)?
    ): Path? {
        val javaFiles = mutableListOf<ScriptPackScriptFile>()
        for (pack in packs) {
            if (pack.kind != ScriptPackKind.DIRECTORY) continue
            //collect all java files in a pack
            runCatching {
                Files.walk(pack.location).use { stream ->
                    stream.filter { f: Path -> Files.isRegularFile(f) && f.fileName.toString().endsWith(".java") }
                        .forEach { file: Path ->
                            val relative = pack.location.relativize(file).toString().replace('\\', '/')
                            javaFiles.add(
                                ScriptPackScriptFile(
                                    relativePath = relative,
                                    absolutePath = file,
                                    bytes = Files.readAllBytes(file)
                                )
                            )
                        }
                }
            }
        }
        if (javaFiles.isEmpty()) {
            LOGGER.info("No .java files found in packs")
            return null
        }
        LOGGER.info("Compiling {} .java files from {} packs", javaFiles.size, packs.size)
        reportProgress(progressReporter, "katton.reload.common.compile_java_sources")
        val result = JavaCompilationUtil.compileToJar(javaFiles, cacheDirectory, classpath, dependencyFingerprints)
        result?.let(::cleanStaleJavaCaches)
        LOGGER.info("Java compilation result: {}", result)
        return result
    }

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
        val compilationConfig = createCompilationConfiguration(
            orderedScriptPaths = plan.scriptPaths,
            classpathJars = plan.classpathJars,
            cacheJar = cacheJar
        )
        reportProgress(progressReporter, "katton.reload.common.compile_source_scripts")
        val compileResult = runBlocking {
            compiler(dummyScript, compilationConfig)
        }
        logCompileResult(plan.sourcePacks, compileResult)
        if (compileResult is ResultWithDiagnostics.Failure) {
            ScriptIssueReporter.report(
                title = "Katton script compilation failed",
                detail = buildString {
                    appendLine("Environment: ${environment.name.lowercase()}")
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
        val cacheKey = listOf(pack.syncId, pack.codeHash, dependencyFingerprints.joinToString("|")).joinToString(":")
        if (jarLoadCache.containsKey(cacheKey)) {
            LOGGER.info("Reusing jar load cache for {}", pack.manifest.name)
            return jarLoadCache[cacheKey]?.getOrNull()
        }

        val jarPath = pack.compiledJar
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            LOGGER.warn("Skipping jar pack {} because compiled jar is missing", pack.manifest.name)
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
                                appendLine("Environment: ${environment.name.lowercase()}")
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
                        appendLine("Environment: ${environment.name.lowercase()}")
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
        label: String
    ): ResultWithDiagnostics<EvaluationResult> {
        val script = artifact.compiledScript

        val pluginLoader = artifact.baseClassLoader
        val savedCcl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = pluginLoader
        try {
            return executeCombinedWithClassLoader(script, artifact, invocation, scope, classPacks, defaultPack, label)
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
            .toSortedMap()
        LOGGER.info(
            "Discovered {} top-level compiled classes for {} in {} environment",
            entrypointsByClass.size,
            label,
            environment.name.lowercase()
        )
        var successCount = 0
        var failureCount = 0
        val errorMessages = mutableListOf<String>()

        for ((fqcn, entrypoints) in entrypointsByClass) {
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
        return if (valid) null else "phase ${entrypoint.phaseName} is not valid for ${scope.serializedName} ${environment.name.lowercase()} packs"
    }

    private fun createCompilationConfiguration(
        orderedScriptPaths: List<String>,
        classpathJars: List<Path>,
        cacheJar: Path?
    ): ScriptCompilationConfiguration {
        // Source packs are compiled as one unit so Kotlin symbols can be referenced across pack boundaries.
        val currentHostClasspath = resolveHostClasspath()
        return ScriptCompilationConfiguration {
            importScripts(orderedScriptPaths.map { File(it).toScriptSource() })
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

    private fun createBaseClassLoader(selection: ScriptDependencySelection): ClassLoader {
        val parent = Thread.currentThread().contextClassLoader ?: ScriptEngine::class.java.classLoader
        val delegates = selection.resolved.mapNotNull { it.classLoader }.filter { it !== parent }.distinct()
        return if (delegates.isEmpty()) parent else DependencyDelegatingClassLoader(parent, delegates)
    }

    private fun resolveHostClasspath(): List<File> {
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

        return files.toList().also {
            LOGGER.info("Resolved {} host classpath entries for script compilation", it.size)
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
        digest.update("katton-source-pack-cache-v2".toByteArray(StandardCharsets.UTF_8))
        sourcePacks.forEach { pack ->
            digest.update(pack.syncId.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(pack.codeHash.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        binaryPacks.forEach { pack ->
            digest.update(pack.syncId.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(pack.codeHash.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        dependencyFingerprints.sorted().forEach { fingerprint ->
            digest.update(fingerprint.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
                    jar.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.contains("$") }
                        .map { entry ->
                            jar.getInputStream(entry).use { input ->
                                ClassFileEntry(
                                    className = entry.name.removeSuffix(".class").replace('/', '.'),
                                    bytes = input.readAllBytes()
                                )
                            }
                        }
                        .toList()
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
        var failure: Throwable? = null
        minecraft.execute {
            try {
                contextualAction()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }

        try {
            latch.await()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Interrupted while waiting for client main-thread script execution", interrupted)
        }

        failure?.let { throw it }
    }
}
