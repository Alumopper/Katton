package top.katton.dev

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.katton.engine.PackCompiler
import top.katton.pack.*
import java.nio.file.Path
import kotlin.test.*

class DevDiagnosticsTest {
    @TempDir lateinit var directory: Path
    @Test fun `compiler errors retain original file position and package revision`() {
        DevEvents.enabled = true
        try {
            for ((file, source) in listOf("中文/Broken.kt" to "fun broken() { missingSymbol() }", "Broken.java" to "public class Broken { void run() { missingSymbol(); } }")) {
                val before = DevEvents.cursor()
                val pack = ScriptPackSnapshots("""{"id":"broken","version":"1","dependencies":[]}""", mapOf(file to source.toByteArray()))
                    .toPack(directory.resolve("pack"), ScriptPackScope.WORLD, ScriptPackKind.DIRECTORY)
                assertFails { PackCompiler.compile(pack, directory.resolve("compiled"), emptyList(), emptyList()) }
                val errors = DevEvents.after(before).filter { it.severity == "ERROR" && it.file == file }
                assertTrue(errors.isNotEmpty(), "Missing original source diagnostic for $file")
                assertTrue(errors.all { it.packId == "world:broken" && it.revision == pack.hash && it.line == 1 && it.column!! > 0 })
            }
        } finally { DevEvents.enabled = false }
    }
    @Test fun `event pages bound responses and expose buffer overrun`() {
        DevEvents.enabled = true
        try {
            val before = DevEvents.cursor()
            repeat(600) { DevEvents.emit("INFO", "message $it") }
            val batch = DevEvents.read(before + 1)
            assertTrue(batch.dropped)
            assertEquals(32, batch.entries.size)
            assertEquals(batch.entries.last().cursor, batch.cursor)
            assertTrue(DevEvents.read(batch.cursor).entries.all { it.cursor > batch.cursor })
        } finally { DevEvents.enabled = false }
    }
}
