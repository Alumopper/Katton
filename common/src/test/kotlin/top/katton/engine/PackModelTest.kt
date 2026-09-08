package top.katton.engine

import org.junit.jupiter.api.io.TempDir
import top.katton.pack.*
import top.katton.util.ScriptExecutionContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class PackModelTest {
    @TempDir lateinit var root: Path
    private fun pack(id: String, dependencies: String = "", source: String = ""): ScriptPack {
        val dir = Files.createDirectories(root.resolve(id))
        Files.writeString(dir.resolve("manifest.json"), """{"id":"$id","version":"1.0","dependencies":[],"packDependencies":[$dependencies]}""")
        if (source.isNotEmpty()) Files.writeString(dir.resolve("Code.kt"), source)
        return assertNotNull(ScriptPackManager.scanPackDirectory(dir, ScriptPackScope.WORLD))
    }
    private fun edge(id: String, export: Boolean = false, required: Boolean = true) = """{"id":"$id","export":$export,"required":$required}"""

    @Test fun `export closure excludes private transitive dependencies`() {
        val c = pack("c")
        val b = pack("b", edge("c"))
        val a = pack("a", edge("b"))
        assertEquals(listOf(b), ScriptPackDependencyGraph.resolve(listOf(a,b,c)).visiblePacks(a))
        val exported = b.copy(manifest = b.manifest.copy(packDependencies = listOf(ScriptPackDependency("c", export = true))))
        assertEquals(listOf(c, exported), ScriptPackDependencyGraph.resolve(listOf(a,exported,c)).visiblePacks(a))
    }
    @Test fun `optional cycles give a complete closed path`() {
        val a = pack("a", edge("b", required = false))
        val b = pack("b", edge("a", required = false))
        val graph = ScriptPackDependencyGraph.resolve(listOf(a,b))
        assertEquals(setOf(a,b), graph.invalidPacks)
        assertTrue(graph.errors.single().contains("world:a -> world:b -> world:a"))
    }
    @Test fun `reverse impact preserves siblings and includes deleted edges`() {
        val b = pack("b")
        val a = pack("a", edge("b"))
        val c = pack("c", edge("b"))
        val all = listOf(a,b,c)
        assertEquals(setOf(a.syncId), ScriptPackDependencyGraph.affected(all,all,setOf(a.syncId)))
        assertEquals(all.map { it.syncId }.toSet(), ScriptPackDependencyGraph.affected(all,all,setOf(b.syncId)))
        assertEquals(all.map { it.syncId }.toSet(), ScriptPackDependencyGraph.affected(all,listOf(a,c),setOf(b.syncId)))
    }
    @Test fun `ZIP and directory produce identical logical files hashes and libraries`() {
        val initial = pack("content", source = "val answer = 42")
        Files.createDirectories(initial.location.resolve("libs"))
        Files.write(initial.location.resolve("libs/helper.jar"), byteArrayOf(1,2,3))
        val directory = assertNotNull(ScriptPackManager.scanPackDirectory(initial.location, initial.scope))
        val archive = root.resolve("content.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            (mapOf("manifest.json" to directory.manifestJson.toByteArray()) + directory.contentFiles.associate { it.relativePath to it.bytes }).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        val zipped = assertNotNull(ScriptPackManager.scanPackZip(archive, ScriptPackScope.WORLD))
        assertEquals(directory.hash, zipped.hash)
        assertEquals(directory.codeHash, zipped.codeHash)
        assertEquals(directory.contentFiles.map { it.relativePath }, zipped.contentFiles.map { it.relativePath })
        Files.write(initial.location.resolve("libs/helper.jar"), byteArrayOf(4))
        assertNotEquals(directory.codeHash, ScriptPackManager.scanPackDirectory(initial.location, initial.scope)!!.codeHash)
    }
    @Test fun `rollback restores original contributions without replaying handlers`() {
        val map = mutableMapOf("value" to "base")
        ScriptExecutionContext.withOwner("a:1:") { ManagedResources.put(map, "value", "a") }
        ScriptExecutionContext.withOwner("b:1:") { ManagedResources.put(map, "value", "b") }
        val old = ManagedResources.detach(listOf("a:"))
        assertEquals("b", map["value"])
        ScriptExecutionContext.withOwner("a:2:") { ManagedResources.put(map, "value", "candidate") }
        ManagedResources.discard(ManagedResources.detach(listOf("a:2:")))
        ManagedResources.restore(old)
        // Restore must retain the original precedence relative to unrelated B.
        assertEquals("b", map["value"])
        ManagedResources.discard(ManagedResources.detach(listOf("a:", "b:")))
        assertEquals("base", map["value"])
    }
    @Test fun `callback timeout resumes old generation`() {
        assertTrue(ManagedResources.enter("busy:1"))
        assertFalse(ManagedResources.pause(listOf("busy:"), 1))
        assertFalse(ManagedResources.isPaused("busy:1"))
        ManagedResources.leave("busy:1")
    }
    @Test fun `persistent records survive reload detach but candidate rollback removes them`() {
        var registered = true
        val record = ScriptExecutionContext.withOwner("persistent:old:") {
            ManagedResources.record(attach = { registered = true }, detach = { registered = false }, persistent = true)
        }!!
        assertTrue(ManagedResources.detach(listOf("persistent:")).isEmpty())
        assertTrue(registered)
        assertTrue(ManagedResources.retains("persistent:old:"))
        val candidate = ManagedResources.detach(listOf("persistent:"), includePersistent = true)
        assertFalse(registered)
        ManagedResources.restore(candidate)
        assertTrue(registered)
        ManagedResources.release(record)
        assertFalse(ManagedResources.retains("persistent:old:"))
    }
    @Test fun `resource commit failure preserves an independently committed pack`() {
        val a = pack("a")
        val b = pack("b")
        val nextA = a.copy(hash = a.hash + "assets")
        val nextB = b.copy(hash = b.hash + "assets")
        val committed = mutableListOf<List<ScriptPack>>()
        val restored = mutableListOf<List<ScriptPack>>()
        val result = PackReloadBatch.run(listOf(a, b), listOf(nextA, nextB),
            activate = { proposed ->
                if (proposed.any { it == nextB }) null else proposed.also { committed += it }
            }, restoreResources = { restored += it })
        assertEquals(setOf(nextA, b), result!!.toSet())
        assertEquals(1, committed.size)
        assertEquals(setOf(nextA, b), restored.single().toSet())
    }
    @Test fun `separate Kotlin outputs retain metadata and reject unexported APIs`() {
        val c = pack("c", source = "package shared; class Value(val number: Int)")
        val b = pack("b", edge("c"), "package shared; fun value() = Value(42)")
        val a = pack("a", edge("b"), "package consumer; fun answer() = shared.value().number")
        val host = listOf(Unit::class.java, org.jetbrains.annotations.NotNull::class.java).map { Path.of(it.protectionDomain.codeSource.location.toURI()) }
        val invocation = ScriptInvocation(ScriptEnvironment.SERVER, "READY", top.katton.api.InvocationReason.HOT_RELOAD, top.katton.api.ReloadCause.COMMAND)
        assertFails { PackRuntime.prepare(listOf(a,b,c), invocation, root.resolve("cache"), host) }
        val exported = b.copy(manifest = b.manifest.copy(packDependencies = listOf(ScriptPackDependency("c", export = true))), codeHash = b.codeHash + "export")
        val prepared = PackRuntime.prepare(listOf(a,exported,c), invocation, root.resolve("cache"), host)
        assertEquals(listOf("c","b","a"), prepared.map { it.pack.manifest.id })
        assertTrue(prepared[1].artifact.files.keys.any { it.endsWith(".kotlin_module") })
    }

    @Test fun `Kotlin and Java in one pack can reference each other`() {
        val initial = pack("mixed", source = "package mixed; class KotlinValue { fun answer() = JavaValue.answer(); fun base() = 42 }")
        Files.writeString(initial.location.resolve("JavaValue.java"), "package mixed; public class JavaValue { public static int answer() { return new KotlinValue().base(); } }")
        val mixed = assertNotNull(ScriptPackManager.scanPackDirectory(initial.location, initial.scope))
        val host = listOf(Unit::class.java, org.jetbrains.annotations.NotNull::class.java).map { Path.of(it.protectionDomain.codeSource.location.toURI()) }
        val artifact = PackCompiler.compile(mixed, root.resolve("cache"), host, emptyList())
        PackClassLoader(artifact, PrivateLibraries(emptyList(), emptyMap()), javaClass.classLoader, emptyList()).use { loader ->
            assertEquals(42, loader.loadClass("mixed.JavaValue").getMethod("answer").invoke(null))
        }
    }

    @Test fun `entrypoint failure preserves old bindings and independent consumers are untouched`() {
        val b = pack("base", source = "package base; class Value")
        val a = pack("left", edge("base"), "package left; class Value")
        val c = pack("right", edge("base"), "package right; class Value")
        val host = listOf(Unit::class.java, org.jetbrains.annotations.NotNull::class.java).map { Path.of(it.protectionDomain.codeSource.location.toURI()) }
        val invocation = ScriptInvocation(ScriptEnvironment.SERVER, "READY", top.katton.api.InvocationReason.HOT_RELOAD, top.katton.api.ReloadCause.COMMAND)
        val values = mutableMapOf<String, Long>()
        val calls = mutableMapOf<String, Int>()
        var fail: String? = null
        val activate: (PackInstance, ScriptInvocation) -> Boolean = { instance, _ ->
            val id = instance.preparation.pack.manifest.id
            ScriptExecutionContext.withOwner(instance.ownerPrefix + "READY:entry") {
                ManagedResources.put(values, id, instance.generation)
                calls[id] = calls.getOrDefault(id, 0) + 1
                check(id != fail) { "injected failure" }
            }
            true
        }
        fun apply(packs: List<ScriptPack>) = PackRuntime.transaction {
            PackRuntime.execute(PackRuntime.prepare(packs, invocation, root.resolve("cache"), host), invocation,
                packs.mapTo(hashSetOf()) { it.syncId }, { javaClass.classLoader }, activate)
        }
        try {
            assertTrue(apply(listOf(a,b,c)))
            val original = values.toMap()
            val changedA = a.copy(codeHash = a.codeHash + "changed")
            assertTrue(apply(listOf(changedA,b,c)))
            assertNotEquals(original["left"], values["left"])
            assertEquals(1, calls["base"])
            assertEquals(1, calls["right"])
            val accepted = values.toMap()
            fail = "base"
            assertTrue(apply(listOf(changedA,b.copy(codeHash = b.codeHash + "changed"),c)))
            assertEquals(accepted, values)
            assertEquals(2, calls["left"])
            assertEquals(1, calls["right"])
        } finally { PackRuntime.clear(ScriptEnvironment.SERVER, setOf(ScriptPackScope.WORLD)) }
    }

    @Test fun `multi release private classes compare Java 25 effective bytes and retain resources`() {
        val initial = pack("multi")
        val libs = Files.createDirectory(initial.location.resolve("libs"))
        fun jar(name: String, entries: Map<String, ByteArray>, multi: Boolean = false) {
            val manifest = java.util.jar.Manifest().apply {
                mainAttributes.putValue("Manifest-Version", "1.0")
                if (multi) mainAttributes.putValue("Multi-Release", "true")
            }
            JarOutputStream(Files.newOutputStream(libs.resolve(name)), manifest).use { output ->
                entries.forEach { (entry, bytes) -> output.putNextEntry(JarEntry(entry)); output.write(bytes); output.closeEntry() }
            }
        }
        jar("a.jar", mapOf("example/Value.class" to byteArrayOf(1), "META-INF/versions/25/example/Value.class" to byteArrayOf(2),
            "META-INF/versions/26/example/Value.class" to byteArrayOf(3), "a.txt" to byteArrayOf(1)), true)
        jar("b.jar", mapOf("example/Value.class" to byteArrayOf(2), "b.txt" to byteArrayOf(2)))
        val scanned = assertNotNull(ScriptPackManager.scanPackDirectory(initial.location, initial.scope))
        val libraries = PrivateLibraries.prepare(scanned, root.resolve("cache"))
        assertEquals(2, libraries.paths.size)
        assertContentEquals(byteArrayOf(2), libraries.classes["example.Value"])
        jar("b.jar", mapOf("example/Value.class" to byteArrayOf(9)))
        assertFailsWith<IllegalArgumentException> {
            PrivateLibraries.prepare(ScriptPackManager.scanPackDirectory(initial.location, initial.scope)!!, root.resolve("cache"))
        }
    }

    @Test fun `ZIP rejects traversal portable duplicates and actual expansion overflow`() {
        fun archive(name: String, entries: Map<String, ByteArray>): Path {
            val path = root.resolve(name)
            ZipOutputStream(Files.newOutputStream(path)).use { output ->
                output.putNextEntry(ZipEntry("manifest.json")); output.write("{\"id\":\"zip\",\"dependencies\":[]}".toByteArray()); output.closeEntry()
                entries.forEach { (entry, bytes) -> output.putNextEntry(ZipEntry(entry)); output.write(bytes); output.closeEntry() }
            }
            return path
        }
        assertNull(ScriptPackManager.scanPackZip(archive("traversal.zip", mapOf("../escape.kt" to byteArrayOf())), ScriptPackScope.WORLD))
        assertNull(ScriptPackManager.scanPackZip(archive("duplicates.zip", mapOf("A.kt" to byteArrayOf(), "a.kt" to byteArrayOf())), ScriptPackScope.WORLD))
        assertNull(ScriptPackManager.scanPackZip(archive("file-parent.zip", mapOf("assets" to byteArrayOf(), "assets/value.json" to byteArrayOf())), ScriptPackScope.WORLD))
        assertNull(ScriptPackManager.scanPackZip(archive("expanded.zip", mapOf("ignored.bin" to ByteArray(ScriptPackFileLimits.MAX_FILE_BYTES + 1))), ScriptPackScope.WORLD))
    }

    @Test fun `candidate activation cannot bypass a paused old callback`() {
        assertTrue(ManagedResources.pause(listOf("old:"), 0))
        try {
            ManagedResources.activation("new:") {
                assertFalse(ManagedResources.enter("old:callback"))
                assertTrue(ManagedResources.enter("new:entry"))
                ManagedResources.leave("new:entry")
            }
        } finally { ManagedResources.resume(listOf("old:")) }
    }

    @Test fun `declared host loader and service resources take their declared identity`() {
        val source = root.resolve("HostValue.java")
        Files.writeString(source, "package hosted; public class HostValue {}")
        val classes = Files.createDirectories(root.resolve("host-classes"))
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",classes.toString(),source.toString()))
        fun jar(name: String): Path {
            val path = root.resolve("$name.jar")
            JarOutputStream(Files.newOutputStream(path)).use { output ->
                output.putNextEntry(JarEntry("hosted/HostValue.class")); output.write(Files.readAllBytes(classes.resolve("hosted/HostValue.class"))); output.closeEntry()
                output.putNextEntry(JarEntry("META-INF/services/hosted.Service")); output.write(name.toByteArray()); output.closeEntry()
            }
            return path
        }
        val otherJar = jar("undeclared")
        val declaredJar = jar("declared")
        java.net.URLClassLoader(arrayOf(otherJar.toUri().toURL()), javaClass.classLoader).use { parent ->
            java.net.URLClassLoader(arrayOf(declaredJar.toUri().toURL()), javaClass.classLoader).use { declared ->
                val host = PackHostClassLoader(parent, emptyList(), listOf(ResolvedScriptDependency("declared", "1", listOf(declaredJar), declared)))
                assertSame(declared.loadClass("hosted.HostValue"), host.loadClass("hosted.HostValue"))
                assertNotSame(parent.loadClass("hosted.HostValue"), host.loadClass("hosted.HostValue"))
                assertEquals(listOf("declared"), host.getResources("META-INF/services/hosted.Service").toList().map { it.openStream().use { stream -> stream.readBytes().toString(Charsets.UTF_8) } })
            }
        }
    }

    @Test fun `private jars remain usable internally but are absent from dependency exports`() {
        val initial = pack("library")
        val source = root.resolve("PrivateValue.java")
        Files.writeString(source, "package hidden; public class PrivateValue { public static int value = 42; }")
        val classes = Files.createDirectories(root.resolve("private-classes"))
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",classes.toString(),source.toString()))
        Files.createDirectory(initial.location.resolve("libs"))
        val jar = initial.location.resolve("libs/private.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            output.putNextEntry(JarEntry("hidden/PrivateValue.class"))
            output.write(Files.readAllBytes(classes.resolve("hidden/PrivateValue.class")))
            output.closeEntry()
            output.putNextEntry(JarEntry("unique.txt")); output.write(byteArrayOf(1)); output.closeEntry()
        }
        val pack = assertNotNull(ScriptPackManager.scanPackDirectory(initial.location, initial.scope))
        val libraries = PrivateLibraries.prepare(pack, root.resolve("libs-cache"))
        val artifact = PackArtifact("empty", root.resolve("empty.jar"), emptyMap())
        PackClassLoader(artifact, libraries, javaClass.classLoader, emptyList()).use { dependency ->
            assertEquals(42, dependency.loadClass("hidden.PrivateValue").getField("value").getInt(null))
            PackClassLoader(artifact, PrivateLibraries(emptyList(), emptyMap()), javaClass.classLoader, listOf(dependency)).use { consumer ->
                assertFailsWith<ClassNotFoundException> { consumer.loadClass("hidden.PrivateValue") }
                assertNull(consumer.getResource("unique.txt"))
            }
            PackClassLoader(artifact, libraries, javaClass.classLoader, emptyList()).use { separate ->
                dependency.loadClass("hidden.PrivateValue").getField("value").setInt(null, 7)
                assertEquals(42, separate.loadClass("hidden.PrivateValue").getField("value").getInt(null))
            }
        }
    }

    @Test fun `private copies isolate state while consumers share dependency types`() {
        val source = root.resolve("Counter.java")
        Files.writeString(source, "package fixture; public class Counter { public static int value; }")
        val out = Files.createDirectories(root.resolve("java"))
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-d",out.toString(),source.toString()))
        val bytes = Files.readAllBytes(out.resolve("fixture/Counter.class"))
        val artifact = PackArtifact("counter", root.resolve("counter.jar"), mapOf("fixture/Counter.class" to bytes))
        val empty = PackArtifact("empty", root.resolve("empty.jar"), emptyMap())
        val libs = PrivateLibraries(emptyList(), emptyMap())
        val host = javaClass.classLoader
        PackClassLoader(artifact, libs, host, emptyList()).use { dependency ->
            PackClassLoader(empty, libs, host, listOf(dependency)).use { a ->
                PackClassLoader(empty, libs, host, listOf(dependency)).use { b ->
                    val type = a.loadClass("fixture.Counter")
                    assertSame(type, b.loadClass("fixture.Counter"))
                    type.getField("value").setInt(null, 42)
                    assertEquals(42, b.loadClass("fixture.Counter").getField("value").getInt(null))
                    PackClassLoader(artifact, libs, host, emptyList()).use { next ->
                        assertEquals(0, next.loadClass("fixture.Counter").getField("value").getInt(null))
                    }
                }
            }
        }
    }
}
