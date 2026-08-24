package top.katton.pack

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptPackDependencyGraphTest {
    @Test
    fun `dependencies execute before dependents`() {
        val library = pack("library", "2.0.0")
        val addon = pack("addon", dependencies = listOf(ScriptPackDependency("library", ">=2.0")))

        val result = ScriptPackDependencyGraph.resolve(listOf(addon, library))

        assertTrue(result.errors.isEmpty())
        assertEquals(listOf("world:library", "world:addon"), result.orderedPacks.map { it.syncId })
    }

    @Test
    fun `missing requirements and cycles are rejected without rejecting independent packs`() {
        val independent = pack("independent")
        val missing = pack("missing", dependencies = listOf(ScriptPackDependency("absent")))
        val left = pack("left", dependencies = listOf(ScriptPackDependency("right")))
        val right = pack("right", dependencies = listOf(ScriptPackDependency("left")))

        val result = ScriptPackDependencyGraph.resolve(listOf(missing, right, independent, left))

        assertEquals(listOf("world:independent"), result.orderedPacks.map { it.syncId })
        assertTrue(result.errors.any { it.contains("not enabled") })
        assertTrue(result.errors.any { it.contains("cycle") })
    }

    @Test
    fun `rejected optional dependency does not reject its consumer`() {
        val brokenOptional = pack("optional", dependencies = listOf(ScriptPackDependency("absent")))
        val consumer = pack(
            "consumer",
            dependencies = listOf(ScriptPackDependency("optional", required = false))
        )

        val result = ScriptPackDependencyGraph.resolve(listOf(consumer, brokenOptional))

        assertEquals(listOf("world:consumer"), result.orderedPacks.map { it.syncId })
    }

    private fun pack(
        id: String,
        version: String = "1.0.0",
        dependencies: List<ScriptPackDependency> = emptyList()
    ) = ScriptPack(
        syncId = "world:$id",
        scope = ScriptPackScope.WORLD,
        kind = ScriptPackKind.DIRECTORY,
        location = Path.of(id),
        manifestJson = "{}",
        manifest = ScriptPackManifest(
            id = id,
            name = id,
            version = version,
            description = "",
            authors = emptyList(),
            enabledByDefault = true,
            clientSync = true,
            signature = null,
            dependencies = emptyList(),
            packDependencies = dependencies
        ),
        enabled = true,
        hash = id,
        codeHash = id,
        scripts = emptyList(),
        contentFiles = emptyList(),
        compiledJar = null
    )
}
