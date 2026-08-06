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
    fun `empty dependencies are valid`() {
        val manifest = ScriptPackManifest.parse(path, """{"id":"example","dependencies":[]}""")
        assertEquals("example", manifest.id)
        assertTrue(manifest.dependencies.isEmpty())
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
}
