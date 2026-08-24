package top.katton.sign

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64

abstract class GenerateKattonSigningKeyTask : DefaultTask() {

    @get:OutputFile
    abstract val privateKeyFile: RegularFileProperty

    @get:OutputFile
    abstract val publicKeyFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val privatePath = privateKeyFile.get().asFile.toPath().toAbsolutePath().normalize()
        val publicPath = publicKeyFile.get().asFile.toPath().toAbsolutePath().normalize()
        require(!Files.exists(privatePath, LinkOption.NOFOLLOW_LINKS)) {
            "Refusing to overwrite existing Katton private key: $privatePath"
        }
        require(!Files.exists(publicPath, LinkOption.NOFOLLOW_LINKS)) {
            "Refusing to overwrite existing Katton public key: $publicPath"
        }

        Files.createDirectories(privatePath.parent)
        Files.createDirectories(publicPath.parent)
        writeAtomically(privatePath, pem("PRIVATE KEY", keyPair.private.encoded), privateKey = true)
        try {
            writeAtomically(publicPath, pem("PUBLIC KEY", keyPair.public.encoded), privateKey = false)
        } catch (failure: Throwable) {
            // Avoid leaving half a key pair that a later run cannot safely
            // distinguish from an intentionally preserved private key.
            runCatching { Files.deleteIfExists(privatePath) }
            throw failure
        }

        logger.lifecycle("Generated Katton signing key:")
        logger.lifecycle("  Private key: {}", privatePath)
        logger.lifecycle("  Public key:  {}", publicPath)
        logger.lifecycle("  Fingerprint: {}", fingerprint(keyPair.public.encoded))
        logger.lifecycle("Keep the private key secret and do not commit it.")
    }

    private fun pem(label: String, bytes: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes)
        return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
    }

    private fun fingerprint(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02x".format(it) }
    }

    private fun writeAtomically(path: Path, content: String, privateKey: Boolean) {
        var temporaryFile: Path? = null
        try {
            val temp = Files.createTempFile(path.parent, ".katton-key-", ".tmp")
            temporaryFile = temp
            Files.writeString(
                temp,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
            if (privateKey) {
                runCatching {
                    Files.setPosixFilePermissions(
                        temp,
                        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                    )
                }
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, path)
            }
            temporaryFile = null
        } finally {
            temporaryFile?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }
}
