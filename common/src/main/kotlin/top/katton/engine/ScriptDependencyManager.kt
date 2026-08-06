package top.katton.engine

import top.katton.api.LOGGER
import top.katton.pack.ScriptDependency
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

data class ResolvedScriptDependency(
    val id: String,
    val version: String,
    val classpath: List<Path>,
    val classLoader: ClassLoader?,
    val enabled: Boolean = true
)

fun interface PlatformDependencyResolver {
    fun resolve(id: String): ResolvedScriptDependency?
}

data class ScriptDependencySelection(
    val validPacks: List<ScriptPack>,
    val resolved: List<ResolvedScriptDependency>,
    val fingerprints: List<String>,
    val errors: List<String>
)

/** Coordinates manifest dependency validation and compiler/runtime classpaths. */
object ScriptDependencyManager {
    @Volatile
    var platform: ScriptPlatform = ScriptPlatform.UNKNOWN
        private set

    @Volatile
    private var resolver: PlatformDependencyResolver = PlatformDependencyResolver { null }

    private val availability = ConcurrentHashMap<String, Boolean>()
    private val classNameCache = ConcurrentHashMap<String, List<String>>()

    @JvmStatic
    fun install(platform: ScriptPlatform, resolver: PlatformDependencyResolver) {
        this.platform = platform
        this.resolver = resolver
        availability.clear()
        classNameCache.clear()
    }

    fun resolve(
        packs: Collection<ScriptPack>,
        environment: ScriptEnvironment,
        phaseName: String? = null
    ): ScriptDependencySelection {
        val valid = mutableListOf<ScriptPack>()
        val resolvedById = linkedMapOf<String, ResolvedScriptDependency>()
        val errors = mutableListOf<String>()
        val isClient = environment == ScriptEnvironment.CLIENT

        for (pack in packs) {
            var packValid = true
            val applicable = pack.manifest.dependencies.filter { it.appliesTo(platform, isClient) }
            val packDependencies = mutableListOf<ResolvedScriptDependency>()
            if (platform == ScriptPlatform.PAPER && applicable.isNotEmpty() && phaseName != "READY") {
                packValid = false
                errors += "Pack '${pack.manifest.id}' declares Paper plugin dependencies, which are only available from ServerPhase.READY"
            }
            for (dependency in applicable) {
                val actual = resolvedById[dependency.id.lowercase()] ?: resolver.resolve(dependency.id)?.also {
                    resolvedById[dependency.id.lowercase()] = it
                }
                val failure = when {
                    actual == null -> "is not installed"
                    !actual.enabled -> "is installed but not enabled"
                    !VersionConstraint.matches(actual.version, dependency.version) ->
                        "has version ${actual.version}, which does not satisfy ${dependency.version}"
                    else -> null
                }
                if (failure != null && dependency.required) {
                    packValid = false
                    errors += "Pack '${pack.manifest.id}' requires ${platform.serializedName} dependency '${dependency.id}' ${dependency.version}, but it $failure"
                }
                if (failure == null && actual != null) packDependencies += actual
            }
            if (packValid && platform == ScriptPlatform.PAPER) {
                val ambiguous = findAmbiguousClasses(packDependencies)
                if (ambiguous.isNotEmpty()) {
                    packValid = false
                    val examples = ambiguous.entries.take(5).joinToString { (name, ids) -> "$name (${ids.joinToString()})" }
                    errors += "Pack '${pack.manifest.id}' declares Paper plugins that export ambiguous classes: $examples"
                }
            }
            availability[availabilityKey(pack, environment)] = packValid
            if (packValid) valid += pack
        }

        val resolved = resolvedById.values.toList()
        return ScriptDependencySelection(
            validPacks = valid,
            resolved = resolved,
            fingerprints = resolved.map(::fingerprint),
            errors = errors
        )
    }

    fun isPackAvailable(pack: ScriptPack, environment: ScriptEnvironment): Boolean =
        availability[availabilityKey(pack, environment)] ?: true

    fun find(id: String): ResolvedScriptDependency? = resolver.resolve(id)

    private fun availabilityKey(pack: ScriptPack, environment: ScriptEnvironment) = "${environment.name}:${pack.syncId}"

    private fun findAmbiguousClasses(dependencies: List<ResolvedScriptDependency>): Map<String, Set<String>> {
        if (dependencies.size < 2) return emptyMap()
        val owners = linkedMapOf<String, MutableSet<String>>()
        dependencies.forEach { dependency ->
            dependency.classpath.flatMap(::classNames).forEach { className ->
                owners.getOrPut(className) { linkedSetOf() }.add(dependency.id)
            }
        }
        return owners.filterValues { it.size > 1 }
    }

