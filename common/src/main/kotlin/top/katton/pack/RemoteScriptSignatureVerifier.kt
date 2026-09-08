package top.katton.pack

import com.google.gson.JsonParser
import top.katton.network.ScriptPackBundlePacket
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Verifies optional Ed25519 metadata before a remote pack reaches the cache. */
object RemoteScriptSignatureVerifier {
    private const val SIGNATURE_ALGORITHM = "Ed25519"
    private const val ED25519_SIGNATURE_BYTES = 64
    private const val MAX_PUBLIC_KEY_BYTES = 512
    private val PAYLOAD_DOMAIN =
        "katton-script-pack-signature-v3".toByteArray(StandardCharsets.UTF_8)

    data class VerificationResult(
        val valid: Boolean,
        val reason: String,
        val keyFingerprint: String? = null,
        val signed: Boolean = true
    )

    fun verify(pack: ScriptPack): VerificationResult {
        val signature = pack.manifest.signature
            ?: return VerificationResult(true, "missing signature metadata", signed = false)
        return verifySignature(signature, buildSignedPayload(pack))
    }

    fun verify(packData: ScriptPackBundlePacket.PackData): VerificationResult =
        runCatching {
            val manifest = ScriptPackManifest.parse(Path.of("remote-pack"), packData.manifestJson)
            val signature = manifest.signature
                ?: return VerificationResult(true, "missing signature metadata", signed = false)
            verifySignature(signature, buildSignedPayload(packData))
        }.getOrElse {
            VerificationResult(false, "signature verification failed: ${it.message ?: it.javaClass.simpleName}")
        }

    private fun verifySignature(signature: ScriptPackSignature, signedPayload: ByteArray): VerificationResult {
        if (signature.algorithm != SIGNATURE_ALGORITHM) {
            return VerificationResult(false, "unsupported signature algorithm ${signature.algorithm}")
        }
        if (signature.payloadVersion != SCRIPT_PACK_SIGNATURE_PAYLOAD_VERSION) {
            return VerificationResult(
                false,
                "unsupported signature payload version ${signature.payloadVersion}; re-sign this pack with format " +
                    SCRIPT_PACK_SIGNATURE_PAYLOAD_VERSION
            )
        }

        // A pinned key always wins over an embedded key. If both are present,
        // requiring them to match prevents a server from silently rotating an
        // already trusted key ID.
        val trustedPublicKey = RemoteScriptTrustStore.trustedPublicKey(signature.keyId)
        val publicKeyText = trustedPublicKey ?: signature.publicKey
            ?: return VerificationResult(false, "missing public key for ${signature.keyId}")
        val publicKeyBytes = decodeBase64(publicKeyText, MAX_PUBLIC_KEY_BYTES)
            ?: return VerificationResult(false, "invalid public key encoding for ${signature.keyId}")
        val signatureBytes = decodeBase64(signature.signature, ED25519_SIGNATURE_BYTES)
            ?.takeIf { it.size == ED25519_SIGNATURE_BYTES }
            ?: return VerificationResult(false, "invalid Ed25519 signature encoding for ${signature.keyId}")

        val fingerprint = fingerprint(publicKeyBytes)
        if (trustedPublicKey != null && signature.publicKey != null) {
            val embeddedBytes = decodeBase64(signature.publicKey, MAX_PUBLIC_KEY_BYTES)
                ?: return VerificationResult(
                    false,
                    "invalid embedded public key encoding for ${signature.keyId}",
                    fingerprint
                )
            if (!MessageDigest.isEqual(publicKeyBytes, embeddedBytes)) {
                return VerificationResult(
                    false,
                    "embedded public key does not match trusted key for ${signature.keyId}",
                    fingerprint
                )
            }
        }

        val publicKey = runCatching {
            KeyFactory.getInstance(SIGNATURE_ALGORITHM).generatePublic(X509EncodedKeySpec(publicKeyBytes))
        }.getOrElse {
            return VerificationResult(false, "invalid Ed25519 public key for ${signature.keyId}", fingerprint)
        }
        val valid = runCatching {
            Signature.getInstance(SIGNATURE_ALGORITHM).apply {
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

    fun buildSignedPayload(pack: ScriptPack): ByteArray = buildSignedPayload(
        syncId = pack.syncId,
        // Cached multiplayer packs deliberately use SERVER_CACHE for lifecycle
        // policy, but their signature was made with the server's original
        // global/world scope. Recover that signed value from the stable sync ID.
        scope = if (pack.scope == ScriptPackScope.SERVER_CACHE) {
            pack.syncId.substringBefore(':')
        } else {
            pack.scope.serializedName
        },
        manifestJson = pack.manifestJson,
        files = pack.contentFiles.map { SignedFile(it.relativePath, it.bytes) }
    )

    fun buildSignedPayload(packData: ScriptPackBundlePacket.PackData): ByteArray = buildSignedPayload(
        syncId = packData.syncId,
        scope = packData.scope,
        manifestJson = packData.manifestJson,
        files = packData.files.map { SignedFile(it.relativePath, it.content) }
    )

    /**
     * Version 3 covers private JARs and length-prefixes every field, including the file
     * count. Unlike the old NUL-delimited stream, arbitrary binary asset bytes
     * can no longer be reinterpreted as additional paths or files.
     */
    private fun buildSignedPayload(
        syncId: String,
        scope: String,
        manifestJson: String,
        files: List<SignedFile>
    ): ByteArray {
        val manifestWithoutSignature = canonicalManifestWithoutSignature(manifestJson)
        val sortedFiles = files.sortedBy { it.relativePath }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed(PAYLOAD_DOMAIN)
        digest.updateFramed(syncId.toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(scope.toByteArray(StandardCharsets.UTF_8))
        digest.updateFramed(manifestWithoutSignature.toByteArray(StandardCharsets.UTF_8))
        digest.updateInt(sortedFiles.size)
        sortedFiles.forEach { file ->
            digest.updateFramed(file.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.updateFramed(file.bytes)
        }
        return digest.digest()
    }

    private fun canonicalManifestWithoutSignature(manifestJson: String): String {
        val root = JsonParser.parseString(manifestJson).asJsonObject
        root.remove("signature")
        return root.toString()
    }

    private fun decodeBase64(text: String, maximumBytes: Int): ByteArray? {
        // Four base64 characters encode at most three bytes. Checking the text
        // first avoids allocating a large temporary array for malformed input.
        if (text.length > ((maximumBytes + 2) / 3) * 4 + 4) return null
        return runCatching { Base64.getDecoder().decode(text) }
            .recoverCatching { Base64.getUrlDecoder().decode(text) }
            .getOrNull()
            ?.takeIf { it.size <= maximumBytes }
    }

    private fun fingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02x".format(it) }
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

    private data class SignedFile(val relativePath: String, val bytes: ByteArray)
}
