package top.katton.engine

import org.junit.jupiter.api.io.TempDir
import top.katton.pack.ScriptPackScriptFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JavaCompilationUtilTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `compiled Java cache is a valid jar and repairs corrupt entries`() {
        val source = ScriptPackScriptFile(
            relativePath = "example/Generated.java",
            absolutePath = tempDirectory.resolve("Generated.java"),
            bytes = """
                package example;
                public final class Generated {
                    public static int answer() { return 42; }
                }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        )

        val first = assertIs<JavaCompilationUtil.Result.Success>(
            JavaCompilationUtil.compileToJar(listOf(source), tempDirectory)
        ).jar
        assertTrue(hasEntry(first, "example/Generated.class"))

        Files.write(first, byteArrayOf(1, 2, 3))
        val repaired = assertIs<JavaCompilationUtil.Result.Success>(
            JavaCompilationUtil.compileToJar(listOf(source), tempDirectory)
        ).jar
        assertTrue(hasEntry(repaired, "example/Generated.class"))
    }

    @Test
    fun `invalid Java source is a failure rather than no sources`() {
        val invalid = ScriptPackScriptFile(
            relativePath = "Broken.java",
            absolutePath = tempDirectory.resolve("Broken.java"),
            bytes = "public class Broken { this is invalid }".toByteArray(StandardCharsets.UTF_8)
        )

        assertIs<JavaCompilationUtil.Result.Failure>(
            JavaCompilationUtil.compileToJar(listOf(invalid), tempDirectory)
        )
        assertIs<JavaCompilationUtil.Result.NoSources>(
            JavaCompilationUtil.compileToJar(emptyList(), tempDirectory)
        )
    }

    private fun hasEntry(jarPath: Path, entryName: String): Boolean =
        JarFile(jarPath.toFile()).use { jar -> jar.getJarEntry(entryName) != null }
}
