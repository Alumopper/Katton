package top.katton.sign

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
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
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

abstract class KattonSignPackTask : DefaultTask() {
    private companion object {
        const val PAYLOAD_VERSION = 2
        const val MAX_FILES = 4_096
        const val MAX_DIRECTORY_ENTRIES = 16_384
        const val MAX_RELATIVE_PATH_CHARS = 1_024
        const val MAX_MANIFEST_BYTES = 1024 * 1024
        const val MAX_FILE_BYTES = 16 * 1024 * 1024
        const val MAX_PACK_BYTES = 64 * 1024 * 1024
        const val MAX_KEY_FILE_BYTES = 64 * 1024
        const val MAX_KEY_ID_CHARS = 256
        val PAYLOAD_DOMAIN = "katton-script-pack-signature-v2".toByteArray(StandardCharsets.UTF_8)
        val WINDOWS_RESERVED_NAMES = buildSet {
            addAll(listOf("con", "prn", "aux", "nul"))
            (1..9).forEach { number ->
                add("com$number")
                add("lpt$number")
            }
        }
    }

    // The task performs its own bounded, no-follow scan. Letting Gradle
    // snapshot this directory first would follow its own input rules before
    // Katton gets a chance to reject a hostile symlink or oversized file.
    @get:Internal
    abstract val packDir: DirectoryProperty

    // Private key material must not participate in Gradle input snapshots.
    @get:Internal
    abstract val privateKeyFile: RegularFileProperty

    @get:Internal
    abstract val publicKeyFile: RegularFileProperty

    @get:Input
    abstract val scope: Property<String>

    @get:Input
    abstract val keyId: Property<String>

