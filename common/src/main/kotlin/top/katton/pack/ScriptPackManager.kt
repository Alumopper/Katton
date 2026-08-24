package top.katton.pack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.katton.api.LOGGER
import top.katton.engine.ScriptDependencyManager
import top.katton.engine.ScriptEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.io.path.absolutePathString

object ScriptPackManager {

    private const val PACKS_DIR_NAME = "kattonpacks"
    private const val MANIFEST_FILE_NAME = "manifest.json"
    private const val STATE_FILE_NAME = ".kattonpack.state.json"
    @Volatile
    private var gameDirectory: Path? = null

    @Volatile
    private var worldDirectory: Path? = null

    @Volatile
    private var globalPacks: List<ScriptPack> = emptyList()

    @Volatile
    private var worldPacks: List<ScriptPack> = emptyList()

    fun setGameDirectory(path: Path?) {
        gameDirectory = path
    }

    fun setWorldDirectory(path: Path?) {
        worldDirectory = path
    }

    fun getGlobalScriptDirectory(): Path? {
        return gameDirectory?.resolve(PACKS_DIR_NAME)
    }

    fun getWorldScriptDirectory(): Path? {
        return worldDirectory?.resolve(PACKS_DIR_NAME)
    }

    @Synchronized
    fun clearWorldDirectory() {
        worldDirectory = null
        worldPacks = emptyList()
    }

    @Synchronized
    fun refreshGlobalPacks() {
        globalPacks = scanScopePacks(gameDirectory, ScriptPackScope.GLOBAL)
    }

    @Synchronized
    fun refreshWorldPacks() {
        worldPacks = scanScopePacks(worldDirectory, ScriptPackScope.WORLD)
    }

    @Synchronized
    fun refreshLocalPacks() {
        refreshGlobalPacks()
        refreshWorldPacks()
    }

    fun collectExecutableWorldPacks(): List<ScriptPack> {
        return worldPacks.asSequence().filter { it.enabled }.toList()
    }

    fun collectExecutableGlobalPacks(): List<ScriptPack> {
        return globalPacks.asSequence().filter { it.enabled }.toList()
    }

    fun collectExecutablePacks(): List<ScriptPack> {
        return (globalPacks + worldPacks)
            .asSequence()
            .filter { it.enabled }
            .toList()
    }

    fun collectServerSyncPacks(): List<ScriptPack> {
        val candidates = collectExecutablePacks()
            .asSequence()
            .filter { it.manifest.clientSync }
            .toList()
        return candidates.filter { ScriptDependencyManager.isPackAvailable(it, ScriptEnvironment.SERVER) }
    }

    fun listLocalPacksForGui(lockGlobalInWorld: Boolean): List<ScriptPackView> {
        return (globalPacks + worldPacks)
            .sortedWith(
                compareBy(
                    { it.scope.ordinal },
                    { it.manifest.name.lowercase(Locale.ROOT) },
                    { it.manifest.id.lowercase(Locale.ROOT) }
                )
            )
            .map { pack ->
                val locked = lockGlobalInWorld && pack.scope == ScriptPackScope.GLOBAL
                ScriptPackView(
                    syncId = pack.syncId,
                    scope = pack.scope,
                    kind = pack.kind,
                    id = pack.manifest.id,
                    name = pack.manifest.name,
                    version = pack.manifest.version,
                    description = pack.manifest.description,
                    authors = pack.manifest.authors,
                    hash = pack.hash,
                    enabled = pack.enabled,
                    locked = locked,
                    sourcePath = pack.location.absolutePathString()
                )
            }
    }

    fun getPackBySyncId(syncId: String): ScriptPack? {
        return (globalPacks + worldPacks).firstOrNull { it.syncId == syncId }
    }

    @Synchronized
    fun setPackEnabled(syncId: String, enabled: Boolean): Boolean {
        val pack = getPackBySyncId(syncId) ?: return false
        if (pack.scope == ScriptPackScope.SERVER_CACHE) {
            return false
        }

        val stateFile = resolveStateFile(pack.location, pack.kind)
        val stateJson = JsonObject().apply {
            addProperty("enabled", enabled)
        }

        return runCatching {
            SafePackFileIo.writeUtf8Atomically(
                stateFile,
                stateJson.toString(),
                ScriptPackFileLimits.MAX_STATE_BYTES,
                "script pack state"
            )
            when (pack.scope) {
                ScriptPackScope.GLOBAL -> refreshGlobalPacks()
                ScriptPackScope.WORLD -> refreshWorldPacks()
                ScriptPackScope.SERVER_CACHE -> Unit
            }
            true
        }.getOrElse {
            LOGGER.warn("Failed to persist pack enabled state for {}", syncId, it)
            false
        }
    }

