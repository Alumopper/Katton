package top.katton.sign

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
        val privatePath = privateKeyFile.get().asFile.toPath()
        val publicPath = publicKeyFile.get().asFile.toPath()

        Files.createDirectories(privatePath.parent)
        Files.createDirectories(publicPath.parent)
        Files.writeString(privatePath, pem("PRIVATE KEY", keyPair.private.encoded), StandardCharsets.UTF_8)
        Files.writeString(publicPath, pem("PUBLIC KEY", keyPair.public.encoded), StandardCharsets.UTF_8)

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
}
