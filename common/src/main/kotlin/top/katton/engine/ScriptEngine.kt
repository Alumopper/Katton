package top.katton.engine

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl
import top.katton.api.ClientScriptEntrypoint
import top.katton.api.LOGGER
import top.katton.api.ServerScriptEntrypoint
import top.katton.client.ReloadProgressState
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackScope
import top.katton.pack.ScriptPackScriptFile
import top.katton.util.ScriptExecutionContext
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.jar.JarFile
import kotlin.jvm.optionals.getOrNull
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.enableScriptsInstancesSharing
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.loadScriptFromJar

/**
 * ScriptEngine compiles source packs together and executes jar packs separately.
 */
object ScriptEngine {

    private data class CompiledScriptArtifact(
        val compiledScript: CompiledScript,
        val cacheJar: Path?
    )

    private data class SourceCompilationPlan(
        val sourcePacks: List<ScriptPack>,
        val binaryPacks: List<ScriptPack>,
        val scriptPaths: List<String>,
        val classpathJars: List<Path>,
        val cacheKey: String
    )

    private val compiler = JvmScriptCompiler()

    @Volatile
    private var cacheDirectory: Path? = null

    private val sourceCompileCache = ConcurrentHashMap<String, CompiledScriptArtifact>()
    private val jarLoadCache = ConcurrentHashMap<String, Optional<CompiledScriptArtifact>>()

    private val baseConfig = ScriptCompilationConfiguration {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
        defaultImports(
            ClientScriptEntrypoint::class.qualifiedName!!,
            ServerScriptEntrypoint::class.qualifiedName!!
        )
    }

    private val evaluationConfig = ScriptEvaluationConfiguration {
        enableScriptsInstancesSharing()
    }

    @JvmStatic
    fun setCacheDirectory(path: Path?) {
        cacheDirectory = path?.toAbsolutePath()?.normalize()
    }