    private fun scanScopePacks(rootDirectory: Path?, scope: ScriptPackScope): List<ScriptPack> {
        if (rootDirectory == null) return emptyList()

        val packsRoot = rootDirectory.resolve(PACKS_DIR_NAME)
        if (!Files.isDirectory(packsRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(packsRoot)) {
            return emptyList()
        }

        return runCatching {
            val discovered = mutableListOf<ScriptPack>()
            var rootEntryCount = 0
            Files.list(packsRoot).use { stream ->
                stream.forEach { path ->
                    rootEntryCount++
                    require(rootEntryCount <= ScriptPackFileLimits.MAX_PACK_ROOT_ENTRIES) {
                        "Script pack root contains too many entries " +
                            "(maximum ${ScriptPackFileLimits.MAX_PACK_ROOT_ENTRIES})"
                    }
                    when {
                        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ->
                            scanPackDirectory(path, scope)?.let(discovered::add)
                        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                            path.fileName.toString().endsWith(".jar", ignoreCase = true) ->
                            scanPackJar(path, scope)?.let(discovered::add)
                    }
                }
            }
            val duplicateIds = discovered
                .groupBy { it.syncId.lowercase(Locale.ROOT) }
                .filterValues { it.size > 1 }
                .keys
            duplicateIds.forEach { duplicateId ->
                LOGGER.warn("Ignoring duplicate script pack id '{}' under {}", duplicateId, packsRoot)
            }
            discovered
                .filterNot { it.syncId.lowercase(Locale.ROOT) in duplicateIds }
                .sortedWith(
                    compareBy<ScriptPack> { it.manifest.name.lowercase(Locale.ROOT) }
                        .thenBy { it.manifest.id.lowercase(Locale.ROOT) }
                )
        }.getOrElse {
            LOGGER.warn("Failed to scan script packs under {}", packsRoot, it)
            emptyList()
        }
    }

    internal fun scanPackDirectory(
        packDirectory: Path,
        scope: ScriptPackScope,
        syncIdOverride: String? = null,
        forceEnabled: Boolean? = null,
        maximumDiscoveredEntries: Int = ScriptPackFileLimits.MAX_DIRECTORY_ENTRIES
    ): ScriptPack? {
        require(maximumDiscoveredEntries in 1..ScriptPackFileLimits.MAX_DIRECTORY_ENTRIES) {
            "Directory entry limit must be between 1 and ${ScriptPackFileLimits.MAX_DIRECTORY_ENTRIES}"
        }
        val normalizedPackDirectory = packDirectory.toAbsolutePath().normalize()
        if (!Files.isDirectory(normalizedPackDirectory, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(normalizedPackDirectory)
        ) {
            return null
        }

        // Read the manifest before walking content so its bytes participate in
        // the same per-pack memory budget used by the network bundle.
        val manifestFile = normalizedPackDirectory.resolve(MANIFEST_FILE_NAME)
        val manifestBytes = runCatching {
            SafePackFileIo.readBytes(
                manifestFile,
                ScriptPackFileLimits.MAX_MANIFEST_BYTES,
                "script pack manifest"
            )
        }
            .getOrElse {
                LOGGER.warn("Failed to read manifest from {}", manifestFile, it)
                return null
            }
        val manifestJson = String(manifestBytes, StandardCharsets.UTF_8)

        val manifest = runCatching {
            ScriptPackManifest.parse(
                normalizedPackDirectory,
                manifestJson,
                fallbackIdOverride = syncIdOverride?.substringAfter(':')
            )
        }
            .getOrElse {
                LOGGER.warn("Invalid Katton pack manifest at {}: {}", manifestFile, it.message)
                return null
            }

        val collected = runCatching {
            collectDirectoryFiles(
                normalizedPackDirectory,
                ScriptPackReadBudget(manifestBytes.size),
                maximumDiscoveredEntries
            )
        }.getOrElse {
            LOGGER.warn("Failed to collect safe pack content from {}: {}", normalizedPackDirectory, it.message)
            return null
        }
        val scriptFiles = collected.scripts
        val javaFiles = collected.javaFiles
        val assetFiles = collected.assetFiles
        val dataFiles = collected.dataFiles
        // Signature bytes are intentionally excluded from the compilation key:
        // re-signing identical code must not trigger a Kotlin/Java recompile.
        val codeHash = computeScriptHash(manifestWithoutSignature(manifestJson), scriptFiles, javaFiles)
        val hash = computeScriptHash(manifestJson, scriptFiles, javaFiles, assetFiles, dataFiles)
        val enabled = forceEnabled
            ?: readEnabledState(normalizedPackDirectory, ScriptPackKind.DIRECTORY)
            ?: manifest.enabledByDefault
        val syncId = syncIdOverride ?: makeSyncId(scope, manifest.id)
        val sourceContentFiles = (scriptFiles + javaFiles).map {
            ScriptPackContentFile(
                relativePath = it.relativePath,
                absolutePath = it.absolutePath,
                bytes = it.bytes
            )
        }
        val contentFiles = sourceContentFiles + assetFiles + dataFiles

        return ScriptPack(
            syncId = syncId,
            scope = scope,
            kind = ScriptPackKind.DIRECTORY,
            location = normalizedPackDirectory,
            manifestJson = manifestJson,
            manifest = manifest,
            enabled = enabled,
            hash = hash,
            codeHash = codeHash,
            scripts = scriptFiles,
            contentFiles = contentFiles,
            compiledJar = null
        ).also {
            LOGGER.info(
                "Discovered source pack {} with {} .kt scripts, {} .java files, {} asset files, and {} data files at {}",
                it.manifest.name,
                it.scripts.size,
                javaFiles.size,
                assetFiles.size,
                dataFiles.size,
                it.location
            )
        }
    }

    internal fun scanPackJar(
        jarPath: Path,
        scope: ScriptPackScope,
        syncIdOverride: String? = null,
        forceEnabled: Boolean? = null,
        maximumArchiveEntries: Int = ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES
    ): ScriptPack? {
        require(maximumArchiveEntries in 1..ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES) {
            "Archive entry limit must be between 1 and ${ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES}"
        }
        val normalizedJarPath = jarPath.toAbsolutePath().normalize()
        val jarBytes = runCatching {
            SafePackFileIo.readBytes(
                normalizedJarPath,
                ScriptPackFileLimits.MAX_FILE_BYTES,
                "script pack jar"
            )
        }
            .getOrElse {
                LOGGER.warn("Failed to read script pack jar {}", normalizedJarPath, it)
                return null
            }

        val embeddedManifest = runCatching {
            inspectArchiveAndReadManifest(jarBytes, maximumArchiveEntries)
        }
            .getOrElse {
                LOGGER.warn("Rejected unsafe or malformed script pack jar {}: {}", normalizedJarPath, it.message)
                return null
            }
        val manifestJson = embeddedManifest ?: createFallbackManifestJson(normalizedJarPath)
        val manifestBytes = manifestJson.toByteArray(StandardCharsets.UTF_8)
        if (manifestBytes.size > ScriptPackFileLimits.MAX_MANIFEST_BYTES ||
            manifestBytes.size.toLong() + jarBytes.size > ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES
        ) {
            LOGGER.warn("Rejected script pack jar {} because its manifest/content budget is too large", normalizedJarPath)
            return null
        }
        val manifest = runCatching { ScriptPackManifest.parse(normalizedJarPath, manifestJson) }
            .getOrElse {
                LOGGER.warn("Invalid Katton jar pack manifest at {}: {}", normalizedJarPath, it.message)
                return null
            }
        val enabled = forceEnabled
            ?: readEnabledState(normalizedJarPath, ScriptPackKind.JAR)
            ?: manifest.enabledByDefault
        val syncId = syncIdOverride ?: makeSyncId(scope, manifest.id)
        val contentFile = ScriptPackContentFile(
            relativePath = normalizedJarPath.fileName.toString(),
            absolutePath = normalizedJarPath,
            bytes = jarBytes
        )
        val hash = computeJarHash(manifestJson, contentFile)
        val codeHash = computeJarHash(manifestWithoutSignature(manifestJson), contentFile)

        return ScriptPack(
            syncId = syncId,
            scope = scope,
            kind = ScriptPackKind.JAR,
            location = normalizedJarPath,
            manifestJson = manifestJson,
            manifest = manifest,
            enabled = enabled,
            hash = hash,
            codeHash = codeHash,
            scripts = emptyList(),
            contentFiles = listOf(contentFile),
            compiledJar = normalizedJarPath
        ).also {
            LOGGER.info("Discovered jar pack {} at {}", it.manifest.name, it.location)
        }
    }

    /**
     * Walks a directory pack exactly once and classifies every retained file.
     * Source files below `assets/` or `data/` are content, never executable
     * sources; this also guarantees that a relative path occurs only once in a
     * synchronized bundle.
     */
    private fun collectDirectoryFiles(
        packDirectory: Path,
        budget: ScriptPackReadBudget,
        maximumDiscoveredEntries: Int
    ): CollectedDirectoryFiles {
        val realRoot = packDirectory.toRealPath()
        val scripts = mutableListOf<ScriptPackScriptFile>()
        val javaFiles = mutableListOf<ScriptPackScriptFile>()
        val assetFiles = mutableListOf<ScriptPackContentFile>()
        val dataFiles = mutableListOf<ScriptPackContentFile>()
        val portablePaths = HashSet<String>()
        var discoveredEntries = 0

        Files.walk(packDirectory).use { stream ->
            stream.forEach { candidate ->
                if (candidate == packDirectory) return@forEach
                discoveredEntries++
                require(discoveredEntries <= maximumDiscoveredEntries) {
                    "Pack contains too many directory entries (maximum $maximumDiscoveredEntries)"
                }
                require(!Files.isSymbolicLink(candidate)) {
                    "Symbolic links are not allowed in script packs: $candidate"
                }
                if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) return@forEach

                // Resolve every retained file as a second containment check for
                // provider-specific link types such as Windows junctions.
                val realFile = candidate.toRealPath()
                require(realFile.startsWith(realRoot)) { "Pack file escapes its root: $candidate" }
                val relative = normalizedRelativePath(packDirectory, candidate)

                val target = when {
                    isPackContentRelativePath(relative, "assets") -> assetFiles
                    isPackContentRelativePath(relative, "data") -> dataFiles
                    relative.endsWith(".kt", ignoreCase = true) -> null
                    relative.endsWith(".java", ignoreCase = true) -> null
                    else -> return@forEach
                }
                require(ScriptPackFileLimits.isPortableRelativePath(relative)) {
                    "Pack file path is unsafe or not portable across clients: $relative"
                }
                require(portablePaths.add(ScriptPackFileLimits.portablePathKey(relative))) {
                    "Pack contains paths that collide on a case-insensitive client: $relative"
                }
                val bytes = budget.readContentFile(candidate, "script pack file '$relative'")
                if (target != null) {
                    target += ScriptPackContentFile(relative, candidate, bytes)
                } else {
                    val script = ScriptPackScriptFile(relative, candidate, bytes)
                    if (relative.endsWith(".kt", ignoreCase = true)) scripts += script else javaFiles += script
                }
            }
        }

        return CollectedDirectoryFiles(
            scripts = scripts.sortedBy { it.relativePath },
            javaFiles = javaFiles.sortedBy { it.relativePath },
            assetFiles = assetFiles.sortedBy { it.relativePath },
            dataFiles = dataFiles.sortedBy { it.relativePath }
        )
    }

    /**
     * Reloads one persisted network-cache container using the advertised hash
     * as the unambiguous kind discriminator. Directory bundles contain their
     * sources directly; a compiled JAR bundle contains one root-level jar plus
     * the transmitted manifest alongside it.
     */
    internal fun scanCachedPackContainer(
        containerDirectory: Path,
        syncId: String,
        expectedHash: String
    ): ScriptPack? {
        if (!Files.isDirectory(containerDirectory, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(containerDirectory)
        ) {
            return null
        }

        val jarCandidates = runCatching {
            Files.list(containerDirectory).use { entries ->
                entries
                    .filter { path ->
                        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                            !Files.isSymbolicLink(path) &&
                            path.fileName.toString().endsWith(".jar", ignoreCase = true)
                    }
                    .sorted()
                    .toList()
            }
        }.getOrElse {
            LOGGER.warn("Failed to inspect cached script pack container {}", containerDirectory, it)
            return null
        }
        if (jarCandidates.size > 1) {
            LOGGER.warn("Rejecting cached script pack container {} with multiple root JARs", containerDirectory)
            return null
        }

        jarCandidates.forEach { jarPath ->
            val jarPack = scanPackJar(
                jarPath = jarPath,
                scope = ScriptPackScope.SERVER_CACHE,
                syncIdOverride = syncId,
                forceEnabled = true
            )
            if (jarPack?.hash == expectedHash) return jarPack
        }

        return scanPackDirectory(
            packDirectory = containerDirectory,
            scope = ScriptPackScope.SERVER_CACHE,
            syncIdOverride = syncId,
            forceEnabled = true
        )?.takeIf { pack -> pack.hash == expectedHash }
    }

    internal fun computeScriptHash(
        manifestJson: String,
        scripts: List<ScriptPackScriptFile>,
        javaFiles: List<ScriptPackScriptFile> = emptyList(),
        assetFiles: List<ScriptPackContentFile> = emptyList(),
        dataFiles: List<ScriptPackContentFile> = emptyList()
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed("katton-directory-pack-hash-v2".toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(manifestJson.toByteArray(StandardCharsets.UTF_8))
        digest.updateScriptFiles(scripts)
        digest.updateScriptFiles(javaFiles)
        digest.updateContentFiles(assetFiles)
        digest.updateContentFiles(dataFiles)

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun computeJarHash(manifestJson: String, jarFile: ScriptPackContentFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed("katton-jar-pack-hash-v2".toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(manifestJson.toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(jarFile.relativePath.toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(jarFile.bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun MessageDigest.updateScriptFiles(files: List<ScriptPackScriptFile>) {
        updateInt(files.size)
        files.sortedBy { it.relativePath }.forEach { file ->
            updateFramed(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            updateFramed(file.bytes)
        }
    }

    private fun MessageDigest.updateContentFiles(files: List<ScriptPackContentFile>) {
        updateInt(files.size)
        files.sortedBy { it.relativePath }.forEach { file ->
            updateFramed(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            updateFramed(file.bytes)
        }
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

    private fun readEnabledState(packPath: Path, kind: ScriptPackKind): Boolean? {
        val stateFile = resolveStateFile(packPath, kind)
        if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(stateFile)) {
            return null
        }

        return runCatching {
            val stateJson = SafePackFileIo.readUtf8(
                stateFile,
                ScriptPackFileLimits.MAX_STATE_BYTES,
                "script pack state"
            )
            val root = JsonParser.parseString(stateJson).asJsonObject
            val enabledElement = root.get("enabled") ?: return@runCatching null
            if (enabledElement.isJsonPrimitive && enabledElement.asJsonPrimitive.isBoolean) {
                enabledElement.asBoolean
            } else {
                null
            }
        }.getOrNull()
    }

    private fun resolveStateFile(packPath: Path, kind: ScriptPackKind): Path {
        return when (kind) {
            ScriptPackKind.DIRECTORY -> packPath.resolve(STATE_FILE_NAME)
            ScriptPackKind.JAR -> packPath.resolveSibling("${packPath.fileName}.state.json")
        }
    }

    /**
     * Validates the expanded archive while extracting its optional Katton
     * manifest. Reading from the already bounded byte snapshot prevents a
     * symlink race and makes compressed zip bombs fail before class/resource
     * loaders see the archive.
     */
    private fun inspectArchiveAndReadManifest(jarBytes: ByteArray, maximumArchiveEntries: Int): String? {
        require(
            jarBytes.size >= 4 && jarBytes[0] == 'P'.code.toByte() && jarBytes[1] == 'K'.code.toByte()
        ) { "File does not have a ZIP/JAR header" }
        var entryCount = 0
        var expandedBytes = 0L
        var rootManifest: ByteArray? = null
        var metadataManifest: ByteArray? = null
        val entryNames = HashSet<String>()

        ZipInputStream(ByteArrayInputStream(jarBytes)).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                val name = entry.name
                validateArchiveEntryName(name)
                require(entryNames.add(name)) { "Archive contains duplicate entry '$name'" }

                // Count directories too. An archive can otherwise contain an
                // excessive number of zero-byte directory records without ever
                // consuming the expanded-byte budget below.
                entryCount++
                require(entryCount <= maximumArchiveEntries) {
                    "Archive contains too many entries (maximum $maximumArchiveEntries)"
                }
                if (entry.isDirectory) {
                    archive.closeEntry()
                    continue
                }

                val remaining = ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES.toLong() - expandedBytes
                require(remaining >= 0L) {
                    "Archive expands beyond ${ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES} bytes"
                }
                val isKattonManifest = name == MANIFEST_FILE_NAME ||
                    name == "META-INF/katton/$MANIFEST_FILE_NAME"
                val perEntryLimit = minOf(
                    if (isKattonManifest) {
                        ScriptPackFileLimits.MAX_MANIFEST_BYTES
                    } else {
                        ScriptPackFileLimits.MAX_FILE_BYTES
                    },
                    remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                )
                val bytes = readArchiveEntry(archive, perEntryLimit, retain = isKattonManifest)
                expandedBytes += bytes.size
                if (isKattonManifest) {
                    if (name == MANIFEST_FILE_NAME) rootManifest = bytes.content
                    else metadataManifest = bytes.content
                }
                archive.closeEntry()
            }
        }

        return (rootManifest ?: metadataManifest)?.let { String(it, StandardCharsets.UTF_8) }
    }

    private fun createFallbackManifestJson(jarPath: Path): String {
        val id = jarPath.fileName.toString().removeSuffix(".jar")
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("name", id)
            addProperty("version", "compiled")
            addProperty("description", "Compiled Katton script pack")
            add("authors", JsonArray())
            add("dependencies", JsonArray())
            addProperty("enabled", true)
        }.toString()
    }

    private fun makeSyncId(scope: ScriptPackScope, id: String): String {
        return "${scope.serializedName}:$id"
    }

    private fun manifestWithoutSignature(manifestJson: String): String {
        val root = JsonParser.parseString(manifestJson).asJsonObject
        root.remove("signature")
        return root.toString()
    }

    private fun normalizedRelativePath(root: Path, file: Path): String {
        val relative = root.relativize(file).normalize()
        require(!relative.isAbsolute && relative.none { it.toString() == ".." }) {
            "Pack file has an unsafe relative path: $file"
        }
        val serialized = relative.toString().replace('\\', '/')
        require(serialized.isNotBlank() && serialized.length <= ScriptPackFileLimits.MAX_RELATIVE_PATH_CHARS) {
            "Pack file path is empty or longer than ${ScriptPackFileLimits.MAX_RELATIVE_PATH_CHARS} characters: $file"
        }
        return serialized
    }

    private fun isPackContentRelativePath(relativePath: String, directoryName: String): Boolean {
        return relativePath.startsWith("$directoryName/")
    }

    private fun validateArchiveEntryName(name: String) {
        require(name.isNotBlank() && name.length <= ScriptPackFileLimits.MAX_RELATIVE_PATH_CHARS) {
            "Archive entry name is empty or too long"
        }
        require('\\' !in name && '\u0000' !in name && !name.startsWith('/')) {
            "Archive contains unsafe entry '$name'"
        }
        require(name.split('/').none { it == ".." }) { "Archive entry escapes its root: $name" }
    }

    private fun readArchiveEntry(
        archive: ZipInputStream,
        maximumBytes: Int,
        retain: Boolean
    ): ArchiveEntryBytes {
        val output = if (retain) ByteArrayOutputStream(minOf(maximumBytes, 8 * 1024)) else null
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = archive.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maximumBytes) { "Archive entry is too large (maximum $maximumBytes bytes)" }
            output?.write(buffer, 0, read)
        }
        return ArchiveEntryBytes(total, output?.toByteArray())
    }

    private data class ArchiveEntryBytes(val size: Int, val content: ByteArray?)

    private data class CollectedDirectoryFiles(
        val scripts: List<ScriptPackScriptFile>,
        val javaFiles: List<ScriptPackScriptFile>,
        val assetFiles: List<ScriptPackContentFile>,
        val dataFiles: List<ScriptPackContentFile>
    )
}
