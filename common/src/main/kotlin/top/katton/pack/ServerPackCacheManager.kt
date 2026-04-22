package top.katton.pack

import net.minecraft.client.Minecraft
import top.katton.Katton
import top.katton.api.LOGGER
import top.katton.network.ScriptPackBundlePacket
import top.katton.network.ScriptPackHashListPacket
import top.katton.network.ScriptPackRequestPacket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Comparator
import kotlin.io.path.absolutePathString

object ServerPackCacheManager {

    private const val SERVER_PACKS_DIR_NAME = "serverpacks"
    private const val MANIFEST_FILE_NAME = "manifest.json"

    @Volatile
    private var activeServerBucket: String? = null

    @Volatile
    private var expectedHashes: Map<String, String> = emptyMap()

    @Volatile
    private var activePacks: List<ScriptPack> = emptyList()

    @Volatile
    private var pendingClientReload: Boolean = false

    @Synchronized
    fun reset() {
        activeServerBucket = null
        expectedHashes = emptyMap()
        activePacks = emptyList()
        pendingClientReload = false
    }

    fun listPacksForGui(): List<ScriptPackView> {
        return activePacks
            .sortedWith(compareBy<ScriptPack> { it.manifest.name.lowercase() }.thenBy { it.manifest.id.lowercase() })
            .map { pack ->
                ScriptPackView(
                    syncId = pack.syncId,
                    scope = ScriptPackScope.SERVER_CACHE,
                    id = pack.manifest.id,
                    name = pack.manifest.name,
                    version = pack.manifest.version,
                    description = pack.manifest.description,
                    authors = pack.manifest.authors,
                    hash = pack.hash,
                    enabled = true,
                    locked = true,
                    sourcePath = pack.directory.absolutePathString()
                )
            }
    }

    fun collectClientScripts(): List<String> {
        return activePacks
            .asSequence()
            .flatMap { pack -> pack.scripts.asSequence().map { it.absolutePath.absolutePathString() } }
            .distinct()
            .toList()
    }

    @Synchronized
    fun handleHashList(packet: ScriptPackHashListPacket, requestSender: (ScriptPackRequestPacket) -> Unit) {
        val bucket = resolveCurrentServerBucket() ?: run {
            LOGGER.warn("Cannot resolve current server bucket, skipping script pack hash sync")
            return
        }

        activeServerBucket = bucket
        expectedHashes = packet.entries.associate { it.syncId to it.hash }

        if (packet.entries.isEmpty()) {
            activePacks = emptyList()
            pendingClientReload = true
            return
        }

        val cachedRoot = resolveCachedRoot(bucket)
        val resolved = mutableListOf<ScriptPack>()
        val missing = mutableListOf<String>()

        packet.entries.forEach { entry ->
            val cached = loadCachedPack(cachedRoot, entry.syncId)
            if (cached == null || cached.hash != entry.hash) {
                missing += entry.syncId
            } else {
                resolved += cached
            }
        }

        if (missing.isEmpty()) {
            activePacks = resolved
            pendingClientReload = true
            return
        }

        activePacks = emptyList()
        pendingClientReload = false
        requestSender(ScriptPackRequestPacket(missing))
    }

    @Synchronized
    fun handleBundle(packet: ScriptPackBundlePacket) {
        val bucket = activeServerBucket ?: resolveCurrentServerBucket() ?: return
        val cachedRoot = resolveCachedRoot(bucket)

        packet.packs.forEach { packData ->
            persistPackBundle(cachedRoot, packData)
        }

        val resolved = mutableListOf<ScriptPack>()
        val unresolved = mutableListOf<String>()

        expectedHashes.forEach { (syncId, expectedHash) ->
            val cached = loadCachedPack(cachedRoot, syncId)
            if (cached == null || cached.hash != expectedHash) {
                unresolved += syncId
            } else {
                resolved += cached
            }
        }

        activePacks = resolved
        pendingClientReload = unresolved.isEmpty()

        if (unresolved.isNotEmpty()) {
            LOGGER.warn("Some server packs are still unresolved after bundle sync: {}", unresolved)
        }
    }

    @Synchronized
    fun executePendingScriptsBeforeRegistryCheck() {
        if (!pendingClientReload) {
            return
        }
        pendingClientReload = false
        Katton.reloadClientScripts()
    }

    private fun loadCachedPack(cachedRoot: Path, syncId: String): ScriptPack? {
        val packDirectory = cachedRoot.resolve(encodeSyncId(syncId))
        if (!Files.isDirectory(packDirectory)) {
            return null
        }

        return ScriptPackManager.scanPackDirectory(
            packDirectory = packDirectory,
            scope = ScriptPackScope.SERVER_CACHE,
            syncIdOverride = syncId,
            forceEnabled = true
        )
    }

    private fun persistPackBundle(cachedRoot: Path, packData: ScriptPackBundlePacket.PackData) {
        val packDirectory = cachedRoot.resolve(encodeSyncId(packData.syncId))

        runCatching {
            deleteDirectory(packDirectory)
            Files.createDirectories(packDirectory)
            Files.writeString(
                packDirectory.resolve(MANIFEST_FILE_NAME),
                packData.manifestJson,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            packData.files.forEach { fileData ->
                val output = packDirectory.resolve(fileData.relativePath).normalize()
                if (!output.startsWith(packDirectory)) {
                    return@forEach
                }
                output.parent?.let(Files::createDirectories)
                Files.write(
                    output,
                    fileData.content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
            }
        }.onFailure {
            LOGGER.warn("Failed to persist server pack {}", packData.syncId, it)
        }
    }

    private fun resolveCachedRoot(bucket: String): Path {
        val gameDir = Katton.gameDirectory ?: error("Game directory is not initialized")
        val root = gameDir.resolve(SERVER_PACKS_DIR_NAME).resolve(bucket)
        Files.createDirectories(root)
        return root
    }

    private fun resolveCurrentServerBucket(): String? {
        val mc = Minecraft.getInstance()
        val address = mc.currentServer?.ip?.trim()?.lowercase()
            ?: "singleplayer"
        if (address.isBlank()) {
            return null
        }
        return sha256(address)
    }

    private fun encodeSyncId(syncId: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(syncId.toByteArray(StandardCharsets.UTF_8))
    }

    private fun deleteDirectory(path: Path) {
        if (!Files.exists(path)) {
            return
        }

        Files.walk(path).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(text.toByteArray(StandardCharsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