    /**
     * Clears all stale compilation cache jars. Keeps the cache directory
     * tidy — only the most recent compilation artifacts survive.
     */
    private fun cleanStaleCaches() {
        val dir = cacheDirectory ?: return
        runCatching {
            Files.newDirectoryStream(dir).use { stream ->
                stream.filter { it.fileName.toString().let { n -> n.startsWith("source-") || n.startsWith("java-") } }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @JvmStatic
    fun compileAndExecuteAll(packs: Collection<ScriptPack>, environment: ScriptEnvironment) {
        cleanStaleCaches()
        val enabledPacks = packs.filter { it.enabled }
        if (enabledPacks.isEmpty()) {
            ReloadProgressState.update("No enabled ${environment.name.lowercase()} script packs", 0.95f)
            return
        }

        val scopeGroups = enabledPacks.groupBy { it.scope }
        val globalJarPacks = enabledPacks.filter { it.scope == ScriptPackScope.GLOBAL && it.kind == ScriptPackKind.JAR }

        val scopeEntries = scopeGroups.entries.toList()
        val scopeCount = scopeEntries.size.coerceAtLeast(1)
        val baseProgress = if (environment == ScriptEnvironment.SERVER) 0.50f else 0.46f
        val maxProgress = if (environment == ScriptEnvironment.SERVER) 0.80f else 0.88f
        ReloadProgressState.update(
            "Preparing ${environment.name.lowercase()} script scopes",
            baseProgress
        )

        scopeEntries.forEachIndexed { index, (scope, scopePacks) ->
            val stepProgress = baseProgress + (maxProgress - baseProgress) * (index.toFloat() / scopeCount.toFloat())
            ReloadProgressState.update(
                "Compiling ${environment.name.lowercase()} scope ${scope.serializedName} (${index + 1}/$scopeCount)",
                stepProgress.coerceIn(baseProgress, maxProgress)
            )
            ScriptExecutionContext.withScope(scope) {
                compileAndExecuteScope(scopePacks, environment, scope, globalJarPacks)
            }
        }
        ReloadProgressState.update(
            "Finished ${environment.name.lowercase()} script execution",
            maxProgress
        )
    }

    private fun compileAndExecuteScope(
        packs: List<ScriptPack>,
        environment: ScriptEnvironment,
        scope: ScriptPackScope,
        extraClasspathJars: List<ScriptPack>
    ) {
        val sourcePackCount = packs.count { it.scripts.isNotEmpty() }
        val jarPackCount = packs.count { it.kind == ScriptPackKind.JAR }
        LOGGER.info(
            "Preparing {} {} script packs in scope {} (source={}, jar={})",
            packs.size,
            environment.name.lowercase(),
            scope.serializedName,
            sourcePackCount,
            jarPackCount
        )

        val sourcePlan = buildSourceCompilationPlan(packs, extraClasspathJars)
        if (sourcePlan != null) {
            LOGGER.info(
                "Compiling {} source packs together with {} jar dependencies for {}",
                sourcePlan.sourcePacks.size,
                sourcePlan.binaryPacks.size,
                environment.name.lowercase()
            )
            val artifact = loadCompiledSourceArtifact(sourcePlan)
            if (artifact != null) {
                runBlocking {
                    val executionResult = executeCombined(
                        artifact = artifact,
                        environment = environment,
                        scope = scope,
                        label = "source packs (${sourcePlan.sourcePacks.size})"
                    )
                    logExecutionResult("source packs", executionResult)
                }
            }
        }

        packs
            .asSequence()
            .filter { it.kind == ScriptPackKind.JAR }
            .forEach { pack ->
                val artifact = loadJarPack(pack) ?: return@forEach
                runBlocking {
                    val executionResult = executeCombined(
                        artifact = artifact,
                        environment = environment,
                        scope = scope,
                        label = "jar pack ${pack.manifest.name}"
                    )
                    logExecutionResult(pack.manifest.name, executionResult)
                }
            }
    }

    private fun buildSourceCompilationPlan(packs: Collection<ScriptPack>, extraClasspathJars: List<ScriptPack> = emptyList()): SourceCompilationPlan? {
        val sourcePacks = packs
            .filter { it.scripts.isNotEmpty() }
            .sortedBy { it.syncId }
        if (sourcePacks.isEmpty()) {
            return null
        }

        val binaryPacks = (packs.filter { it.kind == ScriptPackKind.JAR && it.compiledJar != null } + extraClasspathJars)
            .distinctBy { it.syncId }
            .sortedBy { it.syncId }
        val scriptPaths = sourcePacks
            .flatMap { pack -> pack.scripts.sortedBy { it.relativePath }.map { it.absolutePath.toAbsolutePath().normalize().toString() } }
        val classpathJars = binaryPacks.mapNotNull { it.compiledJar?.toAbsolutePath()?.normalize() }.toMutableList()
        val cacheKey = buildSourceCacheKey(sourcePacks, binaryPacks)

        // Compile .java files from enabled directory packs (independent of script collection)
        val classpathFromJava = compileJavaFromPacks(packs)
        if (classpathFromJava != null) classpathJars.add(classpathFromJava)

        return SourceCompilationPlan(
            sourcePacks = sourcePacks,
            binaryPacks = binaryPacks,
            scriptPaths = scriptPaths,
            classpathJars = classpathJars,
            cacheKey = cacheKey
        )
    }

    /**
     * Scans enabled directory packs for `.java` files, compiles them with
     * javac, and returns the path to the resulting jar (or null if none).
     */
    private fun compileJavaFromPacks(packs: Collection<ScriptPack>): Path? {
        val javaFiles = mutableListOf<ScriptPackScriptFile>()
        for (pack in packs) {
            if (pack.kind != ScriptPackKind.DIRECTORY || pack.location == null) continue
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
        val result = JavaCompilationUtil.compileToJar(javaFiles, cacheDirectory)
        LOGGER.info("Java compilation result: {}", result)
        return result
    }

    private fun loadCompiledSourceArtifact(plan: SourceCompilationPlan): CompiledScriptArtifact? {
        sourceCompileCache[plan.cacheKey]?.let {
            LOGGER.info("Reusing in-memory combined source compilation cache {}", plan.cacheKey)
            return it
        }

        val cacheJar = resolveSourceCacheJar(plan.cacheKey)
        if (cacheJar != null && Files.isRegularFile(cacheJar)) {
            LOGGER.info("Combined source cache jar available at {}", cacheJar)
        }
        val dummyScript = "".toScriptSource()
        val compilationConfig = createCompilationConfiguration(
            orderedScriptPaths = plan.scriptPaths,
            classpathJars = plan.classpathJars,
            cacheJar = cacheJar
        )
        val compileResult = runBlocking {
            compiler(dummyScript, compilationConfig)
        }
        logCompileResult(plan.sourcePacks, compileResult)

        val artifact = when (compileResult) {
            is ResultWithDiagnostics.Success -> CompiledScriptArtifact(compileResult.value, cacheJar)
            is ResultWithDiagnostics.Failure -> null
        }
        if (artifact != null) {
            LOGGER.info(
                "Stored combined source compilation result for {} packs with cache key {}",
                plan.sourcePacks.size,
                plan.cacheKey
            )
            sourceCompileCache[plan.cacheKey] = artifact
        }
        return artifact
    }

    private fun loadJarPack(pack: ScriptPack): CompiledScriptArtifact? {
        val cacheKey = "${pack.syncId}:${pack.hash}"
        if (jarLoadCache.containsKey(cacheKey)) {
            LOGGER.info("Reusing jar load cache for {}", pack.manifest.name)
            return jarLoadCache[cacheKey]?.getOrNull()
        }

        val jarPath = pack.compiledJar
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            LOGGER.warn("Skipping jar pack {} because compiled jar is missing", pack.manifest.name)
            jarLoadCache[cacheKey] = Optional.empty()
            return null
        }

        LOGGER.info("Loading jar pack {} from {}", pack.manifest.name, jarPath)
        val compiledScript = runCatching { jarPath.toFile().loadScriptFromJar() }
            .getOrElse {
                LOGGER.warn("Failed to load compiled script jar {}", jarPath, it)
                null
            }

        val artifact = compiledScript?.let { CompiledScriptArtifact(it, jarPath) }
        if (artifact == null) {
            LOGGER.info("Jar pack {} has no loadable script metadata, using it as classpath only", pack.manifest.name)
        } else {
            LOGGER.info("Loaded executable compiled script jar for pack {}", pack.manifest.name)
        }
        jarLoadCache[cacheKey] = Optional.ofNullable(artifact)
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

    private fun logExecutionResult(label: String, executionResult: ResultWithDiagnostics<EvaluationResult>) {
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
                    }
                } else {
                    LOGGER.info("[{}] Execution finished without summary", label)
                }
            }

            is ResultWithDiagnostics.Failure -> {
                LOGGER.error("[{}] Execution failed: {}", label, executionResult.reports.joinToString("\n"))
            }
        }
    }

