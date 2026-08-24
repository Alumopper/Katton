package top.katton.pack

import top.katton.Katton
import top.katton.api.LOGGER
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

/**
 * Materializes the bounded JAR bytes captured by [ScriptPackManager].
 *
 * Compilers and Minecraft resource-pack suppliers require a real file path.
 * Pointing them back at the original pack would reopen a mutable file after its
 * hash/signature check. Content-addressed private snapshots keep every consumer
 * on exactly the bytes that were accepted by the scanner.
 */
internal object ScriptPackJarSnapshots {
    private const val SNAPSHOT_DIRECTORY_NAME = "jar-snapshots"
    private val PACK_HASH_PATTERN = Regex("[0-9a-f]{64}")
    private val materializationLock = Any()
    private data class SnapshotKey(val root: Path, val digest: String)

    private val pathsByDigest = ConcurrentHashMap<SnapshotKey, Path>()
    private val initializedRoots = HashSet<Path>()

    fun materialize(pack: ScriptPack): Path? = materializeInto(pack, snapshotRoot())

    /** Test seam and reusable implementation for an explicitly trusted cache root. */
    internal fun materializeInto(pack: ScriptPack, requestedRoot: Path): Path? {
        if (pack.kind != ScriptPackKind.JAR) return null
        val jarContent = pack.contentFiles.singleOrNull() ?: run {
            LOGGER.warn("Cannot materialize JAR pack {} because its byte snapshot is incomplete", pack.syncId)
            return null
        }
        val bytes = jarContent.bytes
        // The scanner has already hashed the manifest, path, and these exact
        // bounded bytes. Reusing that content identity avoids hashing a large
        // JAR again for every compiler/resource/data consumer.
        val digest = pack.hash
        require(PACK_HASH_PATTERN.matches(digest)) { "Invalid JAR pack hash" }
        val root = prepareRoot(requestedRoot)
        val key = SnapshotKey(root, digest)
        pathsByDigest[key]?.takeIf(::isSafeRegularFile)?.let { return it }

        return synchronized(materializationLock) {
            pathsByDigest[key]?.takeIf(::isSafeRegularFile)?.let { return@synchronized it }
            runCatching {
                initializeRoot(root)
                val target = root.resolve("snapshot-$digest.jar")
                if (!matchesSnapshot(target, bytes)) writeAtomically(target, bytes)
                require(matchesSnapshot(target, bytes)) { "Materialized JAR snapshot failed verification: $target" }
                target.also { pathsByDigest[key] = it }
            }.getOrElse { failure ->
                LOGGER.warn("Failed to materialize immutable JAR snapshot for {}", pack.syncId, failure)
                null
            }
        }
    }

    private fun snapshotRoot(): Path {
        val base = Katton.gameDirectory?.resolve(".katton")
            ?: Path.of(System.getProperty("java.io.tmpdir"), "katton")
        return base.resolve(SNAPSHOT_DIRECTORY_NAME)
    }

    private fun prepareRoot(requestedRoot: Path): Path {
        val root = requestedRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
            "JAR snapshot path is not a safe directory: $root"
        }
        return root
    }

    /** A root is cleaned only before this process can publish a path from it. */
    private fun initializeRoot(root: Path) {
        if (!initializedRoots.add(root)) return
        Files.newDirectoryStream(root, "snapshot-*.jar").use { entries ->
            entries.forEach { entry ->
                if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(entry)) {
                    Files.deleteIfExists(entry)
                }
            }
        }
        pathsByDigest.entries.removeIf { (key, _) -> key.root == root }
    }

    private fun matchesSnapshot(path: Path, expected: ByteArray): Boolean {
        if (!isSafeRegularFile(path)) return false
        return runCatching {
            SafePackFileIo.readBytes(path, ScriptPackFileLimits.MAX_FILE_BYTES, "JAR snapshot")
                .contentEquals(expected)
        }.getOrDefault(false)
    }

    private fun isSafeRegularFile(path: Path): Boolean =
        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        require(bytes.size <= ScriptPackFileLimits.MAX_FILE_BYTES) { "JAR snapshot exceeds the file byte limit" }
        var temporary: Path? = null
        try {
            val temp = Files.createTempFile(target.parent, ".katton-jar-", ".tmp")
            temporary = temp
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            JarFile(temp.toFile()).use { jar -> require(jar.size() >= 0) { "Invalid JAR snapshot" } }
            try {
                Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            temporary = null
        } finally {
            temporary?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }
}
