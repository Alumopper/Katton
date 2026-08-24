@file:Suppress("unused")

package top.katton.engine

import org.slf4j.LoggerFactory
import top.katton.pack.ScriptPackScriptFile
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
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

    sealed interface Result {
        data object NoSources : Result
        data class Success(val jar: Path) : Result
        data class Failure(val detail: String) : Result
    }

    private val LOGGER = LoggerFactory.getLogger(JavaCompilationUtil::class.java)
    private val javac: JavaCompiler? = ToolProvider.getSystemJavaCompiler()

    /** Full runtime classpath, computed once. */
    private val runtimeClasspath: String by lazy {
        ManagementFactory.getRuntimeMXBean().classPath
    }

    /**
     * Compiles the given `.java` source files and distinguishes absence from failure.
     *
     * @param javaFiles the Java source files to compile
     * @param cacheDir  where to store (and look up) cached compilation jars
     */
    fun compileToJar(
        javaFiles: List<ScriptPackScriptFile>,
        cacheDir: Path?,
        additionalClasspath: List<Path> = emptyList(),
        dependencyFingerprints: List<String> = emptyList()
    ): Result {
        if (javaFiles.isEmpty()) return Result.NoSources
        val compiler = javac ?: run {
            LOGGER.warn("JavaCompiler not available — is a JDK (not JRE) being used?")
            ScriptIssueReporter.report(
                title = "Katton Java script compilation failed",
                detail = "JavaCompiler is not available. Run Katton with a JDK instead of a JRE."
            )
            return Result.Failure("JavaCompiler is not available. Run Katton with a JDK instead of a JRE.")
        }

        val hash = computeJavaHash(javaFiles, dependencyFingerprints)
        val cachedJar = cacheDir?.resolve("java-$hash.jar")
        if (cachedJar != null && isValidJar(cachedJar)) {
            LOGGER.info("Reusing cached Java compilation jar {}", cachedJar)
            return Result.Success(cachedJar)
        }
        cachedJar?.takeIf(Files::exists)?.let { corruptJar ->
            LOGGER.warn("Discarding corrupt Java compilation cache {}", corruptJar)
            runCatching { Files.deleteIfExists(corruptJar) }
        }

        val tempDir = runCatching { createTempDirectory("katton-java-") }.getOrElse {
            LOGGER.warn("Failed to create temp dir for Java compilation", it)
            return Result.Failure("Failed to create a temporary directory for Java compilation: ${it.message}")
        }

        val result = try {
            val diagnostics = DiagnosticCollector<JavaFileObject>()
            compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8).use { fileManager ->
                val sourceRoot = tempDir.resolve("sources")
                val sources = javaFiles.mapIndexed { index, file ->
                    // Each pack source gets a distinct staging root. Two packs may
                    // legitimately use the same relative path; silently overwriting
                    // one here would compile different code from the scanned snapshot.
                    val target = sourceRoot.resolve(index.toString()).resolve(file.relativePath).normalize()
                    require(target.startsWith(sourceRoot)) { "Java source path escaped staging root: ${file.relativePath}" }
                    Files.createDirectories(target.parent)
                    Files.write(target, file.bytes)
                    target.toFile()
                }
                val units = fileManager.getJavaFileObjectsFromFiles(sources)

                val classOutput = tempDir.resolve("classes")
                Files.createDirectories(classOutput)

                val effectiveClasspath = buildList {
                    add(runtimeClasspath)
                    addAll(additionalClasspath.map(Path::toString))
                }.filter(String::isNotBlank).joinToString(java.io.File.pathSeparator)
                val options = listOf(
                    "-classpath", effectiveClasspath,
                    "-d", classOutput.toString(),
                    "-source", System.getProperty("java.specification.version", "25")
                )

                val task = compiler.getTask(null, fileManager, diagnostics, options, null, units)
                task.setLocale(java.util.Locale.ROOT)

                if (!task.call()) {
                    LOGGER.warn("Java compilation failed for {} source files", javaFiles.size)
                    ScriptIssueReporter.report(
                        title = "Katton Java script compilation failed",
                        detail = buildString {
                            appendLine("Files:")
                            javaFiles.sortedBy { it.relativePath }.forEach { appendLine("- ${it.relativePath}") }
                            appendLine()
                            append(formatJavaDiagnostics(diagnostics))
                        }
                    )
                    Result.Failure(formatJavaDiagnostics(diagnostics))
                } else {
                    val jar = packToJar(classOutput, cacheDir?.resolve("java-$hash.jar"))
                    if (jar == null) {
                        Result.Failure("Java classes compiled, but the cache jar could not be created")
                    } else {
                        Result.Success(jar)
                    }
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Java compilation exception", e)
            ScriptIssueReporter.report(
                title = "Katton Java script compilation failed",
                detail = e.stackTraceToString()
            )
            Result.Failure(e.message ?: e.javaClass.name)
        } finally {
            // Cleanup temp dir
            runCatching {
                Files.walk(tempDir).use { files ->
                    files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }

        return result
    }

    private fun formatJavaDiagnostics(diagnostics: DiagnosticCollector<JavaFileObject>): String {
        return diagnostics.diagnostics.joinToString("\n") { diagnostic ->
            val source = diagnostic.source?.name ?: "<unknown>"
            val line = diagnostic.lineNumber.takeIf { it >= 0 }?.toString() ?: "?"
            val column = diagnostic.columnNumber.takeIf { it >= 0 }?.toString() ?: "?"
            "${diagnostic.kind}: $source:$line:$column: ${diagnostic.getMessage(java.util.Locale.ROOT)}"
        }.ifBlank { "javac did not provide diagnostics." }
    }

    /**
     * SHA-256 of all Java source contents, used for cache key.
     */
    private fun computeJavaHash(
        javaFiles: List<ScriptPackScriptFile>,
        dependencyFingerprints: List<String>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateFramed("katton-java-cache-v2".toByteArray(StandardCharsets.UTF_8))
        digest.updateInt(javaFiles.size)
        javaFiles.sortedBy { it.relativePath }.forEach { f ->
            digest.updateFramed(f.relativePath.toByteArray(StandardCharsets.UTF_8))
            digest.updateFramed(f.bytes)
        }
        digest.updateInt(dependencyFingerprints.size)
        dependencyFingerprints.sorted().forEach { fingerprint ->
            digest.updateFramed(fingerprint.toByteArray(StandardCharsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun MessageDigest.updateFramed(bytes: ByteArray) {
        updateInt(bytes.size)
        update(bytes)
    }

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr 24).toByte())
        update((value ushr 16).toByte())
        update((value ushr 8).toByte())
        update(value.toByte())
    }

    /**
     * Packs compiled `.class` files into a `.jar`.
     */
    @Synchronized
    private fun packToJar(classDir: Path, outputPath: Path?): Path? {
        if (outputPath == null) return null
        if (isValidJar(outputPath)) return outputPath

        var temporaryJar: Path? = null
        runCatching {
            Files.createDirectories(outputPath.parent)
            val temp = Files.createTempFile(outputPath.parent, ".katton-java-", ".jar.tmp")
            temporaryJar = temp
            JarOutputStream(
                Files.newOutputStream(
                    temp,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
            ).use { jar ->
                Files.walk(classDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                        .sorted()
                        .forEach { classFile ->
                            val relative = classDir.relativize(classFile).toString().replace('\\', '/')
                            jar.putNextEntry(JarEntry(relative))
                            Files.copy(classFile, jar)
                            jar.closeEntry()
                        }
                }
            }
            try {
                Files.move(temp, outputPath, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, outputPath)
            }
            LOGGER.info("Packed Java classes into {}", outputPath)
        }.onFailure {
            LOGGER.warn("Failed to pack Java jar", it)
        }.also {
            temporaryJar?.let { path -> runCatching { Files.deleteIfExists(path) } }
        }
        return outputPath.takeIf(::isValidJar)
    }

    private fun isValidJar(path: Path): Boolean =
        Files.isRegularFile(path) && runCatching { JarFile(path.toFile()).use { it.size() } }.isSuccess
}
