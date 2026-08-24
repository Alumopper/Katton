package top.katton.config

import org.junit.jupiter.api.io.TempDir
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackManifest
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KattonConfigManagerTest {
    @TempDir
    lateinit var tempDirectory: Path

    @AfterTest
    fun cleanUp() {
        KattonConfigManager.clearAll()
        KattonConfigManager.setStorageDirectory(null)
    }

    @Test
    fun `scope-qualified ids do not collide and ambiguous manifest id is rejected`() {
        KattonConfigManager.setStorageDirectory(tempDirectory.resolve("overrides"))
        KattonConfigManager.registerPack(pack("global:same", ScriptPackScope.GLOBAL, "global"))
        KattonConfigManager.registerPack(pack("world:same", ScriptPackScope.WORLD, "world"))

        assertEquals("global", KattonConfigManager.get("global:same", "value"))
        assertEquals("world", KattonConfigManager.get("world:same", "value"))
        assertNull(KattonConfigManager.get("same", "value"))
        assertEquals(setOf("global:same", "world:same"), KattonConfigManager.knownPackIds())
    }

    @Test
    fun `jar config override survives pack registration`() {
        KattonConfigManager.setStorageDirectory(tempDirectory.resolve("overrides"))
        val pack = pack("world:jar-pack", ScriptPackScope.WORLD, "default", ScriptPackKind.JAR)
        KattonConfigManager.registerPack(pack)

        assertTrue(KattonConfigManager.set(pack.syncId, "value", "override"))
        KattonConfigManager.clearAll()
        KattonConfigManager.registerPack(pack)

        assertEquals("override", KattonConfigManager.get(pack.syncId, "value"))
    }

    @Test
    fun `server-cache config does not collide with the same local sync id`() {
        val local = pack("global:remote", ScriptPackScope.GLOBAL, "local")
        val pack = pack("global:remote", ScriptPackScope.SERVER_CACHE, "remote")
        KattonConfigManager.registerPack(local)
        KattonConfigManager.registerPack(pack)
        val configId = KattonConfigManager.configId(pack)
        KattonConfigManager.registerFqcnMapping("example.RemoteKt", configId)

        val resolved = ScriptExecutionContext.withOwner("server_cache:READY:example.RemoteKt") {
            KattonConfigManager.resolveCurrentPack()
        }

        assertEquals("local", KattonConfigManager.get(local.syncId, "value"))
        assertEquals("remote", KattonConfigManager.get(configId, "value"))
        assertEquals("server_cache:global:remote", resolved)
    }

    @Test
    fun `file JvmName participates in entrypoint ownership mapping`() {
        assertEquals(
            "example.CustomEntrypoints",
            KattonConfigManager.deriveFqcn(
                """
                    @file:JvmName("CustomEntrypoints")
                    package example
                    fun load() = Unit
                """.trimIndent(),
                "main.kt"
            )
        )
    }

    private fun pack(
        syncId: String,
        scope: ScriptPackScope,
        value: String,
        kind: ScriptPackKind = ScriptPackKind.DIRECTORY
    ) = ScriptPack(
        syncId = syncId,
        scope = scope,
        kind = kind,
        location = tempDirectory.resolve(syncId.replace(':', '-')),
        manifestJson = "{}",
        manifest = ScriptPackManifest(
            id = syncId.substringAfter(':'),
            name = syncId,
            version = "1.0.0",
            description = "",
            authors = emptyList(),
            enabledByDefault = true,
            clientSync = true,
            signature = null,
            dependencies = emptyList(),
            config = mapOf("value" to value)
        ),
        enabled = true,
        hash = "hash",
        codeHash = "code",
        scripts = emptyList(),
        contentFiles = emptyList(),
        compiledJar = null
    )
}
