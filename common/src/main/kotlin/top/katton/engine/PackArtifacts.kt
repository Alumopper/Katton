package top.katton.engine

import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.config.Services
import top.katton.dev.DevEvents
import top.katton.pack.*
import top.katton.api.LOGGER
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.concurrent.ConcurrentHashMap

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Cache values contain immutable compiler output and metadata, never runtime loaders or entrypoint handles. */
internal data class PackArtifact(val fingerprint: String, val jar: Path, val files: Map<String, ByteArray>) {
    val classNames = files.keys.filter { it.endsWith(".class") && it != "module-info.class" }
        .mapTo(hashSetOf()) { it.removeSuffix(".class").replace('/', '.') }
}

internal data class PrivateLibraries(val paths: List<Path>, val classes: Map<String, ByteArray>) {
    companion object {
        fun prepare(pack: ScriptPack, cache: Path): PrivateLibraries {
            Files.createDirectories(cache)
            val classes = linkedMapOf<String, ByteArray>()
            val resources = linkedMapOf<String, ByteArray>()
            var expanded = 0L
            var archiveExpanded = 0L
            val paths = pack.contentFiles.filter { ScriptPackSnapshots.isLibrary(it.relativePath) }
                .sortedBy { it.relativePath }.distinctBy { sha256(it.bytes) }.map { file ->
                    val hash = sha256(file.bytes)
                    // Bound all expanded bytes before JarFile reads the multi-release manifest.
                    java.util.zip.ZipInputStream(file.bytes.inputStream()).use { archive ->
                        val names = hashSetOf<String>()
                        var count = 0
                        while (true) {
                            val entry = archive.nextEntry ?: break
                            require(++count <= ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES) { "Too many private JAR entries" }
                            require(names.add(entry.name)) { "Duplicate private JAR entry: ${entry.name}" }
                            val limit = if (entry.name.equals("META-INF/MANIFEST.MF", true)) ScriptPackFileLimits.MAX_MANIFEST_BYTES else ScriptPackFileLimits.MAX_FILE_BYTES
                            val remaining = ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES - archiveExpanded
                            require(remaining >= 0) { "Private libraries exceed expanded-byte budget" }
                            archiveExpanded += SafePackFileIo.readBytes(archive, minOf(limit.toLong(), remaining).toInt(), entry.name).size
                        }
                    }
                    val path = cache.resolve("$hash.jar")
                    if (!Files.isRegularFile(path) || sha256(Files.readAllBytes(path)) != hash) {
                        val staging = Files.createTempFile(cache, ".library-", ".tmp")
                        try {
                            Files.write(staging, file.bytes)
                            Files.move(staging, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        } finally { Files.deleteIfExists(staging) }
                    }
                    JarFile(path.toFile(), true, JarFile.OPEN_READ, Runtime.Version.parse("25")).use { jar ->
                        require(jar.size() <= ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES) { "Too many private JAR entries" }
                        jar.versionedStream().use { entries -> entries.filter { !it.isDirectory }.forEach { entry ->
                            val bytes = jar.getInputStream(entry).use { SafePackFileIo.readBytes(it, ScriptPackFileLimits.MAX_FILE_BYTES, entry.name) }
                            expanded += bytes.size
                            require(expanded <= ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES) { "Private JAR expands beyond pack limit: ${file.relativePath}" }
                            if (entry.name.endsWith(".class") && entry.name != "module-info.class" && !entry.name.startsWith("META-INF/")) {
                                val name = entry.name.removeSuffix(".class").replace('/', '.')
                                val previous = classes.putIfAbsent(name, bytes)
                                require(previous == null || previous.contentEquals(bytes)) { "Pack ${pack.syncId}: conflicting private class $name in ${file.relativePath}" }
                            } else {
                                val previous = resources.putIfAbsent(entry.name, bytes)
                                if (previous != null && !previous.contentEquals(bytes)) LOGGER.warn("Pack {}: differing private resource {} in {}; library path order applies", pack.syncId, entry.name, file.relativePath)
                            }
                        } }
                    }
                    path
                }
            return PrivateLibraries(paths, classes)
        }
    }
}

internal object PackCompiler {
    private val cache = ConcurrentHashMap<String, PackArtifact>()
    @Synchronized
    fun compile(pack: ScriptPack, root: Path, classpath: List<Path>, dependencyFingerprints: List<String>): PackArtifact {
        Files.createDirectories(root)
        val fingerprint = sha256((listOf("katton-pack-compiler-v3", pack.syncId, pack.codeHash) + dependencyFingerprints).joinToString("\u0000").toByteArray())
        cache[fingerprint]?.takeIf { Files.isRegularFile(it.jar) }?.let { return it }
        val jar = root.resolve("pack-$fingerprint.jar")
        if (!Files.isRegularFile(jar)) {
            val staging = Files.createTempDirectory(root, ".compile-")
            try {
                val sources = pack.contentFiles.filter { ScriptPackSnapshots.isSource(it.relativePath) }.map { file ->
                    val path = staging.resolve("sources").resolve(file.relativePath).normalize()
                    require(path.startsWith(staging.resolve("sources")))
                    Files.createDirectories(path.parent)
                    Files.write(path, file.bytes)
                    path
                }
                val output = staging.resolve("classes")
                Files.createDirectories(output)
                if (sources.any { it.toString().endsWith(".kt", true) }) {
                    val diagnostics = StringBuilder()
                    val args = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "25", "-module-name", "pack_$fingerprint",
                        "-classpath", classpath.joinToString(java.io.File.pathSeparator), "-d", output.toString()) + sources.map(Path::toString)
                    val collector = object : MessageCollector {
                        private var errors = false
                        override fun clear() { errors = false }
                        override fun hasErrors() = errors
                        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
                            if (severity.isError) errors = true
                            if (severity == CompilerMessageSeverity.LOGGING) return
                            diagnostics.appendLine("$severity: ${location?.path ?: ""}:${location?.line ?: 0}: $message")
                            val relative = location?.path?.let { path -> runCatching { staging.resolve("sources").relativize(Path.of(path)).toString().replace('\\', '/') }.getOrNull() }
                            DevEvents.emit(if (severity.isError) "ERROR" else if (severity.isWarning) "WARNING" else "INFO",
                                message, pack.syncId, pack.hash, relative, location?.line, location?.column)
                        }
                    }
                    val compiler = K2JVMCompiler()
                    val arguments = compiler.createArguments()
                    compiler.parseArguments(args.toTypedArray(), arguments)
                    val result = compiler.exec(collector, Services.EMPTY, arguments)
                    check(result == ExitCode.OK) { "Pack ${pack.syncId}: Kotlin compilation failed:\n$diagnostics" }
                }
                val javaSources = sources.filter { it.toString().endsWith(".java", true) }
                if (javaSources.isNotEmpty()) {
                    val compiler = checkNotNull(javax.tools.ToolProvider.getSystemJavaCompiler()) { "Java sources require a JDK" }
                    val diagnostics = javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>()
                    compiler.getStandardFileManager(diagnostics, null, Charsets.UTF_8).use { manager ->
                        val options = listOf("--release", "25", "-proc:none", "-classpath", (classpath + listOf(output)).joinToString(java.io.File.pathSeparator), "-d", output.toString())
                        val ok = compiler.getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjectsFromPaths(javaSources)).call()
                        diagnostics.diagnostics.forEach { diagnostic ->
                            val relative = diagnostic.source?.toUri()?.let { staging.resolve("sources").relativize(Path.of(it)).toString().replace('\\', '/') }
                            DevEvents.emit(diagnostic.kind.name, diagnostic.getMessage(java.util.Locale.ROOT), pack.syncId, pack.hash,
                                relative, diagnostic.lineNumber.takeIf { it > 0 }?.toInt(), diagnostic.columnNumber.takeIf { it > 0 }?.toInt())
                        }
                        check(ok) {
                            "Pack ${pack.syncId}: Java compilation failed:\n${diagnostics.diagnostics.joinToString("\n")}" }
                    }
                }
                val temporary = staging.resolve("artifact.jar")
                JarOutputStream(Files.newOutputStream(temporary)).use { target ->
                    Files.walk(output).use { paths -> paths.filter(Files::isRegularFile).sorted().forEach { file ->
                        target.putNextEntry(JarEntry(output.relativize(file).toString().replace('\\', '/')).apply { time = 0 })
                        Files.copy(file, target)
                        target.closeEntry()
                    } }
                }
                Files.move(temporary, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.walk(staging).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
        }
        val files = JarFile(jar.toFile()).use { archive -> archive.entries().asSequence().filterNot { it.isDirectory }
            .associate { it.name to archive.getInputStream(it).use { input -> input.readAllBytes() } } }
        return PackArtifact(fingerprint, jar, files).also {
            if (cache.size >= 128) cache.clear()
            cache[fingerprint] = it
        }
    }
}

