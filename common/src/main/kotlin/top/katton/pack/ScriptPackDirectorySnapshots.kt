package top.katton.pack

import top.katton.Katton
import top.katton.api.LOGGER
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap

/** Immutable `assets/` or `data/` views for directory packs. */
internal object ScriptPackDirectorySnapshots {
    private const val SNAPSHOT_DIRECTORY_NAME = "directory-snapshots"
    private val CONTENT_HASH_PATTERN = Regex("[0-9a-f]{64}")
    private val materializationLock = Any()
    private data class SnapshotKey(val root: Path, val contentRoot: String, val hash: String)

    private val snapshots = ConcurrentHashMap<SnapshotKey, Path>()
    private val initializedRoots = HashSet<Path>()

    fun materialize(pack: ScriptPack, contentRoot: String, contentHash: String): Path? {
        require(pack.kind != ScriptPackKind.JAR) { "Only directory packs use directory snapshots" }
        require(contentRoot == "assets" || contentRoot == "data") { "Unsupported content root '$contentRoot'" }
        require(CONTENT_HASH_PATTERN.matches(contentHash)) { "Invalid content snapshot hash" }
        val files = pack.contentFiles.filter { it.relativePath.startsWith("$contentRoot/") }
        if (files.isEmpty()) return null

        val root = prepareRoot()
        val key = SnapshotKey(root, contentRoot, contentHash)
        snapshots[key]?.takeIf { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }?.let { return it }
        return synchronized(materializationLock) {
            snapshots[key]?.takeIf { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }
                ?.let { return@synchronized it }
            runCatching {
                initializeRoot(root)
                val target = root.resolve("$contentRoot-$contentHash")
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) deleteTree(target, root)

                val staging = Files.createTempDirectory(root, ".$contentRoot-")
                try {
                    files.forEach { file ->
                        val relative = Path.of(file.relativePath)
                        require(!relative.isAbsolute) { "Snapshot path must be relative: ${file.relativePath}" }
                        val output = staging.resolve(relative).normalize()
                        require(output.startsWith(staging) && output != staging) {
                            "Snapshot path escapes its root: ${file.relativePath}"
                        }
                        Files.createDirectories(output.parent)
                        Files.write(output, file.bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                    }
                    try {
                        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(staging, target)
                    }
                } catch (failure: Throwable) {
                    runCatching { deleteTree(staging, root) }
                    throw failure
                }
                target.also { snapshots[key] = it }
            }.getOrElse { failure ->
                LOGGER.warn(
                    "Failed to materialize immutable {} snapshot for {}",
                    contentRoot,
                    pack.syncId,
                    failure
                )
                null
            }
        }
    }

    private fun prepareRoot(): Path {
        val base = Katton.gameDirectory?.resolve(".katton")
            ?: Path.of(System.getProperty("java.io.tmpdir"), "katton")
        val root = base.resolve(SNAPSHOT_DIRECTORY_NAME).toAbsolutePath().normalize()
        Files.createDirectories(root)
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
            "Directory snapshot path is not a safe directory: $root"
        }
        return root
    }

    /** Remove only this dedicated cache's children, before any are exposed in this process. */
    private fun initializeRoot(root: Path) {
        if (!initializedRoots.add(root)) return
        Files.newDirectoryStream(root).use { entries ->
            entries.forEach { entry -> deleteTree(entry, root) }
        }
        snapshots.entries.removeIf { (key, _) -> key.root == root }
    }

    private fun deleteTree(path: Path, root: Path) {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.startsWith(root) && normalized != root) {
            "Refusing to delete path outside the directory snapshot cache: $normalized"
        }
        if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(normalized)
            return
        }
        Files.walk(normalized).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { candidate ->
                val safe = candidate.toAbsolutePath().normalize()
                require(safe.startsWith(root) && safe != root) { "Snapshot cleanup escaped its cache root" }
                Files.deleteIfExists(candidate)
            }
        }
    }
}
