package top.katton.pack

import net.minecraft.client.Minecraft
import top.katton.Katton
import top.katton.api.LOGGER
import top.katton.api.InvocationReason
import top.katton.api.ReloadCause
import top.katton.client.ScriptPackResourceManager
import top.katton.client.ScriptPackUi
import top.katton.engine.ScriptReloadManager
import top.katton.network.ScriptPackBundlePacket
import top.katton.network.ScriptPackHashListPacket
import top.katton.network.ScriptPackRequestPacket
import top.katton.network.ScriptPackSyncAckPacket
import top.katton.util.ReflectUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

object ServerPackCacheManager {

    private const val SERVER_PACKS_DIR_NAME = "serverpacks"
    private const val MANIFEST_FILE_NAME = "manifest.json"

    enum class RemoteSyncState {
        IDLE,
        DOWNLOADING,
        CACHED,
        PENDING_TRUST,
        TRUSTED,
        EXECUTING,
        REJECTED
    }

    data class RemoteServerIdentity(
        val bucket: String,
        val address: String
    )

    @Volatile
    private var activeServerBucket: String? = null

    @Volatile
    private var expectedHashes: Map<String, String> = emptyMap()

    @Volatile
    private var activeRevision: Long = 0L

    @Volatile
    private var pendingRevision: Long = 0L

    @Volatile
    private var liveAckSender: ((ScriptPackSyncAckPacket) -> Unit)? = null

    @Volatile
    private var activePacks: List<ScriptPack> = emptyList()

    @Volatile
    private var pendingPacks: List<ScriptPack> = emptyList()

    @Volatile
    private var syncState: RemoteSyncState = RemoteSyncState.IDLE

    /** Bridges the packet handler (Netty thread) to the enqueued main-thread task. */
    @Volatile
    private var mainThreadLatch: CountDownLatch? = null

    /**
     * Called by the packet handler on the Netty thread before `enqueueWork`.
     * Creates a latch that will be counted down after the enqueued task finishes.
     */
    fun prepareMainThreadSync() {
        mainThreadLatch = CountDownLatch(1)
    }

    /**
     * Called by the enqueued task on the main thread after [handleBundle] or
     * [handleHashList] finishes (which may have triggered a sync reload).
     */
    fun completeMainThreadSync() {
        mainThreadLatch?.countDown()
    }