/** A dependency exports only its own output; resolution inside that output still uses its defining loader. */
internal class PackClassLoader(
    val artifact: PackArtifact,
    libraries: PrivateLibraries,
    parent: ClassLoader,
    private val dependencies: List<PackClassLoader>
) : java.net.URLClassLoader(libraries.paths.map { it.toUri().toURL() }.toTypedArray(), parent) {
    init {
        require(artifact.classNames.none { it in libraries.classes }) { "Own output conflicts with private library classes" }
        val visible = hashMapOf<String, PackClassLoader>()
        dependencies.forEach { dependency -> dependency.artifact.classNames.forEach { name ->
            require(visible.putIfAbsent(name, dependency).let { it == null || it === dependency }) { "Ambiguous package output class $name" }
        } }
        (artifact.classNames + libraries.classes.keys).forEach { name ->
            require(name !in visible) { "Private or own class collides with visible package output: $name" }
            require(parent.getResource(name.replace('.', '/') + ".class") == null) { "Private or own class collides with host type: $name" }
        }
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        findLoadedClass(name)?.let { return@synchronized it }
        val dependency = dependencies.firstOrNull { name in it.artifact.classNames }
        val result = when {
            name in artifact.classNames -> {
                val bytes = artifact.files.getValue(name.replace('.', '/') + ".class")
                defineClass(name, bytes, 0, bytes.size)
            }
            dependency != null -> dependency.loadClass(name)
            else -> super.loadClass(name, false)
        }
        if (resolve && result.classLoader === this) resolveClass(result)
        result
    }

    override fun getResource(name: String): java.net.URL? = ownResource(name) ?: findResource(name) ?: dependencies.firstNotNullOfOrNull { it.ownResource(name) } ?: parent.getResource(name)
    override fun getResources(name: String): java.util.Enumeration<java.net.URL> = java.util.Collections.enumeration(buildList {
        ownResource(name)?.let(::add)
        addAll(findResources(name).toList())
        dependencies.mapNotNullTo(this) { it.ownResource(name) }
        addAll(parent.getResources(name).toList())
    }.distinct())
    private fun ownResource(name: String): java.net.URL? = if (name in artifact.files) java.net.URI.create("jar:${artifact.jar.toUri()}!${java.net.URI(null, null, "/$name", null).rawPath}").toURL() else null
}

