package top.katton.pack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.katton.Katton
import top.katton.api.LOGGER
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant

object RemoteScriptTrustStore {

    private const val TRUST_FILE_NAME = "remote-script-trust.json"

    private data class TrustFileStamp(
        val size: Long,
        val modified: FileTime,
        val fileKey: Any?,
        val regularFile: Boolean,
        val symbolicLink: Boolean
    )

    private var cacheInitialized = false
    private var cachedFile: Path? = null
    private var cachedStamp: TrustFileStamp? = null
    private var cachedRoot = JsonObject()

    @Synchronized
    fun isTrusted(serverBucket: String): Boolean {
        return readTrustedServers().has(serverBucket)
    }

    @Synchronized
    fun trust(serverBucket: String, serverAddress: String) {
        val root = readRoot().deepCopy()
        val servers = root.getAsJsonObject("trustedServers") ?: JsonObject().also {
            root.add("trustedServers", it)
        }

        servers.add(serverBucket, JsonObject().apply {
            addProperty("serverAddress", serverAddress)
            addProperty("trustedAt", Instant.now().toString())
        })

        writeRoot(root)
    }

    @Synchronized
    fun trustedPublicKey(keyId: String): String? {
        return readTrustedKeys()
            .getAsJsonObject(keyId)
            ?.get("publicKey")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
    }

    @Synchronized
    fun trustPublicKey(keyId: String, publicKey: String, serverAddress: String, fingerprint: String) {
        val root = readRoot().deepCopy()
        val keys = root.getAsJsonObject("trustedKeys") ?: JsonObject().also {
            root.add("trustedKeys", it)
        }

        keys.add(keyId, JsonObject().apply {
            addProperty("publicKey", publicKey)
            addProperty("fingerprint", fingerprint)
            addProperty("serverAddress", serverAddress)
            addProperty("trustedAt", Instant.now().toString())
        })

        writeRoot(root)
    }

    private fun readTrustedServers(): JsonObject {
        return readRoot().getAsJsonObject("trustedServers") ?: JsonObject()
    }

    private fun readTrustedKeys(): JsonObject {
        return readRoot().getAsJsonObject("trustedKeys") ?: JsonObject()
    }

    private fun readRoot(): JsonObject {
        val file = trustFile()?.toAbsolutePath()?.normalize() ?: return JsonObject()
        val attributes = runCatching {
            Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrNull()
        val stamp = attributes?.let {
            TrustFileStamp(it.size(), it.lastModifiedTime(), it.fileKey(), it.isRegularFile, it.isSymbolicLink)
        }
        if (cacheInitialized && cachedFile == file && cachedStamp == stamp) return cachedRoot

        if (attributes == null) {
            updateCache(file, null, JsonObject())
            return cachedRoot
        }
        if (!attributes.isRegularFile || attributes.isSymbolicLink || Files.isSymbolicLink(file)) {
            LOGGER.warn("Ignoring unsafe Katton remote script trust store path {}", file)
            updateCache(file, stamp, JsonObject())
            return cachedRoot
        }

        val parsedRoot = runCatching {
            val json = SafePackFileIo.readUtf8(
                file,
                ScriptPackFileLimits.MAX_TRUST_STORE_BYTES,
                "remote script trust store"
            )
            val parsed = JsonParser.parseString(json)
            require(parsed.isJsonObject) { "trust store root must be a JSON object" }
            parsed.asJsonObject
        }.getOrElse {
            LOGGER.warn("Failed to read Katton remote script trust store {}", file, it)
            JsonObject()
        }
        updateCache(file, stamp, parsedRoot)
        return cachedRoot
    }

    private fun writeRoot(root: JsonObject) {
        val file = trustFile() ?: return
        runCatching {
            SafePackFileIo.writeUtf8Atomically(
                file,
                root.toString(),
                ScriptPackFileLimits.MAX_TRUST_STORE_BYTES,
                "remote script trust store"
            )
            val stamp = Files.readAttributes(
                file,
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS
            ).let {
                TrustFileStamp(it.size(), it.lastModifiedTime(), it.fileKey(), it.isRegularFile, it.isSymbolicLink)
            }
            updateCache(file.toAbsolutePath().normalize(), stamp, root)
        }.onFailure {
            LOGGER.warn("Failed to write Katton remote script trust store {}", file, it)
        }
    }

    private fun trustFile(): Path? {
        return Katton.gameDirectory?.resolve(".katton")?.resolve(TRUST_FILE_NAME)
    }

    private fun updateCache(file: Path, stamp: TrustFileStamp?, root: JsonObject) {
        cacheInitialized = true
        cachedFile = file
        cachedStamp = stamp
        cachedRoot = root
    }
}