    @TaskAction
    fun sign() {
        val packPath = packDir.get().asFile.toPath().toAbsolutePath().normalize()
        require(Files.isDirectory(packPath, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(packPath)) {
            "Katton pack directory must be a real directory, not a symbolic link: $packPath"
        }
        val manifestFile = packPath.resolve("manifest.json")
        val manifestBytes = readBoundedFile(manifestFile, MAX_MANIFEST_BYTES, "manifest.json")
        val manifestJson = String(manifestBytes, StandardCharsets.UTF_8)
        val parsedManifest = JsonParser.parseString(manifestJson)
        require(parsedManifest.isJsonObject) { "manifest.json root must be a JSON object: $manifestFile" }
        val manifest = parsedManifest.asJsonObject
        val packId = manifest.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.takeIf { it.isNotBlank() }
            ?: error("manifest.json missing non-empty 'id' field: $manifestFile")
        val scopeValue = scope.get().trim()
        require(scopeValue == "world" || scopeValue == "global") {
            "kattonScope must be 'world' or 'global', but was '$scopeValue'"
        }
        val keyIdValue = keyId.get().trim()
        require(keyIdValue.isNotEmpty() && keyIdValue.length <= MAX_KEY_ID_CHARS) {
            "kattonKeyId must contain 1..$MAX_KEY_ID_CHARS characters"
        }

        val syncId = "$scopeValue:$packId"
        val files = collectFiles(packPath, manifestBytes.size)
        val payload = buildPayload(syncId, scopeValue, manifestJson, files)

        val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(
            PKCS8EncodedKeySpec(readPem(privateKeyFile.get().asFile.toPath(), "PRIVATE KEY"))
        )
        val signatureBytes = Signature.getInstance("Ed25519").apply {
            initSign(privateKey)
            update(payload)
        }.sign()
        val publicKeyBytes = readPem(publicKeyFile.get().asFile.toPath(), "PUBLIC KEY")
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        require(Signature.getInstance("Ed25519").apply {
            initVerify(publicKey)
            update(payload)
        }.verify(signatureBytes)) {
            "The configured Ed25519 public key does not match the private key"
        }

        manifest.remove("signature")
        manifest.add("signature", JsonObject().apply {
            addProperty("algorithm", "Ed25519")
            addProperty("payloadVersion", PAYLOAD_VERSION)
            addProperty("keyId", keyIdValue)
            addProperty("publicKey", Base64.getEncoder().encodeToString(publicKeyBytes))
            addProperty("signature", Base64.getEncoder().encodeToString(signatureBytes))
        })
        val signedManifestJson = manifest.toString()
        val signedManifestSize = signedManifestJson.toByteArray(StandardCharsets.UTF_8).size
        val synchronizedFileBytes = files.sumOf { it.bytes.size.toLong() }
        require(signedManifestSize.toLong() + synchronizedFileBytes <= MAX_PACK_BYTES) {
            "Signed manifest and synchronized files exceed the $MAX_PACK_BYTES-byte pack limit"
        }
        writeManifestAtomically(manifestFile, signedManifestJson)

        logger.lifecycle("Signed Katton pack {}", packId)
        logger.lifecycle("  Scope:       {}", scopeValue)
        logger.lifecycle("  Sync ID:     {}", syncId)
        logger.lifecycle("  Key ID:      {}", keyIdValue)
        logger.lifecycle("  Fingerprint: {}", fingerprint(publicKeyBytes))
        logger.lifecycle("  Files:       {}", files.size)
        logger.lifecycle("  Manifest:    {}", manifestFile)
    }

    /**
     * Mirrors ScriptPackManager's synchronization policy exactly: executable
     * Kotlin/Java sources outside content roots, plus every file below
     * `assets/` and `data/`. Signing fewer files would make valid resource or
     * data packs fail verification; signing more would cover bytes never sent.
     */
    private fun collectFiles(packPath: Path, manifestBytes: Int): List<PackFile> {
        val realRoot = packPath.toRealPath()
        var remainingBytes = MAX_PACK_BYTES - manifestBytes
        val files = mutableListOf<PackFile>()
        val portablePaths = HashSet<String>()
        var discoveredEntries = 0
        Files.walk(packPath).use { stream ->
            stream.forEach { candidate ->
                if (candidate == packPath) return@forEach
                discoveredEntries++
                require(discoveredEntries <= MAX_DIRECTORY_ENTRIES) {
                    "Pack contains more than $MAX_DIRECTORY_ENTRIES directory entries"
                }
                require(!Files.isSymbolicLink(candidate)) {
                    "Symbolic links are not allowed in signed Katton packs: $candidate"
                }
                if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) return@forEach
                require(candidate.toRealPath().startsWith(realRoot)) { "Pack file escapes its root: $candidate" }

                val relative = normalizedRelativePath(packPath, candidate)
                val inContentRoot = relative.startsWith("assets/") || relative.startsWith("data/")
                val isSource = relative.endsWith(".kt", ignoreCase = true) ||
                    relative.endsWith(".java", ignoreCase = true)
                if (!inContentRoot && !isSource) return@forEach
                require(isPortableRelativePath(relative)) {
                    "Pack file path is not portable across clients: $relative"
                }
                val portableKey = Normalizer.normalize(relative, Normalizer.Form.NFC)
                    .lowercase(java.util.Locale.ROOT)
                require(portablePaths.add(portableKey)) {
                    "Pack contains paths that collide on a case-insensitive client: $relative"
                }

                require(files.size < MAX_FILES) { "Pack contains more than $MAX_FILES synchronized files" }
                val maximum = minOf(MAX_FILE_BYTES, remainingBytes)
                require(maximum >= 0) { "Pack content exceeds $MAX_PACK_BYTES bytes" }
                val bytes = readBoundedFile(candidate, maximum, "pack file '$relative'")
                remainingBytes -= bytes.size
                files += PackFile(relative, bytes)
            }
        }
        return files.sortedBy { it.relativePath }
    }

