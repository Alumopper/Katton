package top.katton.datapack

import com.mojang.logging.LogUtils
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackCompatibility
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.level.DataPackConfig
import net.minecraft.world.level.WorldDataConfiguration
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackContentFile
import top.katton.pack.ScriptPackDirectorySnapshots
import top.katton.pack.ScriptPackFileLimits
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackJarSnapshots
import top.katton.pack.ScriptPackScope
import top.katton.util.ReflectUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier
import java.util.jar.JarFile

object ScriptPackDataManager {
    private const val PACK_ID_PREFIX = "katton_script_data_"
    private val logger = LogUtils.getLogger()
    private val metadata = Pack.Metadata(
        Component.literal("Katton script pack data"),
        PackCompatibility.COMPATIBLE,
        FeatureFlagSet.of(),
        emptyList()
    )
    private val repositorySource = RepositorySource { output ->
        activeEntries.forEach { entry ->
            createPack(entry)?.let(output::accept)
        }
    }

    @Volatile
    private var installedRepository: PackRepository? = null

    @Volatile
    private var activeEntries: List<DataEntry> = emptyList()

    @Volatile
    private var activeSignature: String = ""

    @Synchronized
    fun activateForServer(server: MinecraftServer, packs: List<ScriptPack>): Boolean {
        val repository = server.packRepository
        val repositoryChanged = installedRepository !== repository
        val previousSignature = activeSignature
        if (!installRepositorySource(repository)) {
            return false
        }

        val entries = packs.mapIndexedNotNull { index, pack -> createEntry(index, pack) }
        val nextSignature = signatureOf(entries)
        val shouldReload = when {
            repositoryChanged && nextSignature.isEmpty() -> false
            repositoryChanged -> true
            else -> nextSignature != previousSignature
        }

        activeEntries = entries
        activeSignature = nextSignature
        return shouldReload
    }

    fun reloadServerResources(server: MinecraftServer): Boolean {
        if (server.isSameThread) {
            return reloadServerResourcesOnServerThread(server)
        }

        val result = CompletableFuture<Boolean>()
        server.execute {
            result.complete(reloadServerResourcesOnServerThread(server))
        }
        return result.get()
    }

    private fun createEntry(index: Int, pack: ScriptPack): DataEntry? {
        val jarLocation = if (pack.kind == ScriptPackKind.JAR) {
            ScriptPackJarSnapshots.materialize(pack) ?: return null
        } else null
        val dataHash = packDataHash(pack, jarLocation) ?: return null
        val effectiveLocation = when (pack.kind) {
            ScriptPackKind.JAR -> jarLocation ?: return null
            ScriptPackKind.DIRECTORY ->
                ScriptPackDirectorySnapshots.materialize(pack, "data", dataHash) ?: return null
        }
        val scopeOrder = when (pack.scope) {
            ScriptPackScope.GLOBAL -> 0
            ScriptPackScope.WORLD -> 1
            ScriptPackScope.SERVER_CACHE -> 2
        }
        val packId = String.format(
            Locale.ROOT,
            "${PACK_ID_PREFIX}%02d_%04d_%s",
            scopeOrder,
            index,
            sanitizePackId(pack.syncId)
        )

        return DataEntry(
            packId = packId,
            title = "${pack.manifest.name} data",
            kind = pack.kind,
            location = effectiveLocation,
            scope = pack.scope,
            dataHash = dataHash
        )
    }

    private fun createPack(entry: DataEntry): Pack? {
        val location = PackLocationInfo(
            entry.packId,
            Component.literal(entry.title),
            packSource(entry.scope),
            Optional.empty()
        )
        val supplier = when (entry.kind) {
            ScriptPackKind.DIRECTORY -> PathPackResources.PathResourcesSupplier(entry.location)
            ScriptPackKind.JAR -> FilePackResources.FileResourcesSupplier(entry.location)
        }
        return Pack(
            location,
            supplier,
            metadata,
            PackSelectionConfig(true, Pack.Position.TOP, false)
        )
    }

    private fun reloadServerResourcesOnServerThread(server: MinecraftServer): Boolean {
        return runCatching {
            val repository = server.packRepository
            if (!installRepositorySource(repository)) {
                return false
            }

            repository.reload()
            val requestedIds = LinkedHashSet<String>().apply {
                addAll(repository.selectedIds)
                addAll(activeEntries.map { it.packId })
            }
            repository.setSelected(requestedIds)
            val selectedIds = repository.selectedIds.toList()

            logger.info("Reloading server data resources for {} Katton script data packs", activeEntries.size)
            val reloadFuture = server.reloadResources(selectedIds)
            waitForReload(server, reloadFuture)
            restoreWorldDataConfiguration(server)
            true
        }.getOrElse {
            logger.warn("Failed to reload Katton script pack data resources", it)
            false
        }
    }

