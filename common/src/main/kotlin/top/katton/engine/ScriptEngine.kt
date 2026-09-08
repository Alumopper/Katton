package top.katton.engine

import org.objectweb.asm.*
import top.katton.api.LOGGER
import top.katton.api.ClientPhase
import top.katton.api.InvocationReason
import top.katton.api.ReloadCause
import top.katton.api.ScriptInvocationContext
import top.katton.api.ServerPhase
import top.katton.config.KattonConfigManager
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackDependencyGraph
import top.katton.pack.ScriptPackScope
import top.katton.registry.KattonRegistry
import top.katton.util.ScriptExecutionContext
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URL
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.optionals.getOrNull
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

/** Per-pack compiler facade and lifecycle entrypoint dispatcher. */
object ScriptEngine {
    private const val CLIENT_THREAD_WAIT_TIMEOUT_SECONDS = 60L
    private val externalClasspathJars = linkedSetOf<File>()
    private val hostClasspathLock = Any()
    @Volatile private var hostClasspathCache: List<File>? = null
    @Volatile private var cacheDirectory: Path? = null

    @JvmStatic
    fun addHostClasspathJar(file: File) {
        if (file.isFile) synchronized(hostClasspathLock) {
            if (externalClasspathJars.add(file.absoluteFile)) hostClasspathCache = null
        }
    }
    @JvmStatic
    fun setCacheDirectory(path: Path?) { cacheDirectory = path?.toAbsolutePath()?.normalize() }

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
        return runCatching { preparePacks(packs, invocation); true }.getOrElse {
            LOGGER.error("Script pack preparation failed", it)
            ScriptIssueReporter.report("Katton script compilation failed", it.message ?: it.toString())
            false
        }
    }

    internal fun preparePacks(packs: Collection<ScriptPack>, invocation: ScriptInvocation): List<PackPreparation> =
        PackRuntime.prepare(withGlobalDependencies(packs), invocation, cacheDirectory ?: Path.of(System.getProperty("java.io.tmpdir"), "katton-pack-cache"),
            resolveHostClasspath().map { it.toPath() }).also { prepared ->
                prepared.forEach { instance ->
                    instance.artifact.files.filterKeys { it.endsWith(".class") }.forEach { (path, bytes) ->
                        ScriptEnvironment.entries.forEach { environment ->
                            scanEntrypoints(bytes, path, Type.getDescriptor(environment.annotationClass),
                                if (environment == ScriptEnvironment.CLIENT) ClientPhase.READY.name else ServerPhase.BOOTSTRAP.name)
                                .forEach { entry ->
                                    val parameters = Type.getArgumentTypes(entry.methodDescriptor)
                                    require(parameters.size <= 1 && Type.getReturnType(entry.methodDescriptor) == Type.VOID_TYPE) {
                                        "Invalid entrypoint ${instance.pack.syncId}:$path.${entry.methodName}; expected Unit and zero or one lifecycle context parameter"
                                    }
                                }
                        }
                    }
                }
            }

    private fun withGlobalDependencies(packs: Collection<ScriptPack>): List<ScriptPack> {
        val selected = packs.associateByTo(linkedMapOf()) { it.syncId }
        val globals = top.katton.pack.ScriptPackManager.collectExecutableGlobalPacks()
        fun includeDependencies(pack: ScriptPack) {
            if (pack.scope == ScriptPackScope.SERVER_CACHE) return
            pack.manifest.packDependencies.forEach { declaration ->
                val matches = globals.filter { it.syncId.equals(declaration.id, true) || it.manifest.id.equals(declaration.id, true) }
                val dependency = matches.singleOrNull() ?: return@forEach
                if (selected.putIfAbsent(dependency.syncId, dependency) == null) includeDependencies(dependency)
            }
        }
        packs.forEach(::includeDependencies)
        return selected.values.toList()
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
        val enabled = candidates.filter { it.enabled }
        if (prepareAll(enabled, invocation)) return PreparedPackSelection(enabled, emptySet())
        if (enabled.any { it.scope == ScriptPackScope.SERVER_CACHE }) return null
        // Validate each candidate with its dependency closure. Reject its consumers using both graphs.
        val graph = ScriptPackDependencyGraph.resolve(withGlobalDependencies(enabled))
        val rejected = graph.invalidPacks.mapTo(linkedSetOf()) { it.syncId }
        graph.orderedPacks.forEach { pack ->
            val closure = linkedSetOf<ScriptPack>()
            fun include(target: ScriptPack) {
                if (closure.add(target)) graph.edges[target].orEmpty().forEach { include(it.target) }
            }
            include(pack)
            if (!prepareAll(closure, invocation)) rejected += pack.syncId
        }
        val affected = ScriptPackDependencyGraph.affected(previous, enabled, rejected)
        val effective = enabled.filterNot { it.syncId in affected } + previous.filter { it.enabled && it.syncId in affected }
        if (!prepareAll(effective, invocation)) return null
        return PreparedPackSelection(effective, affected)
    }

    fun compileAndExecuteAll(
        packs: Collection<ScriptPack>,
        invocation: ScriptInvocation,
        progressReporter: ((String) -> Unit)? = null
    ): Boolean {
        return runCatching {
            val prepared = preparePacks(packs, invocation)
            executePreparedPacks(packs, prepared, invocation)
        }.getOrElse {
            LOGGER.error("Script pack activation failed", it)
            ScriptIssueReporter.report("Katton script activation failed", it.message ?: it.toString())
            false
        }
    }

    internal fun executePreparedPacks(packs: Collection<ScriptPack>, prepared: List<PackPreparation>, invocation: ScriptInvocation): Boolean {
        return runCatching {
            registerConfigs(packs.toList())
            var success = false
            onRuntimeThread(invocation.environment, PackRuntime.carryTransaction {
            success = PackRuntime.execute(prepared, invocation, packs.mapTo(hashSetOf()) { it.syncId }, { selection ->
                PackHostClassLoader(selectScriptHostClassLoader(),
                    resolveHostClasspath().map { it.toPath() }, selection.resolved)
            }, ::executePackInstance)
            })
            success
        }.getOrElse {
            LOGGER.error("Script pack activation failed", it)
            ScriptIssueReporter.report("Katton script activation failed", it.message ?: it.toString())
            false
        }
    }

    internal fun onRuntimeThread(environment: ScriptEnvironment, action: () -> Unit) {
        if (environment == ScriptEnvironment.CLIENT) runOnClientMainThreadAndWait(action) else action()
    }

    @JvmStatic
    fun clearWorldInstances() {
        ScriptEnvironment.entries.forEach { PackRuntime.clear(it, setOf(ScriptPackScope.WORLD, ScriptPackScope.SERVER_CACHE)) }
        ScriptReloadManager.clearClientRuntimeSnapshot()
    }

    private val warnedNoEntrypoints = ConcurrentHashMap.newKeySet<String>()

    private fun executePackInstance(instance: PackInstance, invocation: ScriptInvocation): Boolean {
        val pack = instance.preparation.pack
        val entries = instance.preparation.artifact.files.filterKeys { it.endsWith(".class") }.flatMap { (path, bytes) ->
            ScriptEnvironment.entries.flatMap { environment ->
                scanEntrypoints(bytes, path.removeSuffix(".class").replace('/', '.'), Type.getDescriptor(environment.annotationClass),
                    if (environment == ScriptEnvironment.CLIENT) ClientPhase.READY.name else ServerPhase.BOOTSTRAP.name)
                    .map { environment to it }
            }
        }
        if (entries.isEmpty() && warnedNoEntrypoints.add("${pack.syncId}:${pack.hash}")) {
            LOGGER.warn("Script pack {} version {} has no valid entrypoint; its library and resources remain available", pack.syncId, pack.manifest.version)
        }
        for ((environment, entry) in entries) {
            if (environment != invocation.environment) continue
            val invalid = validateEntrypoint(entry, pack.scope, environment)
            require(invalid == null) { "${pack.syncId}:${entry.className}.${entry.methodName}: $invalid" }
            if (entry.phaseName != invocation.phaseName || !ScriptLifecyclePolicy.shouldInvoke(pack.scope, invocation.reason, entry.replay)) continue
            val clazz = Class.forName(entry.className, false, instance.loader)
            val methodType = MethodType.fromMethodDescriptorString(entry.methodDescriptor, instance.loader)
            val owner = top.katton.util.ScriptResourceOwner(environment, pack.scope, pack.syncId, instance.generation,
                invocation.phaseName, entry.className + ":" + entry.methodName)
            val context = invocation.contextFor(pack)
            ScriptExecutionContext.withEnvironment(environment) {
                ScriptExecutionContext.withScope(pack.scope) {
                    ScriptExecutionContext.withIdentity(owner) {
                        ScriptExecutionContext.recordCurrentRevision(pack.codeHash)
                        val oldLoader = Thread.currentThread().contextClassLoader
                        try {
                            Thread.currentThread().contextClassLoader = instance.loader
                            invokeEntrypoint(clazz, entry, methodType, environment, context)
                        } finally { Thread.currentThread().contextClassLoader = oldLoader }
                    }
                }
            }
        }
        return true
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

        }
    }

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

            val contextLoader = Thread.currentThread().contextClassLoader
            val scriptEngineLoader = ScriptEngine::class.java.classLoader

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
                "net.minecraft.server.MinecraftServer",
                "org.jetbrains.annotations.NotNull",
                "com.google.gson.Gson",
                "com.mojang.brigadier.Command",
                "com.mojang.serialization.Codec",
                "org.slf4j.Logger",
                "kotlinx.coroutines.CoroutineScope",
                "net.fabricmc.fabric.api.event.Event",
                "net.neoforged.bus.api.Event",
                "net.neoforged.neoforge.common.NeoForge",
                "net.kyori.adventure.text.Component",
                "com.google.common.collect.ImmutableList",
                "it.unimi.dsi.fastutil.objects.ObjectArrayList",
                "org.joml.Vector3f",
                "io.netty.buffer.ByteBuf",
                "io.netty.channel.Channel",
                "com.mojang.authlib.GameProfile",
                "org.jspecify.annotations.Nullable"
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

    private fun validateEntrypoint(
        entrypoint: EntrypointDescriptor,
        scope: ScriptPackScope,
        environment: ScriptEnvironment
    ): String? {
        val valid = ScriptLifecyclePolicy.isValid(scope, environment, entrypoint.phaseName)
        return if (valid) null else "phase ${entrypoint.phaseName} is not valid for ${scope.serializedName} ${environment.name.lowercase(Locale.ROOT)} packs"
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
            ManagedResources.activation(ScriptExecutionContext.currentScriptOwner()) {
                handle.invokeWithArguments(arguments)
                KattonRegistry.flushPendingRegistrations()
            }
        }
    }

    private fun runOnClientMainThreadAndWait(action: () -> Unit) {
        val owner = ScriptExecutionContext.currentScriptOwner()
        val scope = ScriptExecutionContext.currentScriptScope()
        val environment = ScriptExecutionContext.currentScriptEnvironment()
        val contextualAction = { ManagedResources.activation(owner) {
            ScriptExecutionContext.withEnvironment(environment) {
                ScriptExecutionContext.withScope(scope) {
                    ScriptExecutionContext.withOwner(owner, action)
                }
            }
        } }

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
