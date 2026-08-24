package top.katton.engine

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.katton.pack.DependencyEnvironment
import top.katton.pack.ScriptDependency
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackManifest
import top.katton.pack.ScriptPackScope
import top.katton.pack.ScriptPlatform

class ScriptDependencyManagerTest {
    @AfterTest
    fun resetResolver() {
        ScriptDependencyManager.install(ScriptPlatform.UNKNOWN, PlatformDependencyResolver { null })
    }

    @Test
    fun `required missing and incompatible dependencies disable only affected packs`() {
        ScriptDependencyManager.install(ScriptPlatform.FABRIC) { id ->
            if (id == "present") ResolvedScriptDependency(id, "1.5.0", emptyList(), null) else null
        }
        val missing = pack("missing", dependency("absent", required = true))
        val wrongVersion = pack("wrong", dependency("present", version = ">=2.0"))
        val valid = pack("valid", dependency("present", version = ">=1.0 <2.0"))

        val result = ScriptDependencyManager.resolve(
            listOf(missing, wrongVersion, valid),
            ScriptEnvironment.SERVER,
            "READY"
        )

        assertEquals(listOf(valid), result.validPacks)
        assertEquals(2, result.errors.size)
    }

    @Test
    fun `optional and non-applicable dependencies do not disable a pack`() {
        ScriptDependencyManager.install(ScriptPlatform.FABRIC, PlatformDependencyResolver { null })
        val optional = pack("optional", dependency("absent", required = false))
        val paperOnly = pack(
            "paper-only",
            dependency("plugin", platforms = setOf(ScriptPlatform.PAPER))
        )
        val clientOnly = pack(
            "client-only",
            dependency("client-mod", environment = DependencyEnvironment.CLIENT)
        )

        val result = ScriptDependencyManager.resolve(
            listOf(optional, paperOnly, clientOnly),
            ScriptEnvironment.SERVER,
            "READY"
        )

        assertEquals(3, result.validPacks.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `failed and incompatible dependencies do not leak into compiler classpath`() {
        val lookups = AtomicInteger()
        ScriptDependencyManager.install(ScriptPlatform.FABRIC) { id ->
            lookups.incrementAndGet()
            when (id) {
                "old-api" -> ResolvedScriptDependency(id, "1.0.0", listOf(Path.of("old-api.jar")), null)
                else -> null
            }
        }
        val incompatibleOptional = pack(
            "optional",
            dependency("old-api", version = ">=2.0", required = false)
        )
        val missingOne = pack("missing-one", dependency("absent"))
        val missingTwo = pack("missing-two", dependency("absent"))

        val result = ScriptDependencyManager.resolve(
            listOf(incompatibleOptional, missingOne, missingTwo),
            ScriptEnvironment.SERVER,
            "READY"
        )

        assertEquals(listOf(incompatibleOptional), result.validPacks)
        assertTrue(result.resolved.isEmpty())
        assertTrue(result.fingerprints.isEmpty())
        assertEquals(2, lookups.get(), "each distinct dependency id should be resolved once per pass")
    }

    @Test
    fun `delegated classes are cached after their first lookup`() {
        val lookups = AtomicInteger()
        val delegate = object : ClassLoader(null) {
            override fun loadClass(name: String): Class<*> {
                if (name == "plugin.ExampleApi") {
                    lookups.incrementAndGet()
                    return String::class.java
                }
                return super.loadClass(name)
            }
        }
        val loader = DependencyDelegatingClassLoader(javaClass.classLoader, listOf(delegate))

        assertSame(String::class.java, loader.loadClass("plugin.ExampleApi"))
        assertSame(String::class.java, loader.loadClass("plugin.ExampleApi"))
        assertEquals(1, lookups.get())
    }

    private fun dependency(
        id: String,
        version: String = "*",
        required: Boolean = true,
        platforms: Set<ScriptPlatform> = setOf(ScriptPlatform.FABRIC),
        environment: DependencyEnvironment = DependencyEnvironment.BOTH
    ) = ScriptDependency(id, version, required, platforms, environment)

    private fun pack(id: String, dependency: ScriptDependency) = ScriptPack(
        syncId = "world:$id",
        scope = ScriptPackScope.WORLD,
        kind = ScriptPackKind.DIRECTORY,
        location = Path.of(id),
        manifestJson = "{}",
        manifest = ScriptPackManifest(
            id = id,
            name = id,
            version = "1.0.0",
            description = "",
            authors = emptyList(),
            enabledByDefault = true,
            clientSync = true,
            signature = null,
            dependencies = listOf(dependency)
        ),
        enabled = true,
        hash = id,
        codeHash = id,
        scripts = emptyList(),
        contentFiles = emptyList(),
        compiledJar = null
    )
}
