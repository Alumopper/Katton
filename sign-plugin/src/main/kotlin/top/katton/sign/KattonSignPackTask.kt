package top.katton.sign

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

abstract class KattonSignPackTask : DefaultTask() {

    @get:InputDirectory
    abstract val packDir: DirectoryProperty

    @get:InputFile
    abstract val privateKeyFile: RegularFileProperty

    @get:InputFile
    abstract val publicKeyFile: RegularFileProperty

    @get:Input
    abstract val scope: Property<String>

    @get:Input
    abstract val keyId: Property<String>

    @TaskAction
    fun sign() {
        val packPath = packDir.get().asFile.toPath().toAbsolutePath().normalize()
        val manifestFile = packPath.resolve("manifest.json")
        val manifestJson = Files.readString(manifestFile, StandardCharsets.UTF_8)
        val manifest = JsonParser.parseString(manifestJson).asJsonObject
        val packId = manifest.get("id")?.asString?.takeIf { it.isNotBlank() }
            ?: error("manifest.json missing non-empty 'id' field: $manifestFile")
        val scopeValue = scope.get()
        val syncId = "$scopeValue:$packId"
        val files = collectFiles(packPath)
        val payload = buildPayload(syncId, scopeValue, manifestJson, files)

        val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(
            PKCS8EncodedKeySpec(readPem(privateKeyFile.get().asFile.toPath(), "PRIVATE KEY"))
        )
        val signatureBytes = Signature.getInstance("Ed25519").apply {
            initSign(privateKey)
            update(payload)
        }.sign()
        val publicKeyBytes = readPem(publicKeyFile.get().asFile.toPath(), "PUBLIC KEY")

        manifest.remove("signature")
        manifest.add("signature", JsonObject().apply {
            addProperty("algorithm", "Ed25519")
            addProperty("keyId", keyId.get())
            addProperty("publicKey", Base64.getEncoder().encodeToString(publicKeyBytes))
            addProperty("signature", Base64.getEncoder().encodeToString(signatureBytes))
        })
        Files.writeString(manifestFile, manifest.toString(), StandardCharsets.UTF_8)

        logger.lifecycle("Signed Katton pack {}", packId)
        logger.lifecycle("  Scope:       {}", scopeValue)
        logger.lifecycle("  Sync ID:     {}", syncId)
        logger.lifecycle("  Key ID:      {}", keyId.get())
        logger.lifecycle("  Fingerprint: {}", fingerprint(publicKeyBytes))
        logger.lifecycle("  Files:       {}", files.size)
        logger.lifecycle("  Manifest:    {}", manifestFile)
    }

    private fun collectFiles(packPath: Path): List<PackFile> {
        val files = mutableListOf<PackFile>()
        Files.walk(packPath).use { stream ->
            stream.forEach { file ->
                if (!Files.isRegularFile(file)) return@forEach
                val name = file.fileName.toString()
                if (!name.endsWith(".kt", ignoreCase = true) && !name.endsWith(".java", ignoreCase = true)) return@forEach
                files += PackFile(
                    relativePath = packPath.relativize(file).toString().replace('\\', '/'),
                    bytes = Files.readAllBytes(file)
                )
            }
        }
        return files.sortedBy { it.relativePath }
    }

    private fun buildPayload(syncId: String, scope: String, manifestJson: String, files: List<PackFile>): ByteArray {
        val root = JsonParser.parseString(manifestJson).asJsonObject
        root.remove("signature")
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("katton-script-pack-signature-v1".toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(syncId.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(scope.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(root.toString().toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        files.forEach { file ->
            digest.update(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(file.bytes)
            digest.update(0)
        }
        return digest.digest()
    }

    private fun readPem(path: Path, label: String): ByteArray {
        val text = Files.readString(path, StandardCharsets.UTF_8)
        val normalized = text
            .replace("-----BEGIN $label-----", "")
            .replace("-----END $label-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
        return Base64.getDecoder().decode(normalized)
    }

    private fun fingerprint(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02x".format(it) }
    }

    data class PackFile(val relativePath: String, val bytes: ByteArray)
}
