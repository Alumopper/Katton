package top.katton.dev

import com.google.gson.JsonArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.katton.pack.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.*

class DevDeploymentTest {
    @TempDir lateinit var directory: Path
    private fun request(id: String = "demo", text: String = "fun demo() = 1", file: String = "中文/Example.kt", scope: String = "world") = json(
        "workspaceId" to "workspace", "packs" to listOf(json("scope" to scope,
            "manifest" to """{"id":"$id","version":"1.0","dependencies":[]}""",
            "files" to mapOf(file to Base64.getEncoder().encodeToString(text.toByteArray())))))
    @Test fun `full snapshots support rename deletion and failed generation disk rollback`() {
        val initial = DevDeployment(request(), directory, emptyList())
        initial.install(); initial.finish(setOf("world:demo"))
        val previous = initial.requested.single()
        assertTrue(Files.exists(previous.location.resolve("中文/Example.kt")))
        val next = request(text = "fun renamed() = 2", file = "Renamed.kt")
        next.getAsJsonArray("packs")[0].asJsonObject.addProperty("expectedHash", previous.hash)
        val changed = DevDeployment(next, directory, listOf(previous))
        changed.install()
        assertFalse(Files.exists(previous.location.resolve("中文/Example.kt")))
        assertTrue(Files.exists(previous.location.resolve("Renamed.kt")))
        changed.finish(emptySet())
        assertTrue(Files.exists(previous.location.resolve("中文/Example.kt")))
        assertFalse(Files.exists(previous.location.resolve("Renamed.kt")))
    }
    @Test fun `conflicting workspace and stale disk are rejected`() {
        val initial = DevDeployment(request(), directory, emptyList()); initial.install(); initial.finish(setOf("world:demo"))
        val previous = initial.requested.single()
        val input = request(text = "changed")
        input.getAsJsonArray("packs")[0].asJsonObject.addProperty("expectedHash", previous.hash)
        input.addProperty("workspaceId", "other")
        assertFailsWith<IllegalArgumentException> { DevDeployment(input, directory, listOf(previous)) }
        input.addProperty("workspaceId", "workspace")
        Files.writeString(previous.location.resolve("中文/Example.kt"), "external edit")
        assertFailsWith<IllegalArgumentException> { DevDeployment(input, directory, listOf(previous)) }
    }
    @Test fun `global changes are classified without touching global directories`() {
        val deployment = DevDeployment(request(scope = "global"), directory, emptyList())
        assertEquals(listOf("global:demo"), deployment.restart)
        assertTrue(deployment.changes.isEmpty())
        assertFalse(Files.exists(directory.resolve("kattonpacks")))
    }
    @Test fun `traversal and client cache writes are rejected`() {
        assertFailsWith<IllegalArgumentException> { DevDeployment(request(file = "../outside.kt"), directory, emptyList()) }
        assertFailsWith<IllegalArgumentException> { DevDeployment(request(file = "MANIFEST.JSON"), directory, emptyList()) }
        assertFailsWith<IllegalArgumentException> { DevDeployment(request(file = ".KATTONPACK.STATE.JSON"), directory, emptyList()) }
        assertFails { DevDeployment(request(scope = "server_cache"), directory, emptyList()) }
    }
    @Test fun `independent partial commit restores only failed pack`() {
        val input = request("good")
        input.getAsJsonArray("packs").add(request("bad").getAsJsonArray("packs")[0])
        val deployment = DevDeployment(input, directory, emptyList())
        deployment.install(); deployment.finish(setOf("world:good"))
        assertTrue(Files.exists(deployment.requested.first().location))
        assertFalse(Files.exists(deployment.requested.last().location))
    }
    @Test fun `existing global resource changes use their own scope and rollback`() {
        val game = directory.resolve("game"); Files.createDirectories(game)
        val world = directory.resolve("world"); Files.createDirectories(world)
        val location = game.resolve("kattonpacks/demo"); Files.createDirectories(location)
        val manifest = """{"id":"demo","version":"1.0","dependencies":[]}"""
        Files.writeString(location.resolve("manifest.json"), manifest)
        Files.writeString(location.resolve("settings.json"), "old")
        val previous = ScriptPackSnapshots.directory(location, manifest, 100).toPack(location, ScriptPackScope.GLOBAL, ScriptPackKind.DIRECTORY)
        val input = request(scope = "global", file = "settings.json", text = "new")
        input.getAsJsonArray("packs")[0].asJsonObject.apply { addProperty("expectedHash", previous.hash); addProperty("adopt", true) }
        val change = DevDeployment(input, world, listOf(previous), game)
        assertTrue(change.restart.isEmpty())
        change.install(); assertEquals("new", Files.readString(location.resolve("settings.json")))
        change.finish(emptySet()); assertEquals("old", Files.readString(location.resolve("settings.json")))
        assertFalse(Files.exists(world.resolve("kattonpacks")))
    }
}