/** Keeps undeclared platform plugins/mods out of runtime lookup while preserving host class identity. */
internal class PackHostClassLoader(
    parent: ClassLoader,
    private val basePaths: List<Path>,
    private val dependencies: List<ResolvedScriptDependency>
) : ClassLoader(parent) {
    private val roots = (basePaths + dependencies.flatMap { it.classpath }).distinct()
    private val resources = ConcurrentHashMap<Pair<Path, String>, Boolean>()
    private fun contains(root: Path, name: String): Boolean = resources.computeIfAbsent(root to name) {
        if (Files.isDirectory(root)) {
            val path = root.resolve(name).normalize()
            path.startsWith(root) && Files.isRegularFile(path)
        } else runCatching {
            JarFile(root.toFile(), true, JarFile.OPEN_READ, Runtime.Version.parse("25")).use { it.getJarEntry(name) != null }
        }.getOrDefault(false)
    }
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        try { return ClassLoader.getPlatformClassLoader().loadClass(name) } catch (_: ClassNotFoundException) { }
        val resource = name.replace('.', '/') + ".class"
        if (basePaths.any { contains(it, resource) }) return super.loadClass(name, resolve)
        val owners = dependencies.filter { dependency -> dependency.classpath.any { contains(it, resource) } }
        if (owners.isEmpty()) throw ClassNotFoundException(name)
        val types = owners.map { (it.classLoader ?: parent).loadClass(name) }.distinct()
        check(types.size == 1) { "Conflicting host dependency identities for $name: ${owners.joinToString { it.id }}" }
        return types.single()
    }
    override fun getResource(name: String): java.net.URL? = getResources(name).asSequence().firstOrNull()
    override fun getResources(name: String): java.util.Enumeration<java.net.URL> = java.util.Collections.enumeration(buildList {
        ClassLoader.getPlatformClassLoader().getResource(name)?.let(::add)
        roots.filter { contains(it, name) }.forEach { root ->
            add(if (Files.isDirectory(root)) root.resolve(name).toUri().toURL()
                else java.net.URI.create("jar:${root.toUri()}!${java.net.URI(null, null, "/$name", null).rawPath}").toURL())
        }
    }.distinct())
}
