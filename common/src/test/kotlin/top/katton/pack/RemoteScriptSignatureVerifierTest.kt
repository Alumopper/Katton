package top.katton.pack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import top.katton.network.ScriptPackBundlePacket
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RemoteScriptSignatureVerifierTest {
    @Test
    fun `v3 signature covers source asset data and private library bytes`() {
        val unsigned = packData(
            manifestJson = BASE_MANIFEST,
            files = listOf(
                file("main.kt", "fun load() = Unit"),
                file("libs/private.jar", "library-bytes"),
                file("assets/example/lang/en_us.json", "{\"hello\":\"world\"}"),
                file("data/example/tags/item/example.json", "{\"values\":[]}")
            )
        )
        val signed = sign(unsigned)

        assertTrue(RemoteScriptSignatureVerifier.verify(signed).valid)

        val tamperedFiles = signed.files.map { entry ->
            if (entry.relativePath.startsWith("assets/")) entry.copy(content = "tampered".toByteArray()) else entry
        }
        assertFalse(RemoteScriptSignatureVerifier.verify(signed.copy(files = tamperedFiles)).valid)
        assertFalse(RemoteScriptSignatureVerifier.verify(signed.copy(files = signed.files.map {
            if (it.relativePath.startsWith("libs/")) it.copy(content = byteArrayOf(1)) else it
        })).valid)
    }

    @Test
    fun `length framing prevents binary content from becoming extra files`() {
        val oneFile = packData(
            manifestJson = BASE_MANIFEST,
            files = listOf(file("a", "x\u0000b\u0000y"))
        )
        val twoFiles = packData(
            manifestJson = BASE_MANIFEST,
            files = listOf(file("a", "x"), file("b", "y"))
        )

        val firstPayload = RemoteScriptSignatureVerifier.buildSignedPayload(oneFile)
        val secondPayload = RemoteScriptSignatureVerifier.buildSignedPayload(twoFiles)
        assertNotEquals(firstPayload.toList(), secondPayload.toList())
    }

    @Test
    fun `legacy payload metadata is rejected instead of treated as unsigned`() {
        val root = JsonParser.parseString(BASE_MANIFEST).asJsonObject
        root.add("signature", JsonObject().apply {
            addProperty("algorithm", "Ed25519")
            addProperty("keyId", "legacy")
            addProperty("publicKey", Base64.getEncoder().encodeToString(ByteArray(44)))
            addProperty("signature", Base64.getEncoder().encodeToString(ByteArray(64)))
        })

        val result = RemoteScriptSignatureVerifier.verify(packData(root.toString(), emptyList()))
        assertFalse(result.valid)
        assertTrue(result.signed)
        assertTrue(result.reason.contains("payload version 1"))
    }

    @Test
    fun `cached pack verifies against its original synchronized scope`() {
        val signed = sign(
            packData(
                manifestJson = BASE_MANIFEST,
                files = listOf(file("main.kt", "fun load() = Unit"))
            )
        )
        val cachePath = Path.of("cache")
        val cachedPack = ScriptPack(
            syncId = signed.syncId,
            scope = ScriptPackScope.SERVER_CACHE,
            kind = ScriptPackKind.DIRECTORY,
            location = cachePath,
            manifestJson = signed.manifestJson,
            manifest = ScriptPackManifest.parse(cachePath, signed.manifestJson),
            enabled = true,
            hash = signed.hash,
            codeHash = signed.hash,
            scripts = emptyList(),
            contentFiles = signed.files.map { entry ->
                ScriptPackContentFile(entry.relativePath, cachePath.resolve(entry.relativePath), entry.content)
            },
            compiledJar = null
        )

        assertTrue(RemoteScriptSignatureVerifier.verify(cachedPack).valid)
    }

    private fun sign(unsigned: ScriptPackBundlePacket.PackData): ScriptPackBundlePacket.PackData {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val payload = RemoteScriptSignatureVerifier.buildSignedPayload(unsigned)
        val signature = Signature.getInstance("Ed25519").apply {
            initSign(keyPair.private)
            update(payload)
        }.sign()
        val root = JsonParser.parseString(unsigned.manifestJson).asJsonObject
        root.add("signature", JsonObject().apply {
            addProperty("algorithm", "Ed25519")
            addProperty("payloadVersion", SCRIPT_PACK_SIGNATURE_PAYLOAD_VERSION)
            addProperty("keyId", "test-key")
            addProperty("publicKey", Base64.getEncoder().encodeToString(keyPair.public.encoded))
            addProperty("signature", Base64.getEncoder().encodeToString(signature))
        })
        return unsigned.copy(manifestJson = root.toString())
    }

    private fun packData(
        manifestJson: String,
        files: List<ScriptPackBundlePacket.ScriptFileData>
    ) = ScriptPackBundlePacket.PackData(
        syncId = "world:signed-test",
        scope = "world",
        hash = "test-hash",
        manifestJson = manifestJson,
        files = files
    )

    private fun file(path: String, content: String) =
        ScriptPackBundlePacket.ScriptFileData(path, content.toByteArray())

    private companion object {
        const val BASE_MANIFEST = """{"id":"signed-test","dependencies":[]}"""
    }
}