    private fun restoreWorldDataConfiguration(server: MinecraftServer) {
        val currentConfiguration = server.worldData.dataConfiguration
        val currentPacks = currentConfiguration.dataPacks()
        val enabled = currentPacks.getEnabled().filterNot(::isKattonDataPackId)
        val disabled = currentPacks.getDisabled().filterNot(::isKattonDataPackId)
        if (enabled.size == currentPacks.getEnabled().size && disabled.size == currentPacks.getDisabled().size) {
            return
        }

        server.worldData.setDataConfiguration(
            WorldDataConfiguration(
                DataPackConfig(enabled, disabled),
                currentConfiguration.enabledFeatures()
            )
        )
    }

    private fun waitForReload(server: MinecraftServer, future: CompletableFuture<Void>) {
        if (server.isSameThread) {
            server.managedBlock(BooleanSupplier { future.isDone })
            future.join()
        } else {
            future.get()
        }
    }

    private fun installRepositorySource(repository: PackRepository): Boolean {
        if (installedRepository === repository) {
            return true
        }

        val sources = ReflectUtil.getT<Set<RepositorySource>>(repository, "sources")
            .getOrElse {
                logger.warn("Cannot access server data pack repository sources", it)
                return false
            } ?: run {
                logger.warn("Server data pack repository sources are unavailable")
                return false
            }
        if (sources.any { it === repositorySource }) {
            installedRepository = repository
            return true
        }

        val updatedSources = LinkedHashSet<RepositorySource>().apply {
            addAll(sources)
            add(repositorySource)
        }
        ReflectUtil.setFinal(repository, "sources", updatedSources)
            .onFailure {
                logger.warn("Cannot install Katton script pack data source", it)
                return false
            }
        installedRepository = repository
        return true
    }

    private fun packDataHash(pack: ScriptPack, jarLocation: Path?): String? {
        val dataFiles = pack.contentFiles.filter { it.relativePath.startsWith("data/") }
        if (dataFiles.isNotEmpty()) {
            return hashContentFiles(dataFiles)
        }
        return if (pack.kind == ScriptPackKind.JAR) {
            jarLocation?.let(::hashJarDataEntries)
        } else {
            null
        }
    }

    private fun hashContentFiles(files: List<ScriptPackContentFile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed("katton-directory-data-hash-v2".toByteArray(StandardCharsets.UTF_8))
        digest.updateInt(files.size)
        files.sortedBy { it.relativePath }.forEach { file ->
            digest.updateFramed(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.updateFramed(file.bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hashJarDataEntries(path: Path): String? {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            return null
        }

        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            var found = false
            var archiveEntryCount = 0
            var expandedBytes = 0L
            JarFile(path.toFile()).use { jar ->
                val dataEntries = buildList {
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        archiveEntryCount++
                        require(archiveEntryCount <= ScriptPackFileLimits.MAX_ARCHIVE_ENTRIES) {
                            "Archive contains too many entries"
                        }
                        if (!entry.isDirectory && entry.name.startsWith("data/")) add(entry)
                    }
                }.sortedBy { it.name }
                digest.updateFramed("katton-jar-data-hash-v2".toByteArray(StandardCharsets.UTF_8))
                digest.updateInt(dataEntries.size)
                dataEntries.forEach { entry ->
                    found = true
                    digest.updateFramed(entry.name.toByteArray(StandardCharsets.UTF_8))
                    val entryDigest = MessageDigest.getInstance("SHA-256")
                    var entryBytes = 0
                    jar.getInputStream(entry).use { input ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            expandedBytes += read
                            require(entryBytes <= ScriptPackFileLimits.MAX_FILE_BYTES) {
                                "Archive data entry '${entry.name}' is too large"
                            }
                            require(expandedBytes <= ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES) {
                                "Archive data entries exceed the pack byte budget"
                            }
                            entryDigest.update(buffer, 0, read)
                        }
                    }
                    require(entry.size < 0L || entry.size == entryBytes.toLong()) {
                        "Archive data entry '${entry.name}' changed size while reading"
                    }
                    digest.updateInt(entryBytes)
                    digest.updateFramed(entryDigest.digest())
                }
            }
            if (found) digest.digest().joinToString("") { "%02x".format(it) } else null
        }.getOrElse {
            logger.warn("Failed to hash safe data entries from Katton jar {}", path, it)
            null
        }
    }

    private fun packSource(scope: ScriptPackScope): PackSource {
        return when (scope) {
            ScriptPackScope.GLOBAL -> PackSource.DEFAULT
            ScriptPackScope.WORLD -> PackSource.WORLD
            ScriptPackScope.SERVER_CACHE -> PackSource.SERVER
        }
    }

    private fun signatureOf(entries: List<DataEntry>): String {
        return entries.joinToString(separator = "\u001f") { entry ->
            "${entry.packId}\u001e${entry.kind}\u001e${entry.location}\u001e${entry.dataHash}"
        }
    }

    private fun isKattonDataPackId(id: String): Boolean {
        return id.startsWith(PACK_ID_PREFIX)
    }

    private fun sanitizePackId(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                append(
                    if (char.isLetterOrDigit() || char == '_' || char == '-' || char == '.') {
                        char
                    } else {
                        '_'
                    }
                )
            }
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

    private data class DataEntry(
        val packId: String,
        val title: String,
        val kind: ScriptPackKind,
        val location: Path,
        val scope: ScriptPackScope,
        val dataHash: String
    )
}