    private fun classNames(path: Path): List<String> {
        val normalized = path.toAbsolutePath().normalize()
        val cacheKey = buildString {
            append(normalized)
            runCatching {
                append(':').append(Files.size(normalized))
                append(':').append(Files.getLastModifiedTime(normalized).toMillis())
            }
        }
        return classNameCache.computeIfAbsent(cacheKey) { inspectClassNames(normalized) }
    }

    private fun inspectClassNames(path: Path): List<String> = runCatching {
        when {
            Files.isDirectory(path) -> Files.walk(path).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }
                    .map { path.relativize(it).toString().replace('\\', '/').removeSuffix(".class").replace('/', '.') }
                    .toList()
            }
            Files.isRegularFile(path) -> JarFile(path.toFile()).use { jar ->
                jar.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && !it.startsWith("META-INF/versions/") }
                    .map { it.removeSuffix(".class").replace('/', '.') }
                    .toList()
            }
            else -> emptyList()
        }
    }.getOrElse {
        LOGGER.warn("Unable to inspect dependency classes at {}", path, it)
        emptyList()
    }

    private fun fingerprint(dependency: ResolvedScriptDependency): String = buildString {
        append(dependency.id.lowercase())
        append('@')
        append(dependency.version)
        dependency.classpath.sortedBy(Path::toString).forEach { path ->
            append('|')
            append(path.toAbsolutePath().normalize())
            runCatching {
                if (Files.isDirectory(path)) {
                    Files.walk(path).use { stream ->
                        stream.filter { Files.isRegularFile(it) }
                            .sorted()
                            .forEach { file ->
                                append(':').append(path.relativize(file))
                                append(':').append(Files.size(file))
                                append(':').append(Files.getLastModifiedTime(file).toMillis())
                            }
                    }
                } else {
                    append(':').append(Files.size(path))
                    append(':').append(Files.getLastModifiedTime(path).toMillis())
                }
            }.onFailure {
                LOGGER.debug("Unable to fingerprint dependency path {}", path, it)
            }
        }
    }
}

/** Small cross-loader version predicate implementation used by script manifests. */
object VersionConstraint {
    private val comparator = Comparator<String> { left, right -> compareVersions(left, right) }

    fun matches(actual: String, expression: String): Boolean {
        val normalized = expression.trim()
        if (normalized.isEmpty() || normalized == "*") return true
        return normalized.split("||").any { alternative ->
            alternative.split(Regex("[\\s,]+"))
                .filter(String::isNotBlank)
                .all { token -> matchesToken(actual, token) }
        }
    }

    private fun matchesToken(actual: String, token: String): Boolean {
        val operator = listOf(">=", "<=", "==", ">", "<", "=").firstOrNull(token::startsWith) ?: "="
        val expected = token.removePrefix(operator).trim()
        if (expected.isEmpty()) return false
        val compared = comparator.compare(actual, expected)
        return when (operator) {
            ">=" -> compared >= 0
            "<=" -> compared <= 0
            ">" -> compared > 0
            "<" -> compared < 0
            else -> compared == 0
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = tokenize(left)
        val rightParts = tokenize(right)
        val count = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until count) {
            val l = leftParts.getOrNull(index) ?: "0"
            val r = rightParts.getOrNull(index) ?: "0"
            val compared = when {
                l.all(Char::isDigit) && r.all(Char::isDigit) -> l.trimStart('0').ifEmpty { "0" }
                    .let { ln -> r.trimStart('0').ifEmpty { "0" }.let { rn -> compareNumeric(ln, rn) } }
                l.all(Char::isDigit) -> 1
                r.all(Char::isDigit) -> -1
                else -> qualifierRank(l).compareTo(qualifierRank(r)).takeIf { it != 0 } ?: l.compareTo(r, true)
            }
            if (compared != 0) return compared
        }
        return 0
    }

    private fun tokenize(version: String): List<String> =
        Regex("[0-9]+|[A-Za-z]+")
            .findAll(version)
            .map { it.value }
            .toList()

    private fun compareNumeric(left: String, right: String): Int =
        left.length.compareTo(right.length).takeIf { it != 0 } ?: left.compareTo(right)

    private fun qualifierRank(value: String): Int = when (value.lowercase()) {
        "snapshot", "dev" -> -5
        "alpha", "a" -> -4
        "beta", "b" -> -3
        "milestone", "m" -> -2
        "rc", "cr" -> -1
        "final", "ga", "release" -> 1
        else -> 0
    }
}

/** Parent used by compiled scripts to access declared Paper plugin classloaders. */
class DependencyDelegatingClassLoader(
    parent: ClassLoader,
    dependencyLoaders: List<ClassLoader>
) : ClassLoader(parent) {
    private val delegates = dependencyLoaders.distinct()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            runCatching { return super.loadClass(name, resolve) }
            for (delegate in delegates) {
                val loaded = runCatching { delegate.loadClass(name) }.getOrNull() ?: continue
                return loaded
            }
            throw ClassNotFoundException(name)
        }
    }
}
