package top.katton.dev

import com.google.gson.JsonObject
import net.minecraft.SharedConstants
import net.minecraft.world.level.storage.LevelResource
import top.katton.Katton
import top.katton.engine.*
import top.katton.pack.*
import top.katton.platform.ServerRuntimeCapabilities
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** Optional development service. Enabling it never enables JDWP or changes a remote server. */
object KattonDevBridge : DevRuntime {
    @Volatile private var bridge: DevBridgeServer? = null
    private val target = DevTargetSession()
    private var issueListenerInstalled = false
    private var logAppender: DevLogAppender? = null
    @JvmStatic fun worldChanged() { target.invalidate() }
    @JvmStatic fun isEnabled(): Boolean = bridge != null
    @JvmStatic @Synchronized fun enable(): String {
        if (bridge == null) {
            bridge = DevBridgeServer(this, Path.of(System.getProperty("user.home"), ".katton", "dev", "instances"))
            DevEvents.enabled = true
            runCatching { DevLogAppender().also { it.attach(); logAppender = it } }
                .onFailure { DevEvents.emit("WARNING", "Runtime log capture unavailable: ${it.message}") }
            if (!issueListenerInstalled) {
                ScriptIssueReporter.addListener { DevEvents.emit("ERROR", "${it.title}\n${it.detail}") }
                Runtime.getRuntime().addShutdownHook(Thread { disable() })
                issueListenerInstalled = true
            }
        }
        return "Katton development connection enabled on localhost:${bridge!!.port}. Connect from IDEA."
    }
    @JvmStatic @Synchronized fun disable(): String {
        bridge?.close(); bridge = null; logAppender?.detach(); logAppender = null; DevEvents.enabled = false
        return "Katton development connection disabled."
    }
    override fun snapshot(): JsonObject {
        val server = Katton.server
        val world = server?.getWorldPath(LevelResource.ROOT)?.toAbsolutePath()?.normalize()
        val packs = ScriptPackManager.listLocalPacksForGui(false).map { view ->
            json("id" to view.syncId, "scope" to view.scope.serializedName, "name" to view.name,
                "version" to view.version, "hash" to view.hash, "enabled" to view.enabled, "path" to view.sourcePath)
        }
        val jdwp = ManagementFactory.getRuntimeMXBean().inputArguments.firstOrNull { it.startsWith("-agentlib:jdwp=") }
        val debug = jdwp?.removePrefix("-agentlib:jdwp=")?.split(',')?.mapNotNull {
            val p = it.split('=', limit = 2); if (p.size == 2) p[0] to p[1] else null
        }?.toMap().orEmpty()
        return json("pid" to ProcessHandle.current().pid(), "platform" to ScriptDependencyManager.platform.serializedName,
            "startedAt" to ProcessHandle.current().info().startInstant().map { it.toEpochMilli() }.orElse(0L),
            "kattonVersion" to ScriptDependencyManager.installedDependency("katton")?.version?.let {
                if (it.contains("+mc")) it else "$it+mc${SharedConstants.getCurrentVersion().name()}"
            },
            "minecraftVersion" to SharedConstants.getCurrentVersion().name(),
            "gameDirectory" to Katton.gameDirectory?.toString(), "worldDirectory" to world?.toString(),
            "worldId" to target.token(server), "writable" to (server != null), "client" to (server != null && !server.isDedicatedServer),
            "capabilities" to json("clientScripts" to Katton.hasClient, "assets" to Katton.hasClient,
                "registry" to Katton.registrationEnabled, "injection" to Katton.registrationEnabled,
                "dataReload" to ServerRuntimeCapabilities.supportsScriptPackDataReload(), "globalCodeReload" to false),
            "dataReload" to ServerRuntimeCapabilities.supportsScriptPackDataReload(), "packs" to packs,
            "debugAddress" to debug["address"]?.takeIf { debug["server"] == "y" },
            "busy" to (ScriptReloadManager.isServerReloadRunning() || ScriptReloadManager.isClientReloadRunning()))
    }
    override fun dependencies(request: JsonObject): JsonObject {
        val ids = request.getAsJsonArray("ids") ?: error("Missing dependency IDs")
        require(ids.size() <= 128) { "Too many dependency IDs" }
        return json("dependencies" to ids.map { value ->
            val id = value.asString.also { require(it.length <= 256) }
            val dependency = ScriptDependencyManager.installedDependency(id)
            val fingerprints = dependency?.classpath.orEmpty().filter { java.nio.file.Files.isRegularFile(it) }.map { path ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                java.nio.file.Files.newInputStream(path).use { stream ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) { val count = stream.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            json("id" to id, "version" to dependency?.version, "enabled" to (dependency?.enabled == true), "fileFingerprints" to fingerprints)
        })
    }
    override fun refresh(): JsonObject = snapshot().apply {
        check(!ScriptReloadManager.isServerReloadRunning() && !ScriptReloadManager.isClientReloadRunning()) { "Reload is active; refresh after it completes" }
        val disk = (ScriptPackManager.scanGlobalPacksCandidate() + ScriptPackManager.scanWorldPacksCandidate()).associateBy { it.syncId }
        val active = (PackRuntime.effectivePacks(ScriptEnvironment.SERVER, setOf(ScriptPackScope.WORLD)) + ScriptPackManager.collectExecutableGlobalPacks()).associateBy { it.syncId }
        add("packs", com.google.gson.JsonArray().apply { disk.values.forEach { pack ->
            add(json("id" to pack.syncId, "scope" to pack.scope.serializedName, "name" to pack.manifest.name,
                "version" to pack.manifest.version, "hash" to pack.hash, "activeHash" to active[pack.syncId]?.hash,
                "enabled" to pack.enabled, "path" to pack.location.toString()))
        } })
    }
    private fun prepare(request: JsonObject): DevDeployment {
        val server = checkNotNull(Katton.server) { "No local server world is available; remote server caches are read-only" }
        require(request.string("worldId") == target.token(server)) { "World changed; reconnect and select the target again" }
        val packs = ScriptPackManager.collectExecutableGlobalPacks() + ScriptPackManager.scanWorldPacksCandidate()
        val deployment = DevDeployment(request, server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(), packs,
            Katton.gameDirectory?.toAbsolutePath()?.normalize())
        if (deployment.restart.isNotEmpty()) return deployment
        val graph = ScriptPackDependencyGraph.resolve(deployment.proposed())
        val ids = deployment.requested.mapTo(hashSetOf()) { it.syncId }.apply { addAll(deployment.affected()) }
        require(graph.invalidPacks.none { it.syncId in ids }) { graph.errors.joinToString("\n") }
        val selected = ScriptDependencyManager.resolve(graph.orderedPacks, ScriptEnvironment.SERVER, "READY")
        require(deployment.requested.all { pack -> selected.validPacks.any { it.syncId == pack.syncId } }) { selected.errors.joinToString("\n") }
        require(ServerRuntimeCapabilities.supportsScriptPackDataReload() || deployment.requested.none { pack -> pack.contentFiles.any { it.relativePath.startsWith("data/") } }) {
            "This runtime does not support data-resource reload (Folia)"
        }
        require(Katton.hasClient || deployment.requested.none { pack -> pack.contentFiles.any { it.relativePath.startsWith("assets/") } }) {
            "Paper has no Katton client resource support"
        }
        return deployment
    }
    override fun preflight(request: JsonObject): JsonObject {
        val deployment = prepare(request)
        deployment.requireSyncBudget(top.katton.network.ScriptPackPacketLimits.MAX_FILES_PER_BUNDLE,
            top.katton.network.ScriptPackPacketLimits.MAX_BUNDLE_CONTENT_BYTES.toLong())
        return json("status" to if (deployment.restart.isEmpty()) "READY" else "RESTART_REQUIRED",
            "restart" to deployment.restart, "changed" to deployment.changes.map { it.pack.syncId }, "affected" to deployment.affected())
    }
    override fun apply(request: JsonObject): CompletableFuture<JsonObject> {
        val future = CompletableFuture<JsonObject>()
        val server = checkNotNull(Katton.server) { "No local world" }
        val expectedWorld = target.token(server)
        var deployment: DevDeployment? = null
        val admitted = ScriptReloadManager.tryDevelopmentReload(server, {
            check(target.token(Katton.server) == expectedWorld && Katton.server === server) { "World changed before application" }
            val prepared = prepare(request)
            check(prepared.restart.isEmpty()) { "Global code changes require restart: ${prepared.restart}" }
            deployment = prepared
            prepared.install()
            DevEvents.emit("INFO", "Applying ${prepared.changes.size} changed packs")
        }, { success ->
            try {
                val prepared = deployment
                check(prepared != null) { "Deployment preparation failed; inspect diagnostics" }
                val active = PackRuntime.effectivePacks(ScriptEnvironment.SERVER, setOf(ScriptPackScope.WORLD)) + ScriptPackManager.collectExecutableGlobalPacks()
                val accepted = prepared.requested.filter { candidate ->
                    target.token(Katton.server) == expectedWorld && Katton.server === server && active.any { it.syncId == candidate.syncId && it.hash == candidate.hash }
                }.mapTo(hashSetOf()) { it.syncId }
                prepared.finish(accepted)
                if (target.token(Katton.server) == expectedWorld && Katton.server === server) {
                    ScriptPackManager.publishWorldPacks(ScriptPackManager.scanWorldPacksCandidate(), ScriptPackManager.collectExecutableWorldPacks())
                }
                val results = prepared.requested.map { candidate ->
                    val serverOk = candidate.syncId in accepted
                    val clientOk = server.isDedicatedServer || candidate.scope == ScriptPackScope.GLOBAL ||
                        PackRuntime.effectivePacks(ScriptEnvironment.CLIENT, setOf(ScriptPackScope.WORLD)).any { it.syncId == candidate.syncId && it.hash == candidate.hash }
                    json("id" to candidate.syncId, "hash" to candidate.hash,
                        "status" to if (serverOk && clientOk && success) "APPLIED" else if (serverOk) "PARTIAL" else "FAILED")
                }
                val statuses = results.map { it.string("status") }
                val status = when { statuses.all { it == "APPLIED" } -> "APPLIED"; statuses.any { it != "FAILED" } -> "PARTIAL"; else -> "FAILED" }
                future.complete(json("status" to status, "packs" to results, "affected" to prepared.affected()))
                DevEvents.emit(if (status == "APPLIED") "INFO" else "ERROR", "Application finished: $status")
            } catch (t: Throwable) { future.completeExceptionally(t) }
        })
        if (!admitted) future.completeExceptionally(IllegalStateException("Another reload is active; retry after it completes"))
        return future
    }
}
