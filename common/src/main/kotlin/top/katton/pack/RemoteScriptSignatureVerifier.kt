package top.katton.pack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.katton.network.ScriptPackBundlePacket
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object RemoteScriptSignatureVerifier {

    data class VerificationResult(
        val valid: Boolean,
        val reason: String,
        val keyFingerprint: String? = null,
        val signed: Boolean = true
    )

    fun verify(pack: ScriptPack): VerificationResult {
        //check manifest signature metadata presence and algorithm support
        val signature = pack.manifest.signature
            ?: return VerificationResult(true, "missing signature metadata", signed = false)
        if (signature.algorithm != "Ed25519") {
            return VerificationResult(false, "unsupported signature algorithm ${signature.algorithm}")
        }
        //resolve public key
        val trustedPublicKey = RemoteScriptTrustStore.trustedPublicKey(signature.keyId)
        val publicKeyText = trustedPublicKey ?: signature.publicKey
            ?: return VerificationResult(false, "missing public key for ${signature.keyId}")
        val publicKeyBytes = decodeBase64(publicKeyText)
            ?: return VerificationResult(false, "invalid public key encoding for ${signature.keyId}")
        val signatureBytes = decodeBase64(signature.signature)
            ?: return VerificationResult(false, "invalid signature encoding for ${signature.keyId}")

        val fingerprint = fingerprint(publicKeyBytes)
        if (trustedPublicKey != null && signature.publicKey != null) {
            val embeddedBytes = decodeBase64(signature.publicKey)
                ?: return VerificationResult(false, "invalid embedded public key encoding for ${signature.keyId}")
            if (!MessageDigest.isEqual(publicKeyBytes, embeddedBytes)) {
                return VerificationResult(false, "embedded public key does not match trusted key for ${signature.keyId}", fingerprint)
            }
        }

        val signedPayload = buildSignedPayload(pack)
        val publicKey = runCatching {
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        }.getOrElse {
            return VerificationResult(false, "invalid Ed25519 public key for ${signature.keyId}", fingerprint)
        }

        val valid = runCatching {
            Signature.getInstance("Ed25519").apply {
                initVerify(publicKey)
                update(signedPayload)
            }.verify(signatureBytes)
        }.getOrDefault(false)

        return if (valid) {
            VerificationResult(true, "signature verified", fingerprint)
        } else {
            VerificationResult(false, "signature mismatch", fingerprint)
        }
    }

    fun verify(packData: ScriptPackBundlePacket.PackData): VerificationResult {
        return runCatching { verifyBundlePack(packData) }
            .getOrElse { VerificationResult(false, "signature verification failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun verifyBundlePack(packData: ScriptPackBundlePacket.PackData): VerificationResult {
        val manifest = ScriptPackManifest.parse(Path.of("remote-pack"), packData.manifestJson)
        val signature = manifest.signature
            ?: return VerificationResult(true, "missing signature metadata", signed = false)
        if (signature.algorithm != "Ed25519") {
            return VerificationResult(false, "unsupported signature algorithm ${signature.algorithm}")
        }

        val trustedPublicKey = RemoteScriptTrustStore.trustedPublicKey(signature.keyId)
        val publicKeyText = trustedPublicKey ?: signature.publicKey
            ?: return VerificationResult(false, "missing public key for ${signature.keyId}")
        val publicKeyBytes = decodeBase64(publicKeyText)
            ?: return VerificationResult(false, "invalid public key encoding for ${signature.keyId}")
        val signatureBytes = decodeBase64(signature.signature)
            ?: return VerificationResult(false, "invalid signature encoding for ${signature.keyId}")

        val fingerprint = fingerprint(publicKeyBytes)
        if (trustedPublicKey != null && signature.publicKey != null) {
            val embeddedBytes = decodeBase64(signature.publicKey)
                ?: return VerificationResult(false, "invalid embedded public key encoding for ${signature.keyId}")
            if (!MessageDigest.isEqual(publicKeyBytes, embeddedBytes)) {
                return VerificationResult(false, "embedded public key does not match trusted key for ${signature.keyId}", fingerprint)
            }
        }

        val publicKey = runCatching {
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        }.getOrElse {
            return VerificationResult(false, "invalid Ed25519 public key for ${signature.keyId}", fingerprint)
        }

        val signedPayload = buildSignedPayload(packData)
        val valid = runCatching {
            Signature.getInstance("Ed25519").apply {
                initVerify(publicKey)
                update(signedPayload)
            }.verify(signatureBytes)
        }.getOrDefault(false)

        return if (valid) {
            VerificationResult(true, "signature verified", fingerprint)
        } else {
            VerificationResult(false, "signature mismatch", fingerprint)
        }
    }

    fun buildSignedPayload(pack: ScriptPack): ByteArray {
        val manifestWithoutSignature = canonicalManifestWithoutSignature(pack.manifestJson)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("katton-script-pack-signature-v1".toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(pack.syncId.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(pack.scope.serializedName.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(manifestWithoutSignature.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        pack.contentFiles.sortedBy { it.relativePath }.forEach { file ->
            digest.update(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(file.bytes)
            digest.update(0)
        }
        return digest.digest()
    }

    fun buildSignedPayload(packData: ScriptPackBundlePacket.PackData): ByteArray {
        val manifestWithoutSignature = canonicalManifestWithoutSignature(packData.manifestJson)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("katton-script-pack-signature-v1".toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(packData.syncId.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(packData.scope.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(manifestWithoutSignature.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        packData.files.sortedBy { it.relativePath }.forEach { file ->
            digest.update(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(file.content)
            digest.update(0)
        }
        return digest.digest()
    }

    private fun canonicalManifestWithoutSignature(manifestJson: String): String {
        val root = runCatching { JsonParser.parseString(manifestJson).asJsonObject }
            .getOrElse { return manifestJson }
        root.remove("signature")
        return root.toString()
    }

    private fun decodeBase64(text: String): ByteArray? {
        return runCatching { Base64.getDecoder().decode(text) }
            .recoverCatching { Base64.getUrlDecoder().decode(text) }
            .getOrNull()
    }

    private fun fingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02x".format(it) }
    }
}