    private fun buildPayload(syncId: String, scope: String, manifestJson: String, files: List<PackFile>): ByteArray {
        val root = JsonParser.parseString(manifestJson).asJsonObject
        root.remove("signature")
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed(PAYLOAD_DOMAIN)
        digest.updateFramed(syncId.toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(scope.toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(root.toString().toByteArray(StandardCharsets.UTF_8))
        digest.updateInt(files.size)
        files.forEach { file ->
            digest.updateFramed(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.updateFramed(file.bytes)
        }
        return digest.digest()
    }

    private fun readPem(path: Path, label: String): ByteArray {
        val text = String(readBoundedFile(path, MAX_KEY_FILE_BYTES, "$label PEM"), StandardCharsets.UTF_8)
        val normalized = text
            .replace("-----BEGIN $label-----", "")
            .replace("-----END $label-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
        require(normalized.isNotEmpty()) { "$label PEM file contains no key data: $path" }
        return runCatching { Base64.getDecoder().decode(normalized) }
            .getOrElse { throw IllegalArgumentException("Invalid $label PEM file: $path", it) }
    }

    private fun readBoundedFile(path: Path, maximumBytes: Int, label: String): ByteArray {
        require(maximumBytes >= 0) { "$label exceeds the remaining pack byte budget" }
        val attributes = Files.readAttributes(
            path,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        )
        require(attributes.isRegularFile && !attributes.isSymbolicLink && !Files.isSymbolicLink(path)) {
            "$label must be a regular file, not a symbolic link: $path"
        }
        require(attributes.size() <= maximumBytes.toLong()) {
            "$label is too large (${attributes.size()} bytes, maximum $maximumBytes): $path"
        }
        val options = setOf<OpenOption>(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        return Files.newByteChannel(path, options).use { channel ->
            Channels.newInputStream(channel).use { input ->
                val bytes = input.readNBytes(maximumBytes + 1)
                require(bytes.size <= maximumBytes) { "$label grew beyond $maximumBytes bytes while reading: $path" }
                bytes
            }
        }
    }

    private fun writeManifestAtomically(manifestFile: Path, manifestJson: String) {
        val bytes = manifestJson.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_MANIFEST_BYTES) {
            "Signed manifest is too large (${bytes.size} bytes, maximum $MAX_MANIFEST_BYTES)"
        }
        var temporaryFile: Path? = null
        try {
            val temp = Files.createTempFile(manifestFile.parent, ".manifest.", ".json.tmp")
            temporaryFile = temp
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            try {
                Files.move(
                    temp,
                    manifestFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, manifestFile, StandardCopyOption.REPLACE_EXISTING)
            }
            temporaryFile = null
        } finally {
            temporaryFile?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun normalizedRelativePath(root: Path, file: Path): String {
        val relative = root.relativize(file).normalize()
        require(!relative.isAbsolute && relative.none { it.toString() == ".." }) {
            "Pack file has an unsafe relative path: $file"
        }
        val value = relative.toString().replace('\\', '/')
        require(value.isNotEmpty() && value.length <= MAX_RELATIVE_PATH_CHARS) {
            "Pack file path is empty or longer than $MAX_RELATIVE_PATH_CHARS characters: $file"
        }
        return value
    }

    private fun isPortableRelativePath(path: String): Boolean {
        if (path.startsWith('/') || '\\' in path || '\u0000' in path) return false
        if (path.equals("manifest.json", ignoreCase = true)) return false
        return path.split('/').all { segment ->
            if (segment.isEmpty() || segment == "." || segment == "..") return@all false
            if (segment.endsWith('.') || segment.endsWith(' ')) return@all false
            if (segment.any { it.code < 32 || it in "<>:\"|?*" }) return@all false
            segment.substringBefore('.').lowercase(java.util.Locale.ROOT) !in WINDOWS_RESERVED_NAMES
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

    private fun fingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02x".format(it) }

    private data class PackFile(val relativePath: String, val bytes: ByteArray)
}
