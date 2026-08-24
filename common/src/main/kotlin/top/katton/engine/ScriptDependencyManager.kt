package top.katton.engine

import top.katton.api.LOGGER
import top.katton.pack.ScriptDependency
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
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
    private const val MAX_DEPENDENCY_SCAN_CACHE_ENTRIES = 128

    @Volatile
    var platform: ScriptPlatform = ScriptPlatform.UNKNOWN
        private set

    @Volatile
    private var resolver: PlatformDependencyResolver = PlatformDependencyResolver { null }

    private val availability = ConcurrentHashMap<String, Boolean>()
    private val classNameCache = ConcurrentHashMap<String, List<String>>()
    private val ambiguityCache = ConcurrentHashMap<String, Map<String, Set<String>>>()

    @JvmStatic
    fun install(platform: ScriptPlatform, resolver: PlatformDependencyResolver) {
        this.platform = platform
        this.resolver = resolver
        availability.clear()
        classNameCache.clear()
        ambiguityCache.clear()
    }

    fun resolve(
        packs: Collection<ScriptPack>,
        environment: ScriptEnvironment,
        phaseName: String? = null
    ): ScriptDependencySelection {
        val valid = mutableListOf<ScriptPack>()
        // Cache both hits and misses for this pass. A missing dependency may be
        // referenced by many packs, and platform plugin-manager lookups are not free.
        val resolutionCache = hashMapOf<String, ResolvedScriptDependency?>()
        val acceptedById = linkedMapOf<String, ResolvedScriptDependency>()
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
                val dependencyKey = dependency.id.lowercase(Locale.ROOT)
                val actual = if (resolutionCache.containsKey(dependencyKey)) {
                    resolutionCache[dependencyKey]
                } else {
                    resolver.resolve(dependency.id).also {
                        resolutionCache[dependencyKey] = it
                    }
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
            if (packValid) {
                valid += pack
                // Only dependencies that passed this pack's enabled/version checks
                // may enter the shared compiler and runtime classpaths. Previously,
                // an optional incompatible dependency (or one from an invalid pack)
                // could leak classes into every other source pack.
                packDependencies.forEach { dependency ->
                    acceptedById.putIfAbsent(dependency.id.lowercase(Locale.ROOT), dependency)
                }
            }
        }

        val resolved = acceptedById.values.toList()
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
        val cacheKey = dependencies
            .sortedBy { it.id.lowercase(Locale.ROOT) }
            .joinToString("||") { dependency ->
                buildString {
                    append(dependency.id.lowercase(Locale.ROOT)).append('@').append(dependency.version)
                    dependency.classLoader?.let { append("#").append(System.identityHashCode(it)) }
                    dependency.classpath.sortedBy(Path::toString).forEach { append('|').append(pathCacheKey(it)) }
                }
            }
        ambiguityCache[cacheKey]?.let { return it }

        // Keep one String owner for the common case and allocate a Set only for
        // actual collisions. Large plugin jars otherwise created one LinkedHashSet
        // for every class during each ambiguity scan.
        val firstOwners = hashMapOf<String, String>()
        val ambiguousOwners = linkedMapOf<String, MutableSet<String>>()
        dependencies.forEach { dependency ->
            dependency.classpath.forEach { path ->
                classNames(path).forEach { className ->
                    val firstOwner = firstOwners.putIfAbsent(className, dependency.id)
                    if (firstOwner != null && firstOwner != dependency.id) {
                        ambiguousOwners.getOrPut(className) { linkedSetOf(firstOwner) }.add(dependency.id)
                    }
                }
            }
        }
        return ambiguousOwners.also { result ->
            ambiguityCache[cacheKey] = result
            trimScanCache(ambiguityCache)
        }
    }

    private fun classNames(path: Path): List<String> {
        val normalized = path.toAbsolutePath().normalize()
        val cacheKey = pathCacheKey(normalized)
        return classNameCache.computeIfAbsent(cacheKey) { inspectClassNames(normalized) }
            .also { trimScanCache(classNameCache) }
    }

    private fun pathCacheKey(path: Path): String {
        val normalized = path.toAbsolutePath().normalize()
        return buildString {
            append(normalized)
            runCatching {
                append(':').append(Files.size(normalized))
                append(':').append(Files.getLastModifiedTime(normalized).toMillis())
            }
        }
    }

    private fun <V> trimScanCache(cache: ConcurrentHashMap<String, V>) {
        val overflow = cache.size - MAX_DEPENDENCY_SCAN_CACHE_ENTRIES
        if (overflow > 0) cache.keys.asSequence().take(overflow).forEach(cache::remove)
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
        append(dependency.id.lowercase(Locale.ROOT))
        append('@')
        append(dependency.version)
        dependency.classLoader?.let { loader ->
            // Paper can replace a plugin with a new classloader while keeping the
            // same name, version, and jar path. Runtime artifacts must not retain
            // or delegate to the previous plugin instance in that case.
            append("|loader:")
            append(loader.javaClass.name)
            append('@')
            append(System.identityHashCode(loader))
        }
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
            val tokens = alternative.split(Regex("[\\s,]+"))
                .filter(String::isNotBlank)
            tokens.isNotEmpty() && tokens.all { token -> matchesToken(actual, token) }
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
            val l = leftParts.getOrNull(index)
            val r = rightParts.getOrNull(index)
            val compared = when {
                l == null -> compareMissingPart(r!!)
                r == null -> -compareMissingPart(l)
                l.all(Char::isDigit) && r.all(Char::isDigit) -> l.trimStart('0').ifEmpty { "0" }
                    .let { ln -> r.trimStart('0').ifEmpty { "0" }.let { rn -> compareNumeric(ln, rn) } }
                l.all(Char::isDigit) -> 1
                r.all(Char::isDigit) -> -1
                else -> compareQualifiers(l, r)
            }
            if (compared != 0) return compared
        }
        return 0
    }

    private fun tokenize(version: String): List<String> =
        Regex("[0-9]+|[A-Za-z]+")
            // SemVer build metadata does not affect precedence.
            .findAll(version.substringBefore('+'))
            .map { it.value }
            .toList()

    /** Compares an absent release component on the left with [present] on the right. */
    private fun compareMissingPart(present: String): Int =
        if (present.all(Char::isDigit)) {
            compareNumeric("0", present.trimStart('0').ifEmpty { "0" })
        } else {
            compareQualifiers(RELEASE_QUALIFIER, present)
        }

    private fun compareQualifiers(left: String, right: String): Int {
        val leftRank = qualifierRank(left)
        val rightRank = qualifierRank(right)
        if (leftRank != rightRank) return leftRank.compareTo(rightRank)
        // final/ga/release and an absent qualifier all describe a release.
        if (leftRank == RELEASE_RANK) return 0
        return left.compareTo(right, ignoreCase = true)
    }

    private fun compareNumeric(left: String, right: String): Int =
        left.length.compareTo(right.length).takeIf { it != 0 } ?: left.compareTo(right)

    private fun qualifierRank(value: String): Int = when (value.lowercase(Locale.ROOT)) {
        "snapshot", "dev" -> -5
        "alpha", "a" -> -4
        "beta", "b" -> -3
        "milestone", "m" -> -2
        "rc", "cr" -> -1
        RELEASE_QUALIFIER, "final", "ga", "release" -> RELEASE_RANK
        else -> 0
    }

    private const val RELEASE_QUALIFIER = ""
    private const val RELEASE_RANK = 1
}

/** Parent used by compiled scripts to access declared Paper plugin classloaders. */
class DependencyDelegatingClassLoader(
    parent: ClassLoader,
    dependencyLoaders: List<ClassLoader>
) : ClassLoader(parent) {
    private val delegates = dependencyLoaders.distinct()
    // Classes returned by another loader are not visible to findLoadedClass on
    // this delegating loader. Cache those hits so repeated reflective lookups do
    // not retry the parent and every declared plugin loader.
    private val delegatedClasses = ConcurrentHashMap<String, Class<*>>()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            delegatedClasses[name]?.let { return it }
            try {
                return super.loadClass(name, resolve)
            } catch (_: ClassNotFoundException) {
                // Only a normal miss should fall through. Linkage and security
                // failures describe a broken class and must not be masked by a
                // different plugin loader that happens to export the same name.
            }
            for (delegate in delegates) {
                try {
                    val loaded = delegate.loadClass(name)
                    return delegatedClasses.putIfAbsent(name, loaded) ?: loaded
                } catch (_: ClassNotFoundException) {
                    // Try the next explicitly declared dependency.
                }
            }
            throw ClassNotFoundException(name)
        }
    }
}
