package top.katton.engine

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl
import org.objectweb.asm.*
import top.katton.api.LOGGER
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

    @Suppress("ArrayInDataClass")
    private data class ClassFileEntry(
        val className: String,
        val bytes: ByteArray
    )

    private data class EntrypointDescriptor(
        val className: String,
        val methodName: String,
        val methodDescriptor: String
    )

    private val compiler = JvmScriptCompiler()

    private val hostClasspath: List<File> by lazy(::resolveHostClasspath)

    @Volatile
    private var cacheDirectory: Path? = null

    private val sourceCompileCache = ConcurrentHashMap<String, CompiledScriptArtifact>()
    private val jarLoadCache = ConcurrentHashMap<String, Optional<CompiledScriptArtifact>>()

    private val baseConfig = ScriptCompilationConfiguration {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
            updateClasspath(hostClasspath)
        }
    }

    private val evaluationConfig = ScriptEvaluationConfiguration {
        enableScriptsInstancesSharing()
    }

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
    fun compileAndExecuteAll(packs: Collection<ScriptPack>, environment: ScriptEnvironment) {
        val enabledPacks = packs.filter { it.enabled }.toList()
        if (enabledPacks.isEmpty()) return

        val globalJarPacks = enabledPacks.filter { it.scope == ScriptPackScope.GLOBAL && it.kind == ScriptPackKind.JAR }
        compileAndExecute(enabledPacks, environment, globalJarPacks)
    }

    private fun compileAndExecute(
        packs: List<ScriptPack>,
        environment: ScriptEnvironment,
        extraClasspathJars: List<ScriptPack>
    ) {
        val sourcePackCount = packs.count { it.scripts.isNotEmpty() }
        val jarPackCount = packs.count { it.kind == ScriptPackKind.JAR }
        LOGGER.info(
            "Preparing {} {} script packs in scope {} (source={}, jar={})",
            packs.size,
            environment.name.lowercase(),
            packs.first().scope,
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
                        scope = packs.first().scope,
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
                        scope = packs.first().scope,
                        label = "jar pack ${pack.manifest.name}"
                    )
                    logExecutionResult(pack.manifest.name, executionResult)
                }
            }
    }

    private fun buildSourceCompilationPlan(
        packs: Collection<ScriptPack>,
        extraClasspathJars: List<ScriptPack> = emptyList()
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

        val scriptPaths = sourcePacks
            .flatMap { pack -> pack.scripts.sortedBy { it.relativePath }.map { it.absolutePath.toAbsolutePath().normalize().toString() } }

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
        val result = JavaCompilationUtil.compileToJar(javaFiles, cacheDirectory)
        result?.let(::cleanStaleJavaCaches)
        LOGGER.info("Java compilation result: {}", result)
        return result
    }

    private fun loadCompiledSourceArtifact(plan: SourceCompilationPlan): CompiledScriptArtifact? {
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
        val compileResult = runBlocking {
            compiler(dummyScript, compilationConfig)
        }
        logCompileResult(plan.sourcePacks, compileResult)

        val compiledScript = (compileResult as? ResultWithDiagnostics.Success)?.value
        val artifact = compiledScript?.let { CompiledScriptArtifact(it, cacheJar) }
        if (artifact != null) {
            LOGGER.info(
                "Stored combined source compilation result for {} packs with cache key {}",
                plan.sourcePacks.size,
                plan.cacheKey
            )
            sourceCompileCache[plan.cacheKey] = artifact
            cleanStaleScriptCaches(plan.cacheKey)

            // Persist compiled classes to disk for inspection only.
            // These jars cannot be reloaded directly due to Kotlin compiler format constraints.
            if (cacheJar != null) {
                writeCompiledScriptToJar(compiledScript, cacheJar)
            }
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
                    val methodType = MethodType.fromMethodDescriptorString(entrypoint.methodDescriptor, loader)
                    if (methodType.parameterCount() != 0) {
                        failureCount++
                        errorMessages += "$fqcn.${entrypoint.methodName}: ${environment.annotationClassName.substringAfterLast('.')} functions must not declare parameters"
                        continue
                    }

                    ScriptExecutionContext.withScope(scope) {
                        ScriptExecutionContext.withOwner("${scope.serializedName}:$fqcn") {
                            invokeEntrypoint(clazz, entrypoint, methodType, environment)
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

    private fun createCompilationConfiguration(
        orderedScriptPaths: List<String>,
        classpathJars: List<Path>,
        cacheJar: Path?
    ): ScriptCompilationConfiguration {
        // Source packs are compiled as one unit so Kotlin symbols can be referenced across pack boundaries.
        return ScriptCompilationConfiguration(baseConfig) {
            importScripts(orderedScriptPaths.map { File(it).toScriptSource() })
            jvm {
                jvmTarget("25")
                dependenciesFromCurrentContext(wholeClasspath = true)
                updateClasspath(hostClasspath)
                if (classpathJars.isNotEmpty()) {
                    updateClasspath(classpathJars.map(Path::toFile))
                }
            }
        }
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
                    runCatching { addUrl(URL(spec)) }
                }
            }
        }

        fun addClassSource(clazz: Class<*>) {
            addUrl(clazz.protectionDomain?.codeSource?.location)
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
        return collectTopLevelClassFiles(script, cacheJar)
            .sortedBy { it.className }
            .associate { classFile ->
                classFile.className to scanEntrypoints(classFile.bytes, classFile.className, annotationDescriptor)
            }
            .filterValues { it.isNotEmpty() }
    }

    private fun scanEntrypoints(
        classBytes: ByteArray,
        className: String,
        annotationDescriptor: String
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
                            entrypoints += EntrypointDescriptor(
                                className = className,
                                methodName = name,
                                methodDescriptor = descriptor
                            )
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
        environment: ScriptEnvironment
    ) {
        val lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup())
        val handle = lookup.findStatic(clazz, entrypoint.methodName, methodType)
        if (environment != ScriptEnvironment.CLIENT) {
            handle.invokeWithArguments()
            KattonRegistry.flushPendingRegistrations()
            return
        }
        runOnClientMainThreadAndWait {
            handle.invokeWithArguments()
            KattonRegistry.flushPendingRegistrations()
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
