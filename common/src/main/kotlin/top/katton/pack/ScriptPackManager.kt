package top.katton.pack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.katton.api.LOGGER
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.jar.JarFile
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

    fun collectExecutablePacks(): List<ScriptPack> {
        return (globalPacks + worldPacks)
            .asSequence()
            .filter { it.enabled }
            .toList()
    }

    fun collectServerSyncPacks(): List<ScriptPack> {
        return collectExecutablePacks()
    }

    fun listLocalPacksForGui(lockGlobalInWorld: Boolean): List<ScriptPackView> {
        return (globalPacks + worldPacks)
            .sortedWith(compareBy<ScriptPack>({ it.scope.ordinal }, { it.manifest.name.lowercase() }, { it.manifest.id.lowercase() }))
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
            stateFile.parent?.let(Files::createDirectories)
            Files.writeString(
                stateFile,
                stateJson.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
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
        if (!Files.isDirectory(packsRoot)) {
            return emptyList()
        }

        return runCatching {
            val discovered = mutableListOf<ScriptPack>()
            Files.list(packsRoot).use { stream ->
                stream.forEach { path ->
                    when {
                        Files.isDirectory(path) -> scanPackDirectory(path, scope)?.let(discovered::add)
                        Files.isRegularFile(path) && path.fileName.toString().endsWith(".jar", ignoreCase = true) ->
                            scanPackJar(path, scope)?.let(discovered::add)
                    }
                }
            }
            discovered.sortedWith(compareBy<ScriptPack> { it.manifest.name.lowercase() }.thenBy { it.manifest.id.lowercase() })
        }.getOrElse {
            LOGGER.warn("Failed to scan script packs under {}", packsRoot, it)
            emptyList()
        }
    }

    internal fun scanPackDirectory(
        packDirectory: Path,
        scope: ScriptPackScope,
        syncIdOverride: String? = null,
        forceEnabled: Boolean? = null
    ): ScriptPack? {
        val manifestFile = packDirectory.resolve(MANIFEST_FILE_NAME)
        if (!Files.isRegularFile(manifestFile)) {
            return null
        }

        val manifestJson = runCatching { Files.readString(manifestFile, StandardCharsets.UTF_8) }
            .getOrElse {
                LOGGER.warn("Failed to read manifest from {}", manifestFile, it)
                return null
            }

        val manifest = ScriptPackManifest.parse(packDirectory, manifestJson)
        val scriptFiles = collectScriptFiles(packDirectory)
        if (scriptFiles.isEmpty()) {
            LOGGER.warn("Skipping script pack {} because it contains no .kt scripts", packDirectory)
            return null
        }
        val hash = computeScriptHash(manifestJson, scriptFiles)
        val enabled = forceEnabled ?: readEnabledState(packDirectory, ScriptPackKind.DIRECTORY) ?: manifest.enabledByDefault
        val syncId = syncIdOverride ?: makeSyncId(scope, manifest.id)
        val contentFiles = scriptFiles.map {
            ScriptPackContentFile(
                relativePath = it.relativePath,
                absolutePath = it.absolutePath,
                bytes = it.bytes
            )
        }

        return ScriptPack(
            syncId = syncId,
            scope = scope,
            kind = ScriptPackKind.DIRECTORY,
            location = packDirectory,
            manifestJson = manifestJson,
            manifest = manifest,
            enabled = enabled,
            hash = hash,
            scripts = scriptFiles,
            contentFiles = contentFiles,
            compiledJar = null
        ).also {
            LOGGER.info(
                "Discovered source pack {} with {} script files at {}",
                it.manifest.name,
                it.scripts.size,
                it.location
            )
        }
    }

    internal fun scanPackJar(
        jarPath: Path,
        scope: ScriptPackScope,
        syncIdOverride: String? = null,
        forceEnabled: Boolean? = null
    ): ScriptPack? {
        val jarBytes = runCatching { Files.readAllBytes(jarPath) }
            .getOrElse {
                LOGGER.warn("Failed to read script pack jar {}", jarPath, it)
                return null
            }

        val manifestJson = readManifestJsonFromJar(jarPath) ?: createFallbackManifestJson(jarPath)
        val manifest = ScriptPackManifest.parse(jarPath, manifestJson)
        val enabled = forceEnabled ?: readEnabledState(jarPath, ScriptPackKind.JAR) ?: manifest.enabledByDefault
        val syncId = syncIdOverride ?: makeSyncId(scope, manifest.id)
        val contentFile = ScriptPackContentFile(
            relativePath = jarPath.fileName.toString(),
            absolutePath = jarPath,
            bytes = jarBytes
        )

        return ScriptPack(
            syncId = syncId,
            scope = scope,
            kind = ScriptPackKind.JAR,
            location = jarPath,
            manifestJson = manifestJson,
            manifest = manifest,
            enabled = enabled,
            hash = computeJarHash(manifestJson, contentFile),
            scripts = emptyList(),
            contentFiles = listOf(contentFile),
            compiledJar = jarPath
        ).also {
            LOGGER.info("Discovered jar pack {} at {}", it.manifest.name, it.location)
        }
    }

    internal fun collectScriptFiles(packDirectory: Path): List<ScriptPackScriptFile> {
        return runCatching {
            val scripts = mutableListOf<ScriptPackScriptFile>()
            Files.walk(packDirectory).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().endsWith(".kt") }
                    .forEach { file ->
                        val relative = packDirectory.relativize(file).toString().replace('\\', '/')
                        val bytes = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return@forEach
                        scripts.add(
                            ScriptPackScriptFile(
                                relativePath = relative,
                                absolutePath = file,
                                bytes = bytes
                            )
                        )
                    }
            }
            scripts.sortedBy { it.relativePath }
        }.getOrElse {
            LOGGER.warn("Failed to collect scripts from {}", packDirectory, it)
            emptyList()
        }
    }

    internal fun computeScriptHash(manifestJson: String, scripts: List<ScriptPackScriptFile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(manifestJson.toByteArray(StandardCharsets.UTF_8))

        scripts.sortedBy { it.relativePath }.forEach { script ->
            digest.update(script.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(script.bytes)
            digest.update(0)
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun computeJarHash(manifestJson: String, jarFile: ScriptPackContentFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(manifestJson.toByteArray(StandardCharsets.UTF_8))
        digest.update(jarFile.relativePath.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(jarFile.bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readEnabledState(packPath: Path, kind: ScriptPackKind): Boolean? {
        val stateFile = resolveStateFile(packPath, kind)
        if (!Files.isRegularFile(stateFile)) {
            return null
        }

        return runCatching {
            val root = JsonParser.parseString(Files.readString(stateFile, StandardCharsets.UTF_8)).asJsonObject
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

    private fun readManifestJsonFromJar(jarPath: Path): String? {
        return runCatching {
            JarFile(jarPath.toFile()).use { jar ->
                val entry = jar.getJarEntry(MANIFEST_FILE_NAME)
                    ?: jar.getJarEntry("META-INF/katton/$MANIFEST_FILE_NAME")
                    ?: return@use null
                jar.getInputStream(entry).use { input ->
                    input.readAllBytes().toString(StandardCharsets.UTF_8)
                }
            }
        }.getOrNull()
    }

    private fun createFallbackManifestJson(jarPath: Path): String {
        val id = jarPath.fileName.toString().removeSuffix(".jar")
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("name", id)
            addProperty("version", "compiled")
            addProperty("description", "Compiled Katton script pack")
            add("authors", JsonArray())
            addProperty("enabled", true)
        }.toString()
    }

    private fun makeSyncId(scope: ScriptPackScope, id: String): String {
        return "${scope.serializedName}:$id"
    }
}
