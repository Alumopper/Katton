package top.katton.dev

import com.google.gson.Gson
import com.mojang.logging.LogUtils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

interface DevRuntime {
    fun snapshot(): JsonObject
    fun refresh(): JsonObject = snapshot()
    fun preflight(request: JsonObject): JsonObject
    fun apply(request: JsonObject): CompletableFuture<JsonObject>
    fun dependencies(request: JsonObject): JsonObject = json("dependencies" to emptyList<Any>())
}

/** Protocol v1. HTTP handles transport only; the runtime owns all game-thread scheduling. */
class DevBridgeServer(private val runtime: DevRuntime, private val discovery: Path) : AutoCloseable {
    private val gson = Gson()
    val sessionId: String = UUID.randomUUID().toString()
    private val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private val executor = Executors.newFixedThreadPool(3) { Thread(it, "Katton-DevHttp").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8)
    private val busy = AtomicBoolean()
    private val operations = linkedMapOf<String, JsonObject>()
    private val record = discovery.resolve("$sessionId.json")
    private var closed = false
    val port: Int get() = server.address.port

    init {
        server.executor = executor
        server.createContext("/v1/") { exchange ->
            try { handle(exchange) }
            catch (failure: DevRequestException) { respond(exchange, 400, json("error" to failure.message)) }
            catch (failure: IllegalArgumentException) { respond(exchange, 400, json("error" to (failure.message ?: "Invalid request"))) }
            catch (failure: com.google.gson.JsonParseException) { respond(exchange, 400, json("error" to "Malformed JSON request")) }
            catch (failure: Exception) { respond(exchange, 409, json("error" to (failure.message ?: "Operation unavailable"))) }
            finally { exchange.close() }
        }
        try {
            Files.createDirectories(discovery)
            Files.createFile(record)
            if (Files.getFileStore(record).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(record, PosixFilePermissions.fromString("rw-------"))
            }
            Files.writeString(record, gson.toJson(json("protocol" to 1, "sessionId" to sessionId,
                "pid" to ProcessHandle.current().pid(), "port" to port, "token" to token)))
            server.start()
        } catch (t: Throwable) { close(); throw t }
    }

    private fun handle(exchange: HttpExchange) {
        if (!exchange.remoteAddress.address.isLoopbackAddress || exchange.requestHeaders.containsKey("Origin") ||
            !MessageDigest.isEqual((exchange.requestHeaders.getFirst("Authorization") ?: "").toByteArray(), "Bearer $token".toByteArray())) {
            respond(exchange, 401, json("error" to "Authentication required")); return
        }
        val path = exchange.requestURI.path.removePrefix("/v1/")
        if (exchange.requestMethod == "GET") {
            val value = when {
                path == "instance" -> (if (exchange.requestURI.rawQuery == "refresh=true") runtime.refresh() else runtime.snapshot())
                    .apply { addProperty("protocol", 1); addProperty("sessionId", sessionId) }
                path == "events" -> {
                    val cursor = exchange.requestURI.rawQuery?.removePrefix("after=")?.toLongOrNull() ?: 0
                    val batch = DevEvents.read(cursor)
                    json("cursor" to batch.cursor, "entries" to batch.entries, "dropped" to batch.dropped)
                }
                path.startsWith("operations/") -> synchronized(operations) {
                    operations[path.removePrefix("operations/")]?.deepCopy() ?: error("Unknown operation")
                }
                else -> { respond(exchange, 404, json("error" to "Unknown endpoint")); return }
            }
            respond(exchange, 200, value); return
        }
        require(exchange.requestMethod == "POST" && path in setOf("preflight", "apply", "dependencies")) { "Unsupported request" }
        val limit = 96 * 1024 * 1024
        val bytes = exchange.requestBody.readNBytes(limit + 1)
        require(bytes.size <= limit) { "Deployment exceeds request limit" }
        val request = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(request.string("sessionId") == sessionId) { "Instance session changed; reconnect" }
        if (path == "dependencies") { respond(exchange, 200, runtime.dependencies(request)); return }
        if (path == "preflight") {
            check(!busy.get()) { "An application is already running" }
            respond(exchange, 200, runtime.preflight(request)); return
        }
        // A duplicate operation is queried, never executed again after a transport timeout.
        val id = request.string("operationId")
        require(runCatching { UUID.fromString(id) }.isSuccess) { "operationId must be a UUID" }
        val running = json("operationId" to id, "status" to "APPLYING", "revision" to request.string("revision"))
        synchronized(operations) {
            operations[id]?.let {
                require(it.get("revision") == running.get("revision")) { "Operation ID belongs to another revision" }
                respond(exchange, 200, it.deepCopy()); return
            }
            check(busy.compareAndSet(false, true)) { "An application is already running" }
            operations[id] = running
            while (operations.size > 64) operations.remove(operations.keys.first())
        }
        try {
            runtime.apply(request).whenComplete { result, failure ->
                val finished = result ?: json("status" to "FAILED", "error" to (failure?.message ?: "Application failed"))
                finished.addProperty("operationId", id)
                finished.addProperty("revision", request.string("revision"))
                synchronized(operations) { operations[id] = finished }
                busy.set(false)
            }
        } catch (failure: Exception) {
            synchronized(operations) { operations[id] = json("operationId" to id, "revision" to request.string("revision"), "status" to "FAILED", "error" to failure.message) }
            busy.set(false)
        }
        respond(exchange, 202, synchronized(operations) { operations.getValue(id).deepCopy() })
    }

    private fun respond(exchange: HttpExchange, code: Int, value: JsonObject) {
        val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
        try {
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.responseHeaders.set("Cache-Control", "no-store")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        } catch (failure: java.io.IOException) {
            // The IDE disconnected mid-response; there is nothing left to report to.
            LOGGER.debug("Development bridge response was not delivered", failure)
        }
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        server.stop(0)
        executor.shutdownNow()
        Files.deleteIfExists(record)
    }
}

private val LOGGER = LogUtils.getLogger()

internal fun json(vararg values: Pair<String, Any?>): JsonObject = JsonObject().apply {
    values.forEach { (key, value) -> add(key, Gson().toJsonTree(value)) }
}
/** A malformed request payload; reported as 400 rather than as an operation conflict. */
internal class DevRequestException(message: String) : RuntimeException(message)

internal fun JsonObject.string(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString
    ?.takeIf { it.isNotBlank() && it.length <= 4096 } ?: throw DevRequestException("Missing $key")
