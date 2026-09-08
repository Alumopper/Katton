package top.katton.client

import com.mojang.logging.LogUtils
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackCompatibility
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import net.minecraft.server.packs.resources.ReloadableResourceManager
import net.minecraft.util.Unit as MinecraftUnit
import net.minecraft.util.Util
import net.minecraft.world.flag.FeatureFlagSet
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackContentFile
import top.katton.pack.ScriptPackDirectorySnapshots
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackJarSnapshots
import top.katton.pack.ScriptPackScope
import top.katton.util.ReflectUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import java.util.jar.JarFile

object ScriptPackResourceManager {
    private val logger = LogUtils.getLogger()
    private val metadata = Pack.Metadata(
        Component.literal("Katton script pack assets"),
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
    private var activeEntries: List<ResourceEntry> = emptyList()

    @Volatile
    private var activeSignature: String = ""

    @Synchronized
    @Deprecated("Use activateAndReload; activation is transactional")
    fun activateForClient(packs: List<ScriptPack>): Boolean {
        return activateAndReload(packs)
    }

    /**
     * Atomically publishes client asset packs. If the candidate reload fails,
     * the repository state is reset and the previous resources are reloaded.
     */
    @Synchronized
    fun activateAndReload(packs: List<ScriptPack>): Boolean {
        val entries = runCatching { packs.mapIndexedNotNull { index, pack -> createEntry(index, pack) } }
            .onFailure { logger.warn("Failed to materialize candidate Katton script assets", it) }
            .getOrNull() ?: return false
        val nextSignature = signatureOf(entries)
        if (nextSignature == activeSignature) return true

        val previousEntries = activeEntries
        val previousSignature = activeSignature
        activeEntries = entries
        activeSignature = nextSignature
        if (reloadClientResources("publish candidate script assets")) return true

        activeEntries = previousEntries
        activeSignature = previousSignature
        if (!reloadClientResources("restore previous script assets")) {
            logger.error("Failed to restore the previous Katton script assets after a rejected reload")
        }
        return false
    }

    @Synchronized
    fun reloadActiveResources(): Boolean {
        return reloadClientResources("post-script refresh")
    }

    fun hasActiveResources(): Boolean = activeEntries.isNotEmpty()

    @Synchronized
    fun clearServerCacheResources() {
        if (activeEntries.none { it.scope == ScriptPackScope.SERVER_CACHE }) {
            return
        }
        updateActiveEntriesAndReload(activeEntries.filterNot { it.scope == ScriptPackScope.SERVER_CACHE }, "clear server cache")
    }

    @Synchronized
    fun clearAll() {
        if (activeEntries.isEmpty()) {
            return
        }
        updateActiveEntriesAndReload(emptyList(), "clear all")
    }

    private fun updateActiveEntriesAndReload(entries: List<ResourceEntry>, reason: String): Boolean {
        if (!updateActiveEntries(entries)) {
            return false
        }
        return reloadClientResources(reason)
    }

    private fun updateActiveEntries(entries: List<ResourceEntry>): Boolean {
        val nextSignature = signatureOf(entries)
        if (nextSignature == activeSignature) {
            return false
        }

        activeEntries = entries
        activeSignature = nextSignature
        return true
    }

    private fun createEntry(index: Int, pack: ScriptPack): ResourceEntry? {
        val assetFiles = pack.contentFiles.filter { it.relativePath.startsWith("assets/") }
        val (effectiveLocation, assetHash) = when (pack.kind) {
            ScriptPackKind.DIRECTORY, ScriptPackKind.ZIP -> {
                if (assetFiles.isEmpty()) return null
                val hash = hashAssetFiles(assetFiles)
                val snapshot = ScriptPackDirectorySnapshots.materialize(pack, "assets", hash)
                    ?: error("Cannot materialize asset snapshot for ${pack.syncId}")
                snapshot to hash
            }
            ScriptPackKind.JAR -> {
                val snapshot = ScriptPackJarSnapshots.materialize(pack)
                    ?: error("Cannot materialize JAR snapshot for ${pack.syncId}")
                if (!jarHasAssets(snapshot)) return null
                snapshot to pack.hash
            }
        }

        val scopeOrder = when (pack.scope) {
            ScriptPackScope.GLOBAL -> 0
            ScriptPackScope.WORLD -> 1
            ScriptPackScope.SERVER_CACHE -> 2
        }
        val packId = String.format(
            Locale.ROOT,
            "katton_script_assets_%02d_%04d_%s",
            scopeOrder,
            index,
            sanitizePackId(pack.syncId)
        )

        return ResourceEntry(
            packId = packId,
            title = "${pack.manifest.name} assets",
            kind = pack.kind,
            location = effectiveLocation,
            scope = pack.scope,
            hash = assetHash
        )
    }

    private fun createPack(entry: ResourceEntry): Pack? {
        val location = PackLocationInfo(
            entry.packId,
            Component.literal(entry.title),
            packSource(entry.scope),
            Optional.empty()
        )
        val supplier = when (entry.kind) {
            ScriptPackKind.DIRECTORY, ScriptPackKind.ZIP -> PathPackResources.PathResourcesSupplier(entry.location)
            ScriptPackKind.JAR -> FilePackResources.FileResourcesSupplier(entry.location)
        }
        return Pack(
            location,
            supplier,
            metadata,
            PackSelectionConfig(true, Pack.Position.TOP, false)
        )
    }

    private fun reloadClientResources(reason: String): Boolean {
        val mc = Minecraft.getInstance()
        return runCatching {
            if (!installRepositorySource(mc)) {
                return false
            }

            logger.info("Reloading client resources for {} Katton script asset packs ({})", activeEntries.size, reason)
            val reloadFuture = if (mc.isSameThread) {
                startResourceReload(mc)
            } else {
                mc.submit(Supplier { startResourceReload(mc) }).get()
            }
            waitForReload(mc, reloadFuture)
            runAfterReload(mc)
            true
        }.getOrElse {
            logger.warn("Failed to reload Katton script pack resources", it)
            false
        }
    }

    private fun installRepositorySource(mc: Minecraft): Boolean {
        val repository = mc.resourcePackRepository
        if (installedRepository === repository) {
            return true
        }

        val sources = ReflectUtil.getT<Set<RepositorySource>>(repository, "sources")
            .getOrElse {
                logger.warn("Cannot access Minecraft resource pack repository sources", it)
                return false
            } ?: run {
                logger.warn("Minecraft resource pack repository sources are unavailable")
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
                logger.warn("Cannot install Katton script pack resource source", it)
                return false
            }
        installedRepository = repository
        return true
    }

    private fun startResourceReload(mc: Minecraft): CompletableFuture<*> {
        val repository = mc.resourcePackRepository
        val resourceManager = mc.resourceManager as? ReloadableResourceManager
            ?: error("Minecraft resource manager is not reloadable")

        repository.reload()
        val selectedPacks = repository.openAllSelected()
        val reload = resourceManager.createReload(
            Util.backgroundExecutor(),
            mc,
            CompletableFuture.completedFuture(MinecraftUnit.INSTANCE),
            selectedPacks
        )
        return reload.done()
    }

    private fun waitForReload(mc: Minecraft, future: CompletableFuture<*>) {
        if (mc.isSameThread) {
            mc.managedBlock(BooleanSupplier { future.isDone })
            future.join()
        } else {
            future.get()
        }
    }

    private fun runAfterReload(mc: Minecraft) {
        val task = Runnable {
            /*? if mc_26_2 {*/
            mc.levelExtractor.allChanged()
            /*?} else {*/
            /*mc.levelRenderer.allChanged()*/
            /*?}*/
            ClientPostEffectManager.invalidatePostChainCache()
            top.katton.client.scene.ClientSceneManager.resourceReload()
        }
        if (mc.isSameThread) {
            task.run()
        } else {
            mc.submit(task).get()
        }
    }

    private fun jarHasAssets(path: Path): Boolean {
        if (!Files.isRegularFile(path)) {
            error("Materialized JAR asset snapshot is unavailable: $path")
        }
        return runCatching {
            JarFile(path.toFile()).use { jar ->
                jar.entries().asSequence().any { entry ->
                    !entry.isDirectory && entry.name.startsWith("assets/")
                }
            }
        }.getOrElse { failure ->
            throw IllegalStateException("Cannot inspect JAR assets at $path", failure)
        }
    }

    /** Code-only directory edits must not force an expensive resource reload. */
    private fun hashAssetFiles(files: List<ScriptPackContentFile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed("katton-directory-assets-hash-v1".toByteArray(StandardCharsets.UTF_8))
        digest.updateInt(files.size)
        files.sortedBy { it.relativePath }.forEach { file ->
            digest.updateFramed(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.updateFramed(file.bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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

    private fun packSource(scope: ScriptPackScope): PackSource {
        return when (scope) {
            ScriptPackScope.GLOBAL -> PackSource.DEFAULT
            ScriptPackScope.WORLD -> PackSource.WORLD
            ScriptPackScope.SERVER_CACHE -> PackSource.SERVER
        }
    }

    private fun signatureOf(entries: List<ResourceEntry>): String {
        return entries.joinToString(separator = "\u001f") { entry ->
            "${entry.packId}\u001e${entry.kind}\u001e${entry.location}\u001e${entry.hash}"
        }
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

    private data class ResourceEntry(
        val packId: String,
        val title: String,
        val kind: ScriptPackKind,
        val location: Path,
        val scope: ScriptPackScope,
        val hash: String
    )
}
