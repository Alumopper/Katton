package top.katton.dev

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class DevBridgeTest {
    @TempDir lateinit var directory: Path
    @Test fun `target token changes on first world entry and every server lifetime`() {
        val target = DevTargetSession()
        val menu = target.token(null)
        val first = Any(); val firstId = target.token(first)
        assertNotEquals(menu, firstId)
        assertEquals(firstId, target.token(first))
        target.invalidate(); assertNotEquals(firstId, target.token(first))
        val secondId = target.token(Any()); assertNotEquals(firstId, secondId)
        assertNotEquals(secondId, target.token(null))
    }
    @Test fun `authenticated loopback protocol deduplicates operations and removes discovery on close`() {
        val calls = AtomicInteger()
        val result = CompletableFuture<JsonObject>()
        val runtime = object : DevRuntime {
            override fun snapshot() = json("worldId" to "world")
            override fun preflight(request: JsonObject) = json("status" to "READY")
            override fun apply(request: JsonObject): CompletableFuture<JsonObject> { calls.incrementAndGet(); return result }
        }
        val bridge = DevBridgeServer(runtime, directory)
        try {
            val record = JsonParser.parseString(Files.readString(directory.resolve("${bridge.sessionId}.json"))).asJsonObject
            val token = record.string("token")
            fun request(path: String, body: JsonObject? = null, authentication: String = token, origin: Boolean = false): Pair<Int, JsonObject> {
                val connection = URI("http://127.0.0.1:${bridge.port}/v1/$path").toURL().openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $authentication")
                if (origin) connection.setRequestProperty("Origin", "http://example.com")
                connection.connectTimeout = 3000; connection.readTimeout = 3000
                if (body != null) {
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val status = connection.responseCode
                val text = (if (status >= 400) connection.errorStream else connection.inputStream).bufferedReader().use { it.readText() }
                connection.disconnect()
                return status to JsonParser.parseString(text).asJsonObject
            }
            assertEquals(401, request("instance", authentication = "wrong").first)
            assertEquals(bridge.sessionId, request("instance").second.string("sessionId"))
            val input = json("sessionId" to bridge.sessionId, "operationId" to UUID.randomUUID().toString(), "revision" to "revision")
            assertEquals("READY", request("preflight", input).second.string("status"))
            assertEquals(202, request("apply", input).first)
            assertEquals(200, request("apply", input).first)
            assertEquals(1, calls.get())
            val other = input.deepCopy().apply { addProperty("operationId", UUID.randomUUID().toString()) }
            assertEquals(409, request("apply", other).first)
            result.complete(json("status" to "PARTIAL"))
            assertEquals("PARTIAL", request("operations/${input.string("operationId")}").second.string("status"))
            assertEquals(400, request("apply", input.deepCopy().apply { addProperty("sessionId", "stale") }).first)
        } finally { bridge.close() }
        assertFalse(Files.exists(directory.resolve("${bridge.sessionId}.json")))
    }
}
