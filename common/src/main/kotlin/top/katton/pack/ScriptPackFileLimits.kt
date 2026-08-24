package top.katton.pack

import java.io.InputStream
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.text.Normalizer

/**
 * Resource limits shared by local pack scanning and network serialization.
 *
 * Keeping the local limits no larger than the wire limits is important: every
 * pack accepted by the scanner can then be synchronized without failing later
 * in the packet encoder. The limits also keep a malformed local pack or archive
 * from causing an unbounded allocation during reload.
 */
internal object ScriptPackFileLimits {
    private val WINDOWS_RESERVED_NAMES = buildSet {
        addAll(listOf("con", "prn", "aux", "nul"))
        (1..9).forEach { number ->
            add("com$number")
            add("lpt$number")
        }
    }

    const val MAX_FILES_PER_PACK = 4_096
    const val MAX_RELATIVE_PATH_CHARS = 1_024
    const val MAX_MANIFEST_BYTES = 1024 * 1024
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_PACK_CONTENT_BYTES = 64 * 1024 * 1024
    /** Bounds scanning work in the shared `kattonpacks/` directory itself. */
    const val MAX_PACK_ROOT_ENTRIES = 4_096
    /** Includes directories and ignored files so traversal work is bounded too. */
    const val MAX_DIRECTORY_ENTRIES = 16_384
    const val MAX_ARCHIVE_ENTRIES = 16_384
    const val MAX_STATE_BYTES = 64 * 1024
    const val MAX_TRUST_STORE_BYTES = 1024 * 1024
    const val MAX_KEY_FILE_BYTES = 64 * 1024

    /** Paths synchronized by a Linux server must also be materializable on Windows clients. */
    fun isPortableRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || '\\' in path || '\u0000' in path) return false
        if (path.equals("manifest.json", ignoreCase = true)) return false
        return path.split('/').all { segment ->
            if (segment.isEmpty() || segment == "." || segment == "..") return@all false
            if (segment.endsWith('.') || segment.endsWith(' ')) return@all false
            if (segment.any { it.code < 32 || it in "<>:\"|?*" }) return@all false
            segment.substringBefore('.').lowercase(java.util.Locale.ROOT) !in WINDOWS_RESERVED_NAMES
        }
    }

    fun portablePathKey(path: String): String =
        Normalizer.normalize(path, Normalizer.Form.NFC).lowercase(java.util.Locale.ROOT)
}

/** Small, security-sensitive file helpers used by pack and trust-store code. */
internal object SafePackFileIo {
    fun readUtf8(path: Path, maximumBytes: Int, label: String): String =
        String(readBytes(path, maximumBytes, label), StandardCharsets.UTF_8)

    fun readBytes(path: Path, maximumBytes: Int, label: String): ByteArray {
        require(maximumBytes >= 0) { "Maximum byte count must not be negative" }
        val attributes = Files.readAttributes(
            path,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        )
        require(attributes.isRegularFile) { "$label is not a regular file: $path" }
        require(!attributes.isSymbolicLink && !Files.isSymbolicLink(path)) {
            "$label must not be a symbolic link: $path"
        }
        require(attributes.size() <= maximumBytes.toLong()) {
            "$label is too large (${attributes.size()} bytes, maximum $maximumBytes): $path"
        }

        // NOFOLLOW_LINKS closes the gap between checking the attributes and
        // opening the file if a concurrently modified path becomes a symlink.
        val options = setOf<OpenOption>(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        return Files.newByteChannel(path, options).use { channel ->
            Channels.newInputStream(channel).use { input ->
                readBytes(input, maximumBytes, label)
            }
        }
    }

    fun readBytes(input: InputStream, maximumBytes: Int, label: String): ByteArray {
        require(maximumBytes >= 0) { "Maximum byte count must not be negative" }
        val bytes = input.readNBytes(maximumBytes + 1)
        require(bytes.size <= maximumBytes) {
            "$label is too large (maximum $maximumBytes bytes)"
        }
        return bytes
    }

    fun writeUtf8Atomically(path: Path, text: String, maximumBytes: Int, label: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= maximumBytes) {
            "$label is too large (${bytes.size} bytes, maximum $maximumBytes): $path"
        }

        val absolutePath = path.toAbsolutePath().normalize()
        val parent = absolutePath.parent ?: error("$label has no parent directory: $absolutePath")
        Files.createDirectories(parent)
        var temporaryFile: Path? = null
        try {
            val temp = Files.createTempFile(parent, ".${absolutePath.fileName}.", ".tmp")
            temporaryFile = temp
            Files.write(
                temp,
                bytes,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
            try {
                Files.move(
                    temp,
                    absolutePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, absolutePath, StandardCopyOption.REPLACE_EXISTING)
            }
            temporaryFile = null
        } finally {
            temporaryFile?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }
}

/** Tracks the decoded bytes and files retained for one source pack snapshot. */
internal class ScriptPackReadBudget(manifestBytes: Int) {
    private var remainingBytes = ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES - manifestBytes
    private var fileCount = 0

    init {
        require(manifestBytes in 0..ScriptPackFileLimits.MAX_MANIFEST_BYTES) {
            "Manifest is too large ($manifestBytes bytes)"
        }
    }

    fun readContentFile(path: Path, label: String): ByteArray {
        require(fileCount < ScriptPackFileLimits.MAX_FILES_PER_PACK) {
            "Pack contains too many files (maximum ${ScriptPackFileLimits.MAX_FILES_PER_PACK})"
        }
        val maximum = minOf(ScriptPackFileLimits.MAX_FILE_BYTES, remainingBytes)
        require(maximum >= 0) { "Pack content exceeds ${ScriptPackFileLimits.MAX_PACK_CONTENT_BYTES} bytes" }
        val bytes = SafePackFileIo.readBytes(path, maximum, label)
        fileCount++
        remainingBytes -= bytes.size
        return bytes
    }
}
