package top.katton.pack

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptPackManifestTest {
    private val path = Path.of("example-pack")

    @Test
    fun `dependencies field is mandatory`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ScriptPackManifest.parse(path, """{"id":"example"}""")
        }
        assertTrue(error.message.orEmpty().contains("\"dependencies\": []"))
    }

    @Test
    fun `malformed json reports the real manifest error`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ScriptPackManifest.parse(path, """{"id":"example","dependencies":[}""")
        }
        assertTrue(error.message.orEmpty().contains("Invalid Katton pack manifest JSON"))
    }

    @Test
    fun `empty dependencies are valid`() {
        val manifest = ScriptPackManifest.parse(path, """{"id":"example","dependencies":[]}""")
        assertEquals("example", manifest.id)
        assertTrue(manifest.dependencies.isEmpty())
    }

    @Test
    fun `pack dependencies parse independently from platform dependencies`() {
        val manifest = ScriptPackManifest.parse(
            path,
            """{"id":"addon","dependencies":[],"packDependencies":[{"id":"core","version":">=2","required":true}]}"""
        )

        assertEquals(ScriptPackDependency("core", ">=2", true), manifest.packDependencies.single())
    }

    @Test
    fun `dependency defaults and filters are parsed`() {
        val manifest = ScriptPackManifest.parse(path, """
            {
              "id":"integration",
              "dependencies":[{
                "id":"Vault",
                "platforms":["paper"],
                "environment":"server"
              }]
            }
        """.trimIndent())
        val dependency = manifest.dependencies.single()
        assertEquals("*", dependency.version)
        assertTrue(dependency.required)
        assertTrue(dependency.appliesTo(ScriptPlatform.PAPER, client = false))
        assertFalse(dependency.appliesTo(ScriptPlatform.PAPER, client = true))
        assertFalse(dependency.appliesTo(ScriptPlatform.FABRIC, client = false))
    }

    @Test
    fun `platform list must be non-empty`() {
        assertFailsWith<IllegalArgumentException> {
            ScriptPackManifest.parse(path, """{"id":"bad","dependencies":[{"id":"x","platforms":[]}]}""")
        }
    }

    @Test
    fun `duplicate dependency ids are rejected case insensitively`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ScriptPackManifest.parse(
                path,
                """{
                    "dependencies": [
                        {"id":"Create","platforms":["fabric"]},
                        {"id":"create","platforms":["fabric"]}
                    ]
                }"""
            )
        }

        assertTrue(error.message.orEmpty().contains("overlapping declarations"))
    }

    @Test
    fun `malformed signature metadata cannot downgrade to unsigned`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ScriptPackManifest.parse(
                path,
                """{"dependencies":[],"signature":{"algorithm":"Ed25519","keyId":"publisher"}}"""
            )
        }

        assertTrue(error.message.orEmpty().contains("signature value"))
    }

    @Test
    fun `signature payload version defaults to legacy v1`() {
        val manifest = ScriptPackManifest.parse(
            path,
            """{"dependencies":[],"signature":{"algorithm":"Ed25519","keyId":"publisher","signature":"AA=="}}"""
        )

        assertEquals(1, manifest.signature?.payloadVersion)
    }

    @Test
    fun `signature payload version parses current v3`() {
        val manifest = ScriptPackManifest.parse(
            path,
            """{"dependencies":[],"signature":{"algorithm":"Ed25519","payloadVersion":3,"keyId":"publisher","signature":"AA=="}}"""
        )

        assertEquals(SCRIPT_PACK_SIGNATURE_PAYLOAD_VERSION, manifest.signature?.payloadVersion)
    }

    @Test
    fun `signature payload version rejects fractional numbers`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ScriptPackManifest.parse(
                path,
                """{"dependencies":[],"signature":{"payloadVersion":2.5,"keyId":"publisher","signature":"AA=="}}"""
            )
        }

        assertTrue(error.message.orEmpty().contains("positive integer"))
    }
}
