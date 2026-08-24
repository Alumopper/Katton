package top.katton.engine

import org.junit.jupiter.api.io.TempDir
import top.katton.api.InvocationReason
import top.katton.api.ReloadCause
import top.katton.api.ServerPhase
import top.katton.pack.ScriptPackManager
import top.katton.pack.ScriptPackScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ScriptEngineSnapshotTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `Kotlin compilation consumes the scanned source snapshot`() {
        val packDirectory = Files.createDirectory(temporaryDirectory.resolve("snapshot"))
        Files.writeString(
            packDirectory.resolve("manifest.json"),
            """{"id":"snapshot","dependencies":[]}"""
        )
        val sourcePath = packDirectory.resolve("main.kt")
        Files.writeString(sourcePath, "package snapshot\nval capturedAnswer = 42")
        val scanned = ScriptPackManager.scanPackDirectory(packDirectory, ScriptPackScope.WORLD)
            ?: error("Expected test pack to scan")

        // If ScriptEngine reopens sourcePath, this invalid edit makes compilation
        // fail. The accepted pack must instead compile its already hashed bytes.
        Files.writeString(sourcePath, "this is deliberately invalid Kotlin")

        assertTrue(
            ScriptEngine.compileAndExecuteAll(
                listOf(scanned),
                ScriptInvocation(
                    environment = ScriptEnvironment.SERVER,
                    phaseName = ServerPhase.BOOTSTRAP.name,
                    reason = InvocationReason.HOT_RELOAD,
                    cause = ReloadCause.COMMAND
                )
            )
        )
    }
}
