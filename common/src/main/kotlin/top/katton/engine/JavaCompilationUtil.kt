@file:Suppress("unused")

package top.katton.engine

import org.slf4j.LoggerFactory
import top.katton.pack.ScriptPackScriptFile
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.tools.JavaCompiler
import javax.tools.ToolProvider
import kotlin.io.path.bufferedReader
import kotlin.io.path.createTempDirectory

/**
 * Compiles `.java` source files into a `.jar` with caching based on content hash.
 *
 * **Classpath**: obtained from [ManagementFactory.getRuntimeMXBean().getClassPath()],
 * which includes ALL game jars, mod jars, and libraries — same as what Kotlin's
 * `dependenciesFromCurrentContext(wholeClasspath = true)` sees.
 *
 * **Caching**: the compiled jar is stored at `<cacheDir>/java-<sha256-hex>.jar`.
 * If the file exists, javac is skipped entirely.
 */
object JavaCompilationUtil {

    private val LOGGER = LoggerFactory.getLogger(JavaCompilationUtil::class.java)
    private val javac: JavaCompiler? = ToolProvider.getSystemJavaCompiler()

    /** Full runtime classpath, computed once. */
    private val runtimeClasspath: String by lazy {
        ManagementFactory.getRuntimeMXBean().classPath
    }

    /**
     * Compiles the given `.java` source files. Returns `null` on failure.
     *
     * @param javaFiles the Java source files to compile
     * @param cacheDir  where to store (and look up) cached compilation jars
     */
    fun compileToJar(javaFiles: List<ScriptPackScriptFile>, cacheDir: Path?): Path? {
        if (javaFiles.isEmpty()) return null
        val compiler = javac ?: run {
            LOGGER.warn("JavaCompiler not available — is a JDK (not JRE) being used?")
            return null
        }

        val hash = computeJavaHash(javaFiles)
        val cachedJar = cacheDir?.resolve("java-$hash.jar")
        if (cachedJar != null && Files.isRegularFile(cachedJar)) {
            LOGGER.info("Reusing cached Java compilation jar {}", cachedJar)
            return cachedJar
        }

        val tempDir = runCatching { createTempDirectory("katton-java-") }.getOrElse {
            LOGGER.warn("Failed to create temp dir for Java compilation", it)
            return null
        }

        val result = try {
            val fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)

            // Write .java files to temp dir so javac can resolve package dirs
            for (file in javaFiles) {
                val target = tempDir.resolve(file.relativePath)
                Files.createDirectories(target.parent)
                Files.write(target, file.bytes)
            }

            val sources = javaFiles.map { tempDir.resolve(it.relativePath).toFile() }
            val units = fileManager.getJavaFileObjectsFromFiles(sources)

            val classOutput = tempDir.resolve("classes")
            Files.createDirectories(classOutput)

            val options = listOf(
                "-classpath", runtimeClasspath,
                "-d", classOutput.toString(),
                "-source", System.getProperty("java.specification.version", "25")
            )

            val task = compiler.getTask(null, fileManager, null, options, null, units)
            task.setLocale(java.util.Locale.ROOT)

            if (!task.call()) {
                LOGGER.warn("Java compilation failed for {} source files", javaFiles.size)
                null
            } else {
                packToJar(classOutput, cacheDir?.resolve("java-$hash.jar"))
            }
        } catch (e: Exception) {
            LOGGER.warn("Java compilation exception", e)
            null
        } finally {
            // Cleanup temp dir
            runCatching { Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }

        return result
    }

    /**
     * SHA-256 of all Java source contents, used for cache key.
     */
    private fun computeJavaHash(javaFiles: List<ScriptPackScriptFile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        javaFiles.sortedBy { it.relativePath }.forEach { f ->
            digest.update(f.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(f.bytes)
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Packs compiled `.class` files into a `.jar`.
     */
    private fun packToJar(classDir: Path, outputPath: Path?): Path? {
        if (outputPath == null) return null
        runCatching {
            Files.createDirectories(outputPath.parent)
            val jarFs = FileSystems.newFileSystem(
                java.net.URI.create("jar:${outputPath.toUri()}"),
                mapOf("create" to "true")
            )
            jarFs.use { fs ->
                Files.walk(classDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                        .forEach { classFile ->
                            val relative = "classDir".let { classDir.relativize(classFile).toString().replace('\\', '/') }
                            val target = fs.getPath(relative)
                            Files.createDirectories(target.parent)
                            Files.copy(classFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        }
                }
            }
            LOGGER.info("Packed Java classes into {}", outputPath)
        }.onFailure {
            LOGGER.warn("Failed to pack Java jar", it)
        }
        return if (Files.isRegularFile(outputPath)) outputPath else null
    }
}
