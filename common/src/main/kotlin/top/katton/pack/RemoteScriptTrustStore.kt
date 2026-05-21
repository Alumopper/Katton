package top.katton.pack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.katton.Katton
import top.katton.api.LOGGER
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

object RemoteScriptTrustStore {

    private const val TRUST_FILE_NAME = "remote-script-trust.json"

    @Synchronized
    fun isTrusted(serverBucket: String): Boolean {
        return readTrustedServers().has(serverBucket)
    }

    @Synchronized
    fun trust(serverBucket: String, serverAddress: String) {
        val root = readRoot()
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
        val root = readRoot()
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
        val file = trustFile() ?: return JsonObject()
        if (!Files.isRegularFile(file)) {
            return JsonObject()
        }

        return runCatching {
            JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).asJsonObject
        }.getOrElse {
            LOGGER.warn("Failed to read Katton remote script trust store {}", file, it)
            JsonObject()
        }
    }

    private fun writeRoot(root: JsonObject) {
        val file = trustFile() ?: return
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(
                file,
                root.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        }.onFailure {
            LOGGER.warn("Failed to write Katton remote script trust store {}", file, it)
        }
    }

    private fun trustFile(): Path? {
        return Katton.gameDirectory?.resolve(".katton")?.resolve(TRUST_FILE_NAME)
    }
}