    /**
     * Called by the packet handler on the Netty thread to block until the
     * enqueued main-thread task has finished processing.
     */
    fun awaitMainThreadSync() {
        mainThreadLatch?.apply {
            try {
                await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            mainThreadLatch = null
        }
    }

    @Synchronized
    fun reset() {
        ScriptPackResourceManager.clearServerCacheResources()
        activeServerBucket = null
        expectedHashes = emptyMap()
        activeRevision = 0L
        pendingRevision = 0L
        liveAckSender = null
        activePacks = emptyList()
        pendingPacks = emptyList()
        syncState = RemoteSyncState.IDLE
        mainThreadLatch = null
    }

    @Suppress("unused")
    fun currentSyncState(): RemoteSyncState = syncState

    /**
     * List server packs for katton pack manager GUI in game.
     */
    fun listPacksForGui(): List<ScriptPackView> {
        return activePacks
            .sortedWith(compareBy<ScriptPack> { it.manifest.name.lowercase() }.thenBy { it.manifest.id.lowercase() })
            .map { pack ->
                ScriptPackView(
                    syncId = pack.syncId,
                    scope = ScriptPackScope.SERVER_CACHE,
                    kind = pack.kind,
                    id = pack.manifest.id,
                    name = pack.manifest.name,
                    version = pack.manifest.version,
                    description = pack.manifest.description,
                    authors = pack.manifest.authors,
                    hash = pack.hash,
                    enabled = true,
                    locked = true,
                    sourcePath = pack.location.absolutePathString()
                )
            }
    }

    fun collectExecutablePacks(): List<ScriptPack> {
        return activePacks.toList()
    }

    /** Starts a play-phase delta update while keeping the current revision active. */
    @Synchronized
    fun handlePlayHashList(
        packet: ScriptPackHashListPacket,
        requestSender: (ScriptPackRequestPacket) -> Unit,
        ackSender: (ScriptPackSyncAckPacket) -> Unit
    ) {
        if (packet.revision <= 0L) return
        if (packet.revision < activeRevision) return
        if (packet.revision == activeRevision) {
            ackSender(ScriptPackSyncAckPacket(packet.revision, true, "already active"))
            return
        }
        if (packet.revision == pendingRevision) return
        val bucket = resolveCurrentServerBucket() ?: run {
            ackSender(ScriptPackSyncAckPacket(packet.revision, false, "cannot resolve server identity"))
            return
        }

        activeServerBucket = bucket
        liveAckSender?.invoke(
            ScriptPackSyncAckPacket(pendingRevision, false, "superseded by revision ${packet.revision}")
        )
        pendingRevision = packet.revision
        liveAckSender = ackSender
        expectedHashes = packet.entries.associate { it.syncId to it.hash }
        pendingPacks = emptyList()
        syncState = RemoteSyncState.DOWNLOADING

        val stagingRoot = resolveRevisionRoot(bucket, packet.revision)
        runCatching {
            deleteDirectory(stagingRoot)
            Files.createDirectories(stagingRoot)
            packet.entries.forEach { entry ->
                val unchanged = activePacks.firstOrNull { it.syncId == entry.syncId && it.hash == entry.hash }
                if (unchanged != null) {
                    copyPackDirectory(unchanged.location, stagingRoot.resolve(encodeSyncId(entry.syncId)))
                }
            }
        }.onFailure {
            failLiveRevision(packet.revision, "cannot create staging snapshot: ${it.message}")
            return
        }

        val changed = packet.entries
            .filter { entry -> activePacks.none { it.syncId == entry.syncId && it.hash == entry.hash } }
            .map { it.syncId }
        if (changed.isEmpty()) {
            finishLiveDownload(stagingRoot)
        } else {
            requestSender(ScriptPackRequestPacket(changed, packet.revision))
        }
    }

    /** Applies the changed part of a play-phase revision to its staging snapshot. */
    @Synchronized
    fun handlePlayBundle(packet: ScriptPackBundlePacket) {
        if (packet.revision <= 0L || packet.revision != pendingRevision) return
        val bucket = activeServerBucket ?: return
        val stagingRoot = resolveRevisionRoot(bucket, packet.revision)
        for (packData in packet.packs) {
            if (expectedHashes[packData.syncId] != packData.hash) {
                failLiveRevision(packet.revision, "unexpected pack or hash for ${packData.syncId}")
                return
            }
            val signatureResult = RemoteScriptSignatureVerifier.verify(packData)
            if (!signatureResult.valid) {
                failLiveRevision(packet.revision, "${packData.syncId}: ${signatureResult.reason}")
                return
            }
            persistPackBundle(stagingRoot, packData)
        }
        finishLiveDownload(stagingRoot)
    }

    private fun finishLiveDownload(stagingRoot: Path) {
        val revision = pendingRevision
        val resolved = expectedHashes.mapNotNull { (syncId, expectedHash) ->
            loadCachedPack(stagingRoot, syncId)?.takeIf { it.hash == expectedHash }
        }
        if (resolved.size != expectedHashes.size) {
            val present = resolved.mapTo(hashSetOf()) { it.syncId }
            failLiveRevision(revision, "incomplete snapshot: missing ${expectedHashes.keys - present}")
            return
        }
        val identity = resolveCurrentServerIdentity(activeServerBucket) ?: run {
            failLiveRevision(revision, "cannot resolve server identity")
            return
        }
        pendingPacks = resolved
        syncState = RemoteSyncState.CACHED
        if (!RemoteScriptTrustStore.isTrusted(identity.bucket) || hasUntrustedSigningKeys(resolved)) {
            syncState = RemoteSyncState.PENDING_TRUST
            ScriptPackUi.openRemoteScriptTrustScreen(identity.address, resolved) { trusted ->
                if (!isPendingRevision(revision)) return@openRemoteScriptTrustScreen
                if (!trusted) {
                    failLiveRevision(revision, "user rejected the updated scripts")
                } else {
                    RemoteScriptTrustStore.trust(identity.bucket, identity.address)
                    trustPackSigningKeys(identity, resolved)
                    prepareAndActivateLiveRevision(resolved)
                }
            }
            return
        }
        prepareAndActivateLiveRevision(resolved)
    }

    private fun prepareAndActivateLiveRevision(resolved: List<ScriptPack>) {
        val revision = pendingRevision
        val previous = activePacks
        syncState = RemoteSyncState.EXECUTING
        CompletableFuture.supplyAsync { ScriptReloadManager.prepareClientPacks(resolved) }
            .whenComplete { prepared, error ->
                Minecraft.getInstance().execute {
                    if (!isPendingRevision(revision)) return@execute
                    if (error != null || prepared != true) {
                        failLiveRevision(revision, error?.message ?: "candidate scripts did not compile")
                        return@execute
                    }
                    activePacks = resolved
                    ScriptReloadManager.reloadClientScriptsAsync(
                        InvocationReason.HOT_RELOAD,
                        ReloadCause.SERVER_PACK_SYNC
                    ) { activated ->
                        if (activated) {
                            completeLiveRevision(revision)
                        } else {
                            activePacks = previous
                            Minecraft.getInstance().execute {
                                ScriptReloadManager.reloadClientScriptsAsync(
                                    InvocationReason.HOT_RELOAD,
                                    ReloadCause.SERVER_PACK_SYNC,
                                    null
                                )
                            }
                            failLiveRevision(revision, "candidate scripts failed during activation")
                        }
                    }
                }
            }
    }

    @Synchronized
    private fun completeLiveRevision(revision: Long) {
        if (revision != pendingRevision) return
        activeRevision = revision
        pendingRevision = 0L
        pendingPacks = emptyList()
        syncState = RemoteSyncState.IDLE
        sendAndClearLiveAck(ScriptPackSyncAckPacket(revision, true, "applied"))
    }

    @Synchronized
    private fun failLiveRevision(revision: Long, reason: String) {
        if (revision != pendingRevision) return
        LOGGER.warn("Failed to apply Katton script-pack revision {}: {}", revision, reason)
        pendingRevision = 0L
        pendingPacks = emptyList()
        syncState = RemoteSyncState.REJECTED
        sendAndClearLiveAck(ScriptPackSyncAckPacket(revision, false, reason.take(1024)))
    }

    @Synchronized
    private fun isPendingRevision(revision: Long): Boolean = pendingRevision == revision

    private fun sendAndClearLiveAck(packet: ScriptPackSyncAckPacket) {
        val sender = liveAckSender ?: return
        liveAckSender = null
        val minecraft = Minecraft.getInstance()
        if (minecraft.isSameThread) {
            sender(packet)
        } else {
            minecraft.execute { sender(packet) }
        }
    }

    @Synchronized
    fun handleHashList(packet: ScriptPackHashListPacket, requestSender: (ScriptPackRequestPacket) -> Unit) {
        val bucket = resolveCurrentServerBucket() ?: run {
            LOGGER.warn("Cannot resolve current server bucket, skipping script pack hash sync")
            return
        }

        activeServerBucket = bucket
        expectedHashes = packet.entries.associate { it.syncId to it.hash }

        if (packet.entries.isEmpty()) {
            // No packs on server: clear stale cache with an async client reload.
            activePacks = emptyList()
            syncState = RemoteSyncState.EXECUTING
            ScriptReloadManager.reloadClientScriptsAsync { success -> completeClientReload(null, success) }
            return
        }

        // The server sends the full bundle snapshot right after the hash list.
        // handleBundle will follow shortly and start the actual async reload.
    }

    @Synchronized
    fun handleHashListWithCompletion(
        packet: ScriptPackHashListPacket,
        requestSender: (ScriptPackRequestPacket) -> Unit,
        completeWhenDeferred: () -> Unit
    ): Boolean {
        val bucket = resolveCurrentServerBucket() ?: run {
            LOGGER.warn("Cannot resolve current server bucket, skipping script pack hash sync")
            return true
        }

        activeServerBucket = bucket
        expectedHashes = packet.entries.associate { it.syncId to it.hash }

        if (packet.entries.isEmpty()) {
            activePacks = emptyList()
            syncState = RemoteSyncState.EXECUTING
            ScriptReloadManager.reloadClientScriptsAsync { success -> completeClientReload(completeWhenDeferred, success) }
            return false
        }

        return true
    }

    @Synchronized
    fun handleBundle(packet: ScriptPackBundlePacket) {
        handleBundle(packet, completeWhenDeferred = null)
    }

    /**
     * Handles a configuration-time bundle. Returns true when the caller may
     * release its networking latch immediately. Returns false when an unknown
     * server trust prompt is open and [completeWhenDeferred] will release it.
     */
    @Synchronized
    fun handleBundleWithTrustPrompt(packet: ScriptPackBundlePacket, completeWhenDeferred: () -> Unit): Boolean {
        return handleBundle(packet, completeWhenDeferred)
    }

    private fun handleBundle(packet: ScriptPackBundlePacket, completeWhenDeferred: (() -> Unit)?): Boolean {
        val bucket = activeServerBucket ?: resolveCurrentServerBucket() ?: return true
        val cachedRoot = resolveCachedRoot(bucket)
        syncState = RemoteSyncState.DOWNLOADING

        // verify signatures and persist packs to cache
        packet.packs.forEach { packData ->
            if (packData.syncId !in expectedHashes) {
                LOGGER.warn("Ignoring unexpected server pack {}", packData.syncId)
                return@forEach
            }
            val signatureResult = RemoteScriptSignatureVerifier.verify(packData)
            if (!signatureResult.valid) {
                rejectRemoteScripts(
                    reason = "${packData.syncId}: ${signatureResult.reason}",
                    disconnect = completeWhenDeferred != null
                )
                return true
            }
            if (!signatureResult.signed) {
                LOGGER.warn("Remote Katton pack {} is unsigned; user trust prompt is still required before execution", packData.syncId)
            }
            persistPackBundle(cachedRoot, packData)
        }
        syncState = RemoteSyncState.CACHED

        val resolved = mutableListOf<ScriptPack>()
        val unresolved = mutableListOf<String>()

        expectedHashes.forEach { (syncId, expectedHash) ->
            val cached = loadCachedPack(cachedRoot, syncId)
            if (cached == null || cached.hash != expectedHash) {
                unresolved += syncId
            } else {
                resolved += cached
            }
        }

        if (unresolved.isNotEmpty()) {
            LOGGER.warn("Some server packs are still unresolved after bundle sync: {}", unresolved)
            activePacks = emptyList()
            pendingPacks = emptyList()
            syncState = RemoteSyncState.REJECTED
            if (completeWhenDeferred != null) {
                ScriptPackUi.disconnectRemoteScripts("Katton server pack snapshot is incomplete: $unresolved")
            }
            return true
        }

        val identity = resolveCurrentServerIdentity(bucket) ?: return true
        pendingPacks = resolved

        //if remove server is untrusted or if script packs are not signed properly
        // show a trust screen to prompt user to trust the server before enabling scripts.
        if (!RemoteScriptTrustStore.isTrusted(identity.bucket) || hasUntrustedSigningKeys(resolved)) {
            activePacks = emptyList()
            syncState = RemoteSyncState.PENDING_TRUST
            if (completeWhenDeferred == null) {
                LOGGER.warn("Skipping remote script execution from untrusted server or signing key {}", identity.address)
                return true
            }
            ScriptPackUi.openRemoteScriptTrustScreen(identity.address, resolved) { trusted ->
                finishTrustDecision(identity, trusted, completeWhenDeferred)
            }
            return false
        }

        activePacks = resolved
        pendingPacks = emptyList()
        syncState = RemoteSyncState.TRUSTED
        return executeTrustedPacks(completeWhenDeferred)
    }

    @Synchronized
    private fun finishTrustDecision(identity: RemoteServerIdentity, trusted: Boolean, completeWhenDeferred: (() -> Unit)? = null) {
        if (!trusted) {
            LOGGER.warn("User rejected remote Katton scripts from {}", identity.address)
            activePacks = emptyList()
            pendingPacks = emptyList()
            syncState = RemoteSyncState.REJECTED
            ScriptPackUi.disconnectRemoteScripts("Remote Katton scripts were not accepted")
            completeWhenDeferred?.invoke()
            return
        }

        RemoteScriptTrustStore.trust(identity.bucket, identity.address)
        trustPackSigningKeys(identity, pendingPacks)
        activePacks = pendingPacks
        pendingPacks = emptyList()
        syncState = RemoteSyncState.TRUSTED
        executeTrustedPacks(completeWhenDeferred)
    }

    private fun executeTrustedPacks(completeWhenDeferred: (() -> Unit)? = null): Boolean {
        // Keep configuration networking waiting when requested, but move the
        // heavy client scan/compile phase onto the client reload worker.
        syncState = RemoteSyncState.EXECUTING
        ScriptReloadManager.reloadClientScriptsAsync { success -> completeClientReload(completeWhenDeferred, success) }
        return completeWhenDeferred == null
    }

    @Synchronized
    private fun completeClientReload(completeWhenDeferred: (() -> Unit)?, success: Boolean = true) {
        syncState = if (success) RemoteSyncState.IDLE else RemoteSyncState.REJECTED
        if (!success && completeWhenDeferred != null) {
            ScriptPackUi.disconnectRemoteScripts("Katton server scripts failed dependency validation, compilation, or execution")
        }
        completeWhenDeferred?.invoke()
    }

    private fun rejectRemoteScripts(reason: String, disconnect: Boolean) {
        LOGGER.warn("Rejecting remote Katton scripts: {}", reason)
        activePacks = emptyList()
        pendingPacks = emptyList()
        syncState = RemoteSyncState.REJECTED
        if (disconnect) {
            ScriptPackUi.disconnectRemoteScripts("Katton remote script signature check failed: $reason")
        }
    }

    private fun trustPackSigningKeys(identity: RemoteServerIdentity, packs: List<ScriptPack>) {
        packs.forEach { pack ->
            val signature = pack.manifest.signature ?: return@forEach
            val publicKey = signature.publicKey ?: return@forEach
            val fingerprint = RemoteScriptSignatureVerifier.verify(pack).keyFingerprint ?: return@forEach
            RemoteScriptTrustStore.trustPublicKey(
                keyId = signature.keyId,
                publicKey = publicKey,
                serverAddress = identity.address,
                fingerprint = fingerprint
            )
        }
    }

    private fun hasUntrustedSigningKeys(packs: List<ScriptPack>): Boolean {
        return packs.any { pack ->
            val signature = pack.manifest.signature ?: return@any false
            RemoteScriptTrustStore.trustedPublicKey(signature.keyId) == null && signature.publicKey != null
        }
    }

    /**
     * Called by the registry-check mixin on the Netty thread.
     *
     * No-op: the actual reload is coordinated by handleBundle/handleHashList
     * and configuration networking waits for its completion when necessary.
     */
    @Synchronized
    fun executePendingScriptsBeforeRegistryCheck() {
        // Reload is coordinated by handleBundle/handleHashList.
    }

    private fun loadCachedPack(cachedRoot: Path, syncId: String): ScriptPack? {
        val packDirectory = cachedRoot.resolve(encodeSyncId(syncId))
        if (!Files.isDirectory(packDirectory)) {
            return null
        }

        return ScriptPackManager.scanPackDirectory(
            packDirectory = packDirectory,
            scope = ScriptPackScope.SERVER_CACHE,
            syncIdOverride = syncId,
            forceEnabled = true
        )
    }

    private fun persistPackBundle(cachedRoot: Path, packData: ScriptPackBundlePacket.PackData) {
        val packDirectory = cachedRoot.resolve(encodeSyncId(packData.syncId))

        runCatching {
            deleteDirectory(packDirectory)
            Files.createDirectories(packDirectory)
            Files.writeString(
                packDirectory.resolve(MANIFEST_FILE_NAME),
                packData.manifestJson,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            packData.files.forEach { fileData ->
                val output = packDirectory.resolve(fileData.relativePath).normalize()
                if (!output.startsWith(packDirectory)) {
                    return@forEach
                }
                output.parent?.let(Files::createDirectories)
                Files.write(
                    output,
                    fileData.content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
            }
        }.onFailure {
            LOGGER.warn("Failed to persist server pack {}", packData.syncId, it)
        }
    }

    private fun resolveCachedRoot(bucket: String): Path {
        val gameDir = Katton.gameDirectory ?: error("Game directory is not initialized")
        val root = gameDir.resolve(SERVER_PACKS_DIR_NAME).resolve(bucket)
        Files.createDirectories(root)
        return root
    }

    private fun resolveCurrentServerBucket(): String? {
        return resolveCurrentServerIdentity()?.bucket
    }

    private fun resolveCurrentServerIdentity(bucketOverride: String? = null): RemoteServerIdentity? {
        val mc = Minecraft.getInstance()
        val address = normalizeAddress(mc.currentServer?.ip)
            ?: resolveConnectionAddress(mc)
            ?: return null
        return RemoteServerIdentity(bucketOverride ?: sha256(address), address)
    }

    private fun resolveRevisionRoot(bucket: String, revision: Long): Path {
        val root = resolveCachedRoot(bucket).resolve("revisions").resolve(revision.toString())
        Files.createDirectories(root)
        return root
    }

    private fun copyPackDirectory(source: Path, target: Path) {
        require(Files.isDirectory(source)) { "Pack cache path is not a directory: $source" }
        Files.walk(source).use { stream ->
            stream.forEach { path ->
                val output = target.resolve(source.relativize(path).toString()).normalize()
                require(output.startsWith(target)) { "Pack cache path escaped staging directory" }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(output)
                } else {
                    output.parent?.let(Files::createDirectories)
                    Files.copy(path, output, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun normalizeAddress(address: String?): String? {
        val normalized = address
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (normalized == "singleplayer" || normalized == "local") {
            return null
        }
        return normalized
    }

    private fun resolveConnectionAddress(mc: Minecraft): String? {
        val connection = mc.connection?.connection ?: return null
        return runCatching {
            ReflectUtil.invoke(connection, "getRemoteAddress").getOrNull()?.toString()
        }.getOrNull()?.let(::normalizeAddress)
    }

    private fun encodeSyncId(syncId: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(syncId.toByteArray(StandardCharsets.UTF_8))
    }

    private fun deleteDirectory(path: Path) {
        if (!Files.exists(path)) {
            return
        }

        Files.walk(path).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(text.toByteArray(StandardCharsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