    private suspend fun executeCombined(
        artifact: CompiledScriptArtifact,
        environment: ScriptEnvironment,
        scope: ScriptPackScope,
        label: String
    ): ResultWithDiagnostics<EvaluationResult> {
        val script = artifact.compiledScript
        val rootClass = when (val res = script.getClass(evaluationConfig)) {
            is ResultWithDiagnostics.Success -> res.value
            is ResultWithDiagnostics.Failure -> return res
        }

        val loader = rootClass.java.classLoader
        val rootName = rootClass.qualifiedName
        val topLevelClasses = collectTopLevelClassNames(script, artifact.cacheJar)
        LOGGER.info(
            "Discovered {} top-level compiled classes for {} in {} environment",
            topLevelClasses.size,
            label,
            environment.name.lowercase()
        )
        var successCount = 0
        var failureCount = 0
        val errorMessages = mutableListOf<String>()

        for (fqcn in topLevelClasses) {
            if (fqcn == rootName) continue

            runCatching {
                val clazz = Class.forName(fqcn, false, loader)
                val entrypoints = clazz.declaredMethods.filter { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.annotationClassNames().contains(environment.annotationClassName)
                }
                if (entrypoints.isNotEmpty()) {
                    LOGGER.info(
                        "Executing {} entrypoints from {} for {}",
                        entrypoints.size,
                        fqcn,
                        label
                    )
                }

                for (method in entrypoints) {
                    if (method.parameterCount != 0) {
                        failureCount++
                        errorMessages += "$fqcn.${method.name}: ${environment.annotationClassName.substringAfterLast('.')} functions must not declare parameters"
                        continue
                    }

                    ScriptExecutionContext.withOwner("${scope.serializedName}:$fqcn") {
                        invokeEntrypoint(method, environment)
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

    private fun createCompilationConfiguration(
        orderedScriptPaths: List<String>,
        classpathJars: List<Path>,
        cacheJar: Path?
    ): ScriptCompilationConfiguration {
        // Source packs are compiled as one unit so Kotlin symbols can be referenced across pack boundaries.
        val jarCache = cacheJar?.let { resolvedJar ->
            resolvedJar.parent?.let(Files::createDirectories)
            CompiledScriptJarsCache { _, _ -> resolvedJar.toFile() }
        }
        val baseHostConfiguration = baseConfig[ScriptCompilationConfiguration.hostConfiguration]

        return ScriptCompilationConfiguration(baseConfig) {
            importScripts(orderedScriptPaths.map { File(it).toScriptSource() })
            jvm {
                dependenciesFromCurrentContext(wholeClasspath = true)
                if (classpathJars.isNotEmpty()) {
                    updateClasspath(classpathJars.map(Path::toFile))
                }
            }
            if (jarCache != null) {
                hostConfiguration(
                    ScriptingHostConfiguration(baseHostConfiguration ?: ScriptingHostConfiguration()) {
                        jvm {
                            compilationCache(jarCache)
                        }
                    }
                )
            }
        }
    }

    private fun resolveSourceCacheJar(cacheKey: String): Path? {
        val root = cacheDirectory ?: return null
        return runCatching {
            Files.createDirectories(root)
            root.resolve("source-$cacheKey.jar")
        }.getOrElse {
            LOGGER.warn("Failed to initialize script compile cache directory {}", root, it)
            null
        }
    }

    private fun buildSourceCacheKey(sourcePacks: List<ScriptPack>, binaryPacks: List<ScriptPack>): String {
        // Both source pack content and binary jar hashes affect the combined compilation result.
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("katton-source-pack-cache-v1".toByteArray(StandardCharsets.UTF_8))
        sourcePacks.forEach { pack ->
            digest.update(pack.syncId.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(pack.hash.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        binaryPacks.forEach { pack ->
            digest.update(pack.syncId.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(pack.hash.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun collectTopLevelClassNames(script: CompiledScript, cacheJar: Path?): List<String> {
        val module = (script as? KJvmCompiledScript)?.getCompiledModule()
        if (module is KJvmCompiledModuleInMemoryImpl) {
            return module.compilerOutputFiles.keys
                .asSequence()
                .filter { it.endsWith(".class") && !it.contains("$") }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .toList()
        }

        if (cacheJar != null && Files.isRegularFile(cacheJar)) {
            return runCatching {
                JarFile(cacheJar.toFile()).use { jar ->
                    jar.entries().asSequence()
                        .map { it.name }
                        .filter { it.endsWith(".class") && !it.contains("$") }
                        .map { it.removeSuffix(".class").replace('/', '.') }
                        .toList()
                }
            }.getOrElse {
                LOGGER.warn("Failed to read compiled script jar {}", cacheJar, it)
                emptyList()
            }
        }

        return emptyList()
    }

    private fun Method.annotationClassNames(): Set<String> {
        return annotations.mapNotNull { it.annotationClass.qualifiedName }.toSet()
    }

    private fun invokeEntrypoint(method: Method, environment: ScriptEnvironment) {
        method.isAccessible = true
        if (environment != ScriptEnvironment.CLIENT) {
            method.invoke(null)
            return
        }
        runOnClientMainThreadAndWait {
            method.invoke(null)
        }
    }

    private fun runOnClientMainThreadAndWait(action: () -> Unit) {
        val minecraft = runCatching { net.minecraft.client.Minecraft.getInstance() }.getOrNull()
        if (minecraft == null || minecraft.isSameThread) {
            action()
            return
        }

        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        minecraft.execute {
            try {
                action()
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
