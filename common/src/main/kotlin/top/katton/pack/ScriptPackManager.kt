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

    @Volatile
    private var activeWorldPacks: List<ScriptPack> = emptyList()

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
        activeWorldPacks = emptyList()
    }

    @Synchronized
    fun refreshGlobalPacks() {
        globalPacks = scanScopePacks(gameDirectory, ScriptPackScope.GLOBAL)
    }

    private val warnedGlobalChanges = mutableSetOf<String>()
    private val globalResourceCandidate = ThreadLocal<List<ScriptPack>?>()
    internal fun captureGlobalResourceCandidate(): List<ScriptPack>? = globalResourceCandidate.get()
    internal fun stageGlobalResourceCandidate(packs: List<ScriptPack>?) {
        if (packs == null) globalResourceCandidate.remove() else globalResourceCandidate.set(packs)
    }
    @Synchronized
    fun finishGlobalResourceRefresh(success: Boolean) {
        if (success) globalResourceCandidate.get()?.let { globalPacks = it }
        globalResourceCandidate.remove()
    }

    /** Global bytecode remains bound to startup; publish only code-identical resource snapshots. */
    @Synchronized
    fun refreshGlobalResources() {
        val candidates = scanScopePacks(gameDirectory, ScriptPackScope.GLOBAL).associateBy { it.syncId }
        val previousIds = globalPacks.mapTo(hashSetOf()) { it.syncId }
        globalResourceCandidate.set(globalPacks.map { previous ->
            val candidate = candidates[previous.syncId]
            if (candidate != null && candidate.codeHash == previous.codeHash && candidate.enabled == previous.enabled) candidate
            else {
                if (warnedGlobalChanges.add("${previous.syncId}:${candidate?.codeHash}"))
                    LOGGER.warn("Global pack {} changed or was removed; restart required", previous.syncId)
                previous
            }
        })
        candidates.keys.filterNot { it in previousIds }.forEach { id ->
            if (warnedGlobalChanges.add("$id:new")) LOGGER.warn("New global pack {} requires a restart", id)
        }
    }

    @Synchronized
    fun refreshWorldPacks() {
        worldPacks = scanScopePacks(worldDirectory, ScriptPackScope.WORLD)
    }

    /** Scans a candidate without replacing the last-known-good active snapshot. */
    @Synchronized
    fun scanWorldPacksCandidate(): List<ScriptPack> {
        return scanScopePacks(worldDirectory, ScriptPackScope.WORLD)
    }
    @Synchronized
    internal fun scanGlobalPacksCandidate(): List<ScriptPack> = scanScopePacks(gameDirectory, ScriptPackScope.GLOBAL)

    /**
     * Publishes discovery metadata and the independently validated executable
     * snapshot only after script and resource activation succeeds.
     */
    @Synchronized
    fun publishWorldPacks(packs: List<ScriptPack>, executablePacks: List<ScriptPack> = packs.filter { it.enabled }) {
        require(packs.all { it.scope == ScriptPackScope.WORLD } &&
            executablePacks.all { it.scope == ScriptPackScope.WORLD }) {
            "Only world-scoped packs can be published as the world snapshot"
        }
        worldPacks = packs.toList()
        activeWorldPacks = executablePacks.filter { it.enabled }.distinctBy { it.syncId }
    }

    @Synchronized
    fun refreshLocalPacks() {
        refreshGlobalPacks()
        refreshWorldPacks()
    }

    fun collectExecutableWorldPacks(): List<ScriptPack> {
        return activeWorldPacks.toList()
    }

    fun collectExecutableGlobalPacks(): List<ScriptPack> {
        return (globalResourceCandidate.get() ?: globalPacks).asSequence().filter { it.enabled }.toList()
    }

    fun collectExecutablePacks(): List<ScriptPack> {
        return (globalPacks + activeWorldPacks)
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
                            path.fileName.toString().endsWith(".zip", ignoreCase = true) ->
                            scanPackZip(path, scope)?.let(discovered::add)
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

        return runCatching {
            ScriptPackSnapshots.directory(normalizedPackDirectory, manifestJson, maximumDiscoveredEntries).toPack(
                normalizedPackDirectory, scope, ScriptPackKind.DIRECTORY, syncIdOverride,
                forceEnabled ?: readEnabledState(normalizedPackDirectory, ScriptPackKind.DIRECTORY)
            )
        }.getOrElse {
            LOGGER.warn("Rejected script pack {}: {}", normalizedPackDirectory, it.message)
            null
        }
    }

    internal fun scanPackZip(path: Path, scope: ScriptPackScope): ScriptPack? = runCatching {
        ScriptPackSnapshots.zip(path).toPack(path.toAbsolutePath().normalize(), scope, ScriptPackKind.ZIP,
            enabledOverride = readEnabledState(path, ScriptPackKind.ZIP))
    }.getOrElse {
        LOGGER.warn("Rejected ZIP script pack {}: {}", path, it.message)
        null
    }

    internal fun scanPackJar(
        jarPath: Path,
        scope: ScriptPackScope,
        syncIdOverride: String? = null,
        forceEnabled: Boolean? = null,
        maximumArchiveEntries: Int = ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES
    ): ScriptPack? {
        LOGGER.warn("Executable JAR packs are no longer supported: {}. Move sources into a directory/ZIP with manifest.json and libraries into libs/.", jarPath)
        return null
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
        dataFiles: List<ScriptPackContentFile> = emptyList(),
        libraries: List<ScriptPackContentFile> = emptyList(),
        extraFiles: List<ScriptPackContentFile> = emptyList()
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed("katton-logical-pack-hash-v3".toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(manifestJson.toByteArray(StandardCharsets.UTF_8))
        digest.updateScriptFiles(scripts)
        digest.updateScriptFiles(javaFiles)
        digest.updateContentFiles(assetFiles)
        digest.updateContentFiles(dataFiles)
        digest.updateContentFiles(libraries)
        // Preserve existing hashes for packs without additional content.
        if (extraFiles.isNotEmpty()) {
            digest.updateFramed("extra-content-v1".toByteArray(StandardCharsets.UTF_8))
            digest.updateContentFiles(extraFiles)
        }

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
            ScriptPackKind.JAR, ScriptPackKind.ZIP -> packPath.resolveSibling("${packPath.fileName}.state.json")
        }
    }

    /**
     * Validates the expanded archive while extracting its optional Katton
     * manifest. Reading from the already bounded byte snapshot prevents a
     * symlink race and makes compressed zip bombs fail before class/resource
     * loaders see the archive.
     */
    private fun makeSyncId(scope: ScriptPackScope, id: String): String {
        return "${scope.serializedName}:$id"
    }

    private fun manifestWithoutSignature(manifestJson: String): String {
        val root = JsonParser.parseString(manifestJson).asJsonObject
        root.remove("signature")
        return root.toString()
    }

}
