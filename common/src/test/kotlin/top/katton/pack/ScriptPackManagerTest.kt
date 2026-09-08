package top.katton.pack

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptPackManagerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `directory scan classifies content roots without duplicate executable sources`() {
        val pack = createPack("classified")
        write(pack.resolve("main.kt"), "fun main() = Unit")
        write(pack.resolve("Helper.java"), "final class Helper {}")
        write(pack.resolve("assets/example/generated.java"), "not executable")
        write(pack.resolve("data/example/functions/generated.kt"), "not executable")

        val scanned = ScriptPackManager.scanPackDirectory(pack, ScriptPackScope.WORLD)
            ?: error("Expected pack to be scanned")

        assertEquals(listOf("main.kt"), scanned.scripts.map { it.relativePath })
        assertEquals(
            setOf(
                "main.kt",
                "Helper.java",
                "assets/example/generated.java",
                "data/example/functions/generated.kt"
            ),
            scanned.contentFiles.mapTo(linkedSetOf()) { it.relativePath }
        )
        assertEquals(scanned.contentFiles.size, scanned.contentFiles.map { it.relativePath }.distinct().size)
    }

    @Test
    fun `oversized source is rejected before it is read into memory`() {
        val pack = createPack("oversized")
        val source = pack.resolve("huge.kt")
        Files.newByteChannel(
            source,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        ).use { channel ->
            channel.position(ScriptPackFileLimits.MAX_FILE_BYTES.toLong())
            channel.write(java.nio.ByteBuffer.wrap(byteArrayOf(1)))
        }

        assertNull(ScriptPackManager.scanPackDirectory(pack, ScriptPackScope.WORLD))
    }

    @Test
    fun `symbolic link in directory pack is rejected`() {
        val pack = createPack("linked")
        val outside = temporaryDirectory.resolve("outside.kt")
        write(outside, "fun outside() = Unit")
        val link = pack.resolve("linked.kt")
        val linkCreated = try {
            Files.createSymbolicLink(link, outside)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: FileSystemException) {
            false
        } catch (_: SecurityException) {
            false
        }
        assumeTrue(linkCreated, "Symbolic links are unavailable on this test host")

        assertNull(ScriptPackManager.scanPackDirectory(pack, ScriptPackScope.WORLD))
    }

    @Test
    fun `archive entry traversal is rejected`() {
        val jar = temporaryDirectory.resolve("unsafe.jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { output ->
            output.putNextEntry(ZipEntry("../escape.class"))
            output.write(byteArrayOf(1, 2, 3))
            output.closeEntry()
        }

        assertNull(ScriptPackManager.scanPackJar(jar, ScriptPackScope.GLOBAL))
    }

    @Test
    fun `empty archive directories count toward the entry limit`() {
        val jar = temporaryDirectory.resolve("directory-flood.jar")
        val testEntryLimit = 32
        ZipOutputStream(Files.newOutputStream(jar)).use { output ->
            repeat(testEntryLimit + 1) { index ->
                output.putNextEntry(ZipEntry("directory-$index/"))
                output.closeEntry()
            }
        }

        assertNull(
            ScriptPackManager.scanPackJar(
                jar,
                ScriptPackScope.GLOBAL,
                maximumArchiveEntries = testEntryLimit
            )
        )
    }

    @Test
    fun `ignored directory files count toward the traversal limit`() {
        val pack = createPack("directory-flood")
        val testEntryLimit = 32
        repeat(testEntryLimit) { index ->
            write(pack.resolve("ignored-$index.txt"), "ignored")
        }

        assertNull(
            ScriptPackManager.scanPackDirectory(
                pack,
                ScriptPackScope.WORLD,
                maximumDiscoveredEntries = testEntryLimit
            )
        )
    }

    @Test
    fun `re-signing unchanged source does not invalidate compilation hash`() {
        val pack = createPack("resigned")
        write(pack.resolve("main.kt"), "fun main() = Unit")
        val unsigned = ScriptPackManager.scanPackDirectory(pack, ScriptPackScope.WORLD)
            ?: error("Expected unsigned pack")

        write(
            pack.resolve("manifest.json"),
            """{"id":"resigned","dependencies":[],"signature":{"algorithm":"Ed25519","payloadVersion":2,"keyId":"key","publicKey":"AA==","signature":"AA=="}}"""
        )
        val signed = ScriptPackManager.scanPackDirectory(pack, ScriptPackScope.WORLD)
            ?: error("Expected signed pack")

        assertEquals(unsigned.codeHash, signed.codeHash)
        assertNotEquals(unsigned.hash, signed.hash)
    }

    @Test
    fun `top level executable jar requires source migration`() {
        val jar = temporaryDirectory.resolve("compiled.jar")
        writeJar(jar, "Entry.class", byteArrayOf(1, 2, 3))
        assertNull(ScriptPackManager.scanPackJar(jar, ScriptPackScope.GLOBAL))
    }

    @Test
    fun `cached source pack preserves the server fallback id`() {
        val originalDirectory = Files.createDirectory(temporaryDirectory.resolve("fallback-id"))
        write(originalDirectory.resolve("manifest.json"), """{"dependencies":[]}""")
        write(originalDirectory.resolve("main.kt"), "fun cachedSource() = Unit")
        val original = assertNotNull(
            ScriptPackManager.scanPackDirectory(originalDirectory, ScriptPackScope.GLOBAL)
        )

        val container = Files.createDirectory(temporaryDirectory.resolve("encoded-cache-name"))
        write(container.resolve("manifest.json"), original.manifestJson)
        write(container.resolve("main.kt"), "fun cachedSource() = Unit")
        val cached = assertNotNull(
            ScriptPackManager.scanCachedPackContainer(container, original.syncId, original.hash)
        )

        assertEquals("fallback-id", cached.manifest.id)
        assertEquals(original.syncId, cached.syncId)
    }

    @Test
    fun `private jar snapshot does not reopen the original library`() {
        val dir = Files.createDirectory(temporaryDirectory.resolve("private"))
        write(dir.resolve("manifest.json"), """{"id":"private","dependencies":[]}""")
        Files.createDirectory(dir.resolve("libs"))
        val jar = dir.resolve("libs/helper.jar")
        writeJar(jar, "original.txt", byteArrayOf(1,2,3))
        val scanned = assertNotNull(ScriptPackManager.scanPackDirectory(dir, ScriptPackScope.WORLD))
        writeJar(jar, "replacement.txt", byteArrayOf(9))
        val library = top.katton.engine.PrivateLibraries.prepare(scanned, temporaryDirectory.resolve("libraries"))
        JarFile(library.paths.single().toFile()).use { snapshot ->
            assertNotNull(snapshot.getJarEntry("original.txt"))
            assertNull(snapshot.getJarEntry("replacement.txt"))
        }
    }

    @Test
    fun `pack hash framing distinguishes embedded separators from extra files`() {
        val dummyPath = temporaryDirectory.resolve("dummy")
        val oneFile = listOf(
            ScriptPackScriptFile("a", dummyPath, "x\u0000b\u0000y".toByteArray())
        )
        val twoFiles = listOf(
            ScriptPackScriptFile("a", dummyPath, "x".toByteArray()),
            ScriptPackScriptFile("b", dummyPath, "y".toByteArray())
        )

        assertNotEquals(
            ScriptPackManager.computeScriptHash("{}", oneFile),
            ScriptPackManager.computeScriptHash("{}", twoFiles)
        )
    }

    @Test
    fun `published discovery metadata does not replace the validated executable snapshot`() {
        val directory = createPack("transactional")
        write(directory.resolve("main.kt"), "fun version() = 1")
        val previous = assertNotNull(ScriptPackManager.scanPackDirectory(directory, ScriptPackScope.WORLD))
        write(directory.resolve("main.kt"), "fun version() = 2")
        val candidate = assertNotNull(ScriptPackManager.scanPackDirectory(directory, ScriptPackScope.WORLD))

        try {
            ScriptPackManager.publishWorldPacks(listOf(candidate), listOf(previous))

            assertEquals(previous.codeHash, ScriptPackManager.collectExecutableWorldPacks().single().codeHash)
            assertEquals(candidate.hash, ScriptPackManager.listLocalPacksForGui(false).single().hash)
        } finally {
            ScriptPackManager.clearWorldDirectory()
        }
    }

    private fun createPack(id: String): Path {
        val pack = Files.createDirectory(temporaryDirectory.resolve(id))
        write(pack.resolve("manifest.json"), """{"id":"$id","dependencies":[]}""")
        return pack
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun writeJar(path: Path, entryName: String, content: ByteArray) {
        Files.deleteIfExists(path)
        ZipOutputStream(Files.newOutputStream(path)).use { output ->
            output.putNextEntry(ZipEntry("manifest.json"))
            output.write("""{"id":"mutable","dependencies":[]}""".toByteArray())
            output.closeEntry()
            output.putNextEntry(ZipEntry(entryName))
            output.write(content)
            output.closeEntry()
        }
    }
}
