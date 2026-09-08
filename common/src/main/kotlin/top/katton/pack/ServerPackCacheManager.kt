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
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString

object ServerPackCacheManager {

    private const val SERVER_PACKS_DIR_NAME = "serverpacks-v3"
    private const val MANIFEST_FILE_NAME = "manifest.json"
    private const val REVISIONS_DIR_NAME = "revisions"
    private const val CONFIGURATION_SYNC_TIMEOUT_SECONDS = 120L

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

    private data class QueuedLiveRevision(
        val packet: ScriptPackHashListPacket,
        val requestSender: (ScriptPackRequestPacket) -> Unit,
        val ackSender: (ScriptPackSyncAckPacket) -> Unit
    )

    @Volatile
    private var activeServerBucket: String? = null

    /** Address selected by the user, captured before configuration networking starts. */
    @Volatile
    private var connectingServerAddress: String? = null

    @Volatile
    private var expectedHashes: Map<String, String> = emptyMap()

    @Volatile
    private var activeRevision: Long = 0L

    @Volatile
    private var pendingRevision: Long = 0L

    /** Revision currently compiling or executing; it cannot be safely superseded mid-activation. */
    private var activatingRevision: Long = 0L

    /** Only the newest revision received during activation needs a follow-up pass. */
    private var queuedLiveRevision: QueuedLiveRevision? = null

    @Volatile
    private var liveAckSender: ((ScriptPackSyncAckPacket) -> Unit)? = null

    @Volatile
    private var activePacks: List<ScriptPack> = emptyList()

    @Volatile
    private var pendingPacks: List<ScriptPack> = emptyList()

    @Volatile
    private var syncState: RemoteSyncState = RemoteSyncState.IDLE

    /** Invalidates delayed trust/reload callbacks from an older connection attempt. */
    private var configurationSyncGeneration: Long = 0L

    /** One handle per packet prevents a stale completion from releasing a newer packet. */
    class MainThreadSyncHandle internal constructor(
        internal val latch: CountDownLatch = CountDownLatch(1)
    )

    private val pendingMainThreadSyncs = ConcurrentHashMap.newKeySet<MainThreadSyncHandle>()

    /**
     * Compatibility slot for platform modules compiled against the original no-argument API.
     * Current callers retain their own [MainThreadSyncHandle] and never rely on this shared slot.
     */
    private val legacyMainThreadSync = AtomicReference<MainThreadSyncHandle?>()

    /**
     * Called by the packet handler on the Netty thread before `enqueueWork`.
     * Creates a latch that will be counted down after the enqueued task finishes.
     */
    fun beginMainThreadSync(): MainThreadSyncHandle {
        return MainThreadSyncHandle().also(pendingMainThreadSyncs::add)
    }

    /**
     * Retains the original `()V` JVM descriptor for older platform artifacts.
     * A replaced legacy wait is released so it cannot remain blocked indefinitely.
     */
    fun prepareMainThreadSync() {
        legacyMainThreadSync.getAndSet(beginMainThreadSync())?.let(::completeMainThreadSync)
    }

    /**
     * Called by the enqueued task on the main thread after [handleBundle] or
     * [handleHashList] finishes (which may have triggered a sync reload).
     */
    fun completeMainThreadSync(handle: MainThreadSyncHandle) {
        pendingMainThreadSyncs.remove(handle)
        handle.latch.countDown()
    }

    /** Compatibility bridge for the original no-argument platform call. */
    fun completeMainThreadSync() {
        legacyMainThreadSync.get()?.let(::completeMainThreadSync)
    }

    /**
     * Called by the packet handler on the Netty thread to block until the
     * enqueued main-thread task has finished processing.
     */
    fun awaitMainThreadSync(handle: MainThreadSyncHandle): Boolean {
        val minecraft = runCatching { Minecraft.getInstance() }.getOrNull()
        val completed = try {
            if (minecraft != null && minecraft.isSameThread) {
                // NeoForge dispatches configuration payload handlers on the render
                // thread. Pump its task queue while waiting so the async compiler
                // can schedule the main-thread activation work that releases us.
                val deadline = System.nanoTime() +
                    TimeUnit.SECONDS.toNanos(CONFIGURATION_SYNC_TIMEOUT_SECONDS)
                minecraft.managedBlock {
                    handle.latch.count == 0L || System.nanoTime() >= deadline
                }
                handle.latch.count == 0L
            } else {
                handle.latch.await(CONFIGURATION_SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } finally {
            pendingMainThreadSyncs.remove(handle)
        }
        if (!completed) {
            LOGGER.warn(
                "Timed out waiting {} seconds for Katton configuration script synchronization",
                CONFIGURATION_SYNC_TIMEOUT_SECONDS
            )
            runCatching {
                Minecraft.getInstance().execute {
                    ScriptPackUi.disconnectRemoteScripts("Katton script synchronization timed out")
                }
            }.onFailure { LOGGER.warn("Unable to schedule disconnect after Katton sync timeout", it) }
        }
        return completed
    }

    /** Compatibility bridge for the original no-argument platform call. */
    fun awaitMainThreadSync() {
        val handle = legacyMainThreadSync.get() ?: return
        try {
            awaitMainThreadSync(handle)
        } finally {
            legacyMainThreadSync.compareAndSet(handle, null)
        }
    }

    @Synchronized
    fun reset() {
        advanceConfigurationGeneration()
        pendingMainThreadSyncs.toList().forEach(::completeMainThreadSync)
        legacyMainThreadSync.set(null)
        ScriptPackResourceManager.clearServerCacheResources()
        activeServerBucket?.let { bucket -> cleanupRevisionDirectories(bucket, keepRevision = null) }
        activeServerBucket = null
        connectingServerAddress = null
        expectedHashes = emptyMap()
        activeRevision = 0L
        pendingRevision = 0L
        activatingRevision = 0L
        queuedLiveRevision = null
        liveAckSender = null
        activePacks = emptyList()
        pendingPacks = emptyList()
        syncState = RemoteSyncState.IDLE
    }

    /**
     * Captures the logical server address before Minecraft creates its play-phase
     * packet listener. During configuration, `Minecraft.currentServer` and
     * `Minecraft.connection` may both still be unavailable.
     */
    @JvmStatic
    @Synchronized
    fun beginRemoteConnection(address: String?) {
        connectingServerAddress = normalizeAddress(address)
    }

    @Suppress("unused")
    fun currentSyncState(): RemoteSyncState = syncState

    /**
     * List server packs for katton pack manager GUI in game.
     */
    fun listPacksForGui(): List<ScriptPackView> {
        return activePacks
            .sortedWith(
                compareBy<ScriptPack> { it.manifest.name.lowercase(Locale.ROOT) }
                    .thenBy { it.manifest.id.lowercase(Locale.ROOT) }
            )
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
            sendAckSafely(ackSender, ScriptPackSyncAckPacket(packet.revision, true, "already active"))
            return
        }
        if (packet.revision == pendingRevision) return
        if (pendingRevision > 0L && packet.revision < pendingRevision) return

        // collectExecutablePacks() must expose the candidate snapshot while its
        // entrypoints run. Replacing pendingRevision at that point could let a
        // stale completion publish the wrong snapshot, so coalesce newer input
        // into one follow-up revision instead.
        if (activatingRevision != 0L) {
            val queued = queuedLiveRevision
            if (queued == null || packet.revision > queued.packet.revision) {
                queuedLiveRevision = QueuedLiveRevision(packet, requestSender, ackSender)
            }
            return
        }
        val bucket = resolveCurrentServerBucket() ?: run {
            sendAckSafely(
                ackSender,
                ScriptPackSyncAckPacket(packet.revision, false, "cannot resolve server identity")
            )
            return
        }

        activeServerBucket = bucket
        val supersededRevision = pendingRevision
        if (supersededRevision > 0L) {
            sendAndClearLiveAck(
                ScriptPackSyncAckPacket(supersededRevision, false, "superseded by revision ${packet.revision}")
            )
            deleteRevisionQuietly(bucket, supersededRevision)
        }
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
                    copyCachedPack(unchanged, stagingRoot.resolve(encodeSyncId(entry.syncId)))
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
            runCatching { requestSender(ScriptPackRequestPacket(changed, packet.revision)) }
                .onFailure { failure ->
                    failLiveRevision(packet.revision, "cannot request changed packs: ${failure.message}")
                }
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
            if (!persistPackBundle(stagingRoot, packData)) {
                failLiveRevision(packet.revision, "cannot persist pack ${packData.syncId}")
                return
            }
        }
        finishLiveDownload(stagingRoot)
    }

    private fun finishLiveDownload(stagingRoot: Path) {
        val revision = pendingRevision
        val resolved = expectedHashes.mapNotNull { (syncId, expectedHash) ->
            loadCachedPack(stagingRoot, syncId, expectedHash)
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

    @Synchronized
    private fun prepareAndActivateLiveRevision(resolved: List<ScriptPack>) {
        val revision = pendingRevision
        if (revision <= 0L || activatingRevision != 0L) return
        activatingRevision = revision
        val previous = activePacks
        syncState = RemoteSyncState.EXECUTING
        CompletableFuture.supplyAsync { ScriptReloadManager.prepareClientPacks(resolved) }
            .whenComplete { prepared, error ->
                runCatching {
                    Minecraft.getInstance().execute {
                        if (!isPendingRevision(revision)) return@execute
                        if (error != null || prepared != true) {
                            failLiveRevision(revision, error?.message ?: "candidate scripts did not compile")
                            return@execute
                        }
                        startLiveCandidateActivation(revision, resolved, previous)
                    }
                }.onFailure { failure ->
                    failLiveRevision(revision, failure.message ?: "client executor rejected candidate activation")
                }
            }
    }

    /** Atomically publishes the candidate and submits its activation reload. */
    @Synchronized
    private fun startLiveCandidateActivation(
        revision: Long,
        resolved: List<ScriptPack>,
        previous: List<ScriptPack>
    ) {
        if (revision != pendingRevision) return
        activePacks = resolved
        runCatching {
            ScriptReloadManager.reloadClientScriptsAsync(
                InvocationReason.HOT_RELOAD,
                ReloadCause.SERVER_PACK_SYNC
            ) { activated ->
                // reset() or disconnect may invalidate the candidate while the
                // asynchronous entrypoints are still running.
                if (!isPendingRevision(revision)) return@reloadClientScriptsAsync
                if (activated) {
                    completeLiveRevision(revision)
                } else {
                    rollbackFailedLiveRevision(
                        revision,
                        previous,
                        "candidate scripts failed during activation"
                    )
                }
            }
        }.onFailure { failure ->
            // A rejected reload submission happens after activePacks was
            // switched to the candidate, so restore the previous snapshot
            // through the same serialized rollback path.
            rollbackFailedLiveRevision(
                revision,
                previous,
                failure.message ?: "candidate activation could not be scheduled"
            )
        }
    }

    /**
     * Restores the last active snapshot before allowing a queued server revision
     * to start. Keeping [activatingRevision] set during this reload prevents the
     * candidate, rollback, and next revision from mutating script state at once.
     */
    @Synchronized
    private fun rollbackFailedLiveRevision(
        revision: Long,
        previous: List<ScriptPack>,
        activationFailure: String
    ) {
        if (!isPendingRevision(revision)) return
        activePacks = previous
        runCatching {
            ScriptReloadManager.reloadClientScriptsAsync(
                InvocationReason.HOT_RELOAD,
                ReloadCause.SERVER_PACK_SYNC
            ) { rollbackSucceeded ->
                if (!isPendingRevision(revision)) return@reloadClientScriptsAsync
                val reason = if (rollbackSucceeded) {
                    activationFailure
                } else {
                    "$activationFailure; previous scripts also failed to restore"
                }
                failLiveRevision(revision, reason)
            }
        }.onFailure { rollbackFailure ->
            failLiveRevision(
                revision,
                "$activationFailure; rollback could not be scheduled: ${rollbackFailure.message}"
            )
        }
    }

    @Synchronized
    private fun completeLiveRevision(revision: Long) {
        if (revision != pendingRevision) return
        activeRevision = revision
        pendingRevision = 0L
        activatingRevision = 0L
        pendingPacks = emptyList()
        syncState = RemoteSyncState.IDLE
        activeServerBucket?.let { bucket -> cleanupRevisionDirectories(bucket, keepRevision = revision) }
        sendAndClearLiveAck(ScriptPackSyncAckPacket(revision, true, "applied"))
        startQueuedLiveRevisionIfPresent()
    }

    @Synchronized
    private fun failLiveRevision(revision: Long, reason: String) {
        if (revision != pendingRevision) return
        LOGGER.warn("Failed to apply Katton script-pack revision {}: {}", revision, reason)
        pendingRevision = 0L
        if (activatingRevision == revision) activatingRevision = 0L
        pendingPacks = emptyList()
        syncState = RemoteSyncState.REJECTED
        activeServerBucket?.let { bucket ->
            deleteRevisionQuietly(bucket, revision)
            cleanupRevisionDirectories(bucket, keepRevision = activeRevision.takeIf { it > 0L })
        }
        sendAndClearLiveAck(ScriptPackSyncAckPacket(revision, false, reason.take(1024)))
        startQueuedLiveRevisionIfPresent()
    }

    @Synchronized
    private fun isPendingRevision(revision: Long): Boolean = pendingRevision == revision

    /** Starts the latest revision deferred while the previous candidate was executing. */
    private fun startQueuedLiveRevisionIfPresent() {
        val queued = queuedLiveRevision ?: return
        queuedLiveRevision = null
        handlePlayHashList(queued.packet, queued.requestSender, queued.ackSender)
    }

    private fun sendAndClearLiveAck(packet: ScriptPackSyncAckPacket) {
        val sender = liveAckSender ?: return
        liveAckSender = null
        val minecraft = Minecraft.getInstance()
        if (minecraft.isSameThread) {
            sendAckSafely(sender, packet)
        } else {
            runCatching { minecraft.execute { sendAckSafely(sender, packet) } }
                .onFailure { LOGGER.warn("Client executor rejected Katton sync acknowledgement", it) }
        }
    }

    private fun sendAckSafely(
        sender: (ScriptPackSyncAckPacket) -> Unit,
        packet: ScriptPackSyncAckPacket
    ) {
        runCatching { sender(packet) }
            .onFailure { LOGGER.warn("Failed to send Katton script-pack acknowledgement {}", packet.revision, it) }
    }

    @Synchronized
    fun handleHashList(packet: ScriptPackHashListPacket, requestSender: (ScriptPackRequestPacket) -> Unit) {
        val generation = advanceConfigurationGeneration()
        pendingPacks = emptyList()
        activePacks = emptyList()
        val bucket = resolveCurrentServerBucket() ?: run {
            LOGGER.warn("Cannot resolve current server bucket, skipping script pack hash sync")
            return
        }

        activeServerBucket = bucket
        expectedHashes = packet.entries.associate { it.syncId to it.hash }

        if (packet.entries.isEmpty()) {
            // No packs on server: clear stale cache with an async client reload.
            syncState = RemoteSyncState.EXECUTING
            ScriptReloadManager.reloadClientScriptsAsync { success ->
                completeClientReload(generation, null, success)
            }
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
        val generation = advanceConfigurationGeneration()
        pendingPacks = emptyList()
        activePacks = emptyList()
        val bucket = resolveCurrentServerBucket() ?: run {
            LOGGER.warn("Cannot resolve current server bucket, skipping script pack hash sync")
            return true
        }

        activeServerBucket = bucket
        expectedHashes = packet.entries.associate { it.syncId to it.hash }

        if (packet.entries.isEmpty()) {
            syncState = RemoteSyncState.EXECUTING
            ScriptReloadManager.reloadClientScriptsAsync { success ->
                completeClientReload(generation, completeWhenDeferred, success)
            }
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
        // A server should send one configuration bundle, but advancing here also
        // makes duplicate/late bundles invalidate an older prompt or reload.
        val generation = advanceConfigurationGeneration()
        val bucket = activeServerBucket ?: resolveCurrentServerBucket() ?: return true
        val cachedRoot = resolveCachedRoot(bucket)
        syncState = RemoteSyncState.DOWNLOADING

        // verify signatures and persist packs to cache
        packet.packs.forEach { packData ->
            val expectedHash = expectedHashes[packData.syncId]
            if (expectedHash == null) {
                LOGGER.warn("Ignoring unexpected server pack {}", packData.syncId)
                return@forEach
            }
            if (packData.hash != expectedHash) {
                rejectRemoteScripts(
                    reason = "${packData.syncId}: bundle hash does not match the advertised snapshot",
                    disconnect = completeWhenDeferred != null
                )
                return true
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
            if (!persistPackBundle(cachedRoot, packData)) {
                rejectRemoteScripts(
                    reason = "${packData.syncId}: failed to persist a safe local snapshot",
                    disconnect = completeWhenDeferred != null
                )
                return true
            }
        }
        syncState = RemoteSyncState.CACHED

        val resolved = mutableListOf<ScriptPack>()
        val unresolved = mutableListOf<String>()

        expectedHashes.forEach { (syncId, expectedHash) ->
            val cached = loadCachedPack(cachedRoot, syncId, expectedHash)
            if (cached == null) {
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
        val candidatePacks = resolved.toList()
        pendingPacks = candidatePacks

        // If the remote server is untrusted or a signing key is new, require an
        // explicit decision before any downloaded bytecode or source executes.
        if (!RemoteScriptTrustStore.isTrusted(identity.bucket) || hasUntrustedSigningKeys(resolved)) {
            activePacks = emptyList()
            syncState = RemoteSyncState.PENDING_TRUST
            if (completeWhenDeferred == null) {
                LOGGER.warn("Skipping remote script execution from untrusted server or signing key {}", identity.address)
                return true
            }
            ScriptPackUi.openRemoteScriptTrustScreen(identity.address, candidatePacks) { trusted ->
                finishTrustDecision(identity, candidatePacks, generation, trusted, completeWhenDeferred)
            }
            return false
        }

        activePacks = candidatePacks
        pendingPacks = emptyList()
        syncState = RemoteSyncState.TRUSTED
        return executeTrustedPacks(generation, completeWhenDeferred)
    }

    @Synchronized
    private fun finishTrustDecision(
        identity: RemoteServerIdentity,
        candidatePacks: List<ScriptPack>,
        generation: Long,
        trusted: Boolean,
        completeWhenDeferred: (() -> Unit)? = null
    ) {
        if (generation != configurationSyncGeneration ||
            activeServerBucket != identity.bucket ||
            pendingPacks !== candidatePacks
        ) {
            LOGGER.debug("Ignoring stale Katton remote-script trust decision for {}", identity.address)
            completeWhenDeferred?.invoke()
            return
        }
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
        trustPackSigningKeys(identity, candidatePacks)
        activePacks = candidatePacks
        pendingPacks = emptyList()
        syncState = RemoteSyncState.TRUSTED
        executeTrustedPacks(generation, completeWhenDeferred)
    }

    private fun executeTrustedPacks(
        generation: Long,
        completeWhenDeferred: (() -> Unit)? = null
    ): Boolean {
        // Keep configuration networking waiting when requested, but move the
        // heavy client scan/compile phase onto the client reload worker.
        syncState = RemoteSyncState.EXECUTING
        ScriptReloadManager.reloadClientScriptsAsync { success ->
            completeClientReload(generation, completeWhenDeferred, success)
        }
        return completeWhenDeferred == null
    }

    @Synchronized
    private fun completeClientReload(
        generation: Long,
        completeWhenDeferred: (() -> Unit)?,
        success: Boolean = true
    ) {
        if (generation != configurationSyncGeneration) {
            completeWhenDeferred?.invoke()
            return
        }
        syncState = if (success) RemoteSyncState.IDLE else RemoteSyncState.REJECTED
        if (!success && completeWhenDeferred != null) {
            ScriptPackUi.disconnectRemoteScripts("Katton server scripts failed dependency validation, compilation, or execution")
        }
        completeWhenDeferred?.invoke()
    }

    /** Advances without ever using zero, which remains the uninitialized marker. */
    private fun advanceConfigurationGeneration(): Long {
        configurationSyncGeneration = if (configurationSyncGeneration == Long.MAX_VALUE) {
            1L
        } else {
            configurationSyncGeneration + 1L
        }
        return configurationSyncGeneration
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
            val verification = RemoteScriptSignatureVerifier.verify(pack)
            if (!verification.valid) {
                LOGGER.warn("Refusing to trust an invalid cached signature for {}: {}", pack.syncId, verification.reason)
                return@forEach
            }
            val fingerprint = verification.keyFingerprint ?: return@forEach
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

    private fun loadCachedPack(cachedRoot: Path, syncId: String, expectedHash: String): ScriptPack? {
        val packDirectory = cachedRoot.resolve(encodeSyncId(syncId))
        return ScriptPackManager.scanCachedPackContainer(
            containerDirectory = packDirectory,
            syncId = syncId,
            expectedHash = expectedHash
        )
    }

    private fun persistPackBundle(cachedRoot: Path, packData: ScriptPackBundlePacket.PackData): Boolean {
        val packDirectory = cachedRoot.resolve(encodeSyncId(packData.syncId)).toAbsolutePath().normalize()
        val manifestPath = packDirectory.resolve(MANIFEST_FILE_NAME)

        return runCatching {
            deleteDirectory(packDirectory)
            Files.createDirectories(packDirectory)
            Files.writeString(
                manifestPath,
                packData.manifestJson,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )

            val outputPaths = HashSet<Path>(packData.files.size)
            packData.files.forEach { fileData ->
                val relative = Path.of(fileData.relativePath)
                require(!relative.isAbsolute) { "absolute pack path is not allowed: ${fileData.relativePath}" }
                val output = packDirectory.resolve(relative).normalize()
                require(output.startsWith(packDirectory) && output != packDirectory) {
                    "pack path escapes its cache directory: ${fileData.relativePath}"
                }
                require(output != manifestPath) { "pack content may not overwrite $MANIFEST_FILE_NAME" }
                require(outputPaths.add(output)) { "duplicate pack output path: ${fileData.relativePath}" }
                output.parent?.let(Files::createDirectories)
                Files.write(
                    output,
                    fileData.content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
            }
            true
        }.onFailure {
            LOGGER.warn("Failed to persist server pack {}", packData.syncId, it)
            runCatching { deleteDirectory(packDirectory) }
        }.getOrDefault(false)
    }

    private fun resolveCachedRoot(bucket: String): Path {
        val gameDir = Katton.gameDirectory ?: error("Game directory is not initialized")
        if (Files.exists(gameDir.resolve("serverpacks"))) LOGGER.warn("Legacy Katton serverpacks cache is incompatible; rebuilding in serverpacks-v3")
        val serverPacksRoot = ensureRealDirectory(gameDir.resolve(SERVER_PACKS_DIR_NAME), "server-pack cache")
        return ensureRealDirectory(serverPacksRoot.resolve(bucket), "server cache bucket")
    }

    private fun resolveCurrentServerBucket(): String? {
        return resolveCurrentServerIdentity()?.bucket
    }

    private fun resolveCurrentServerIdentity(bucketOverride: String? = null): RemoteServerIdentity? {
        val mc = Minecraft.getInstance()
        val address = normalizeAddress(mc.currentServer?.ip)
            ?: connectingServerAddress
            ?: resolveConnectionAddress(mc)
            ?: return null
        return RemoteServerIdentity(bucketOverride ?: sha256(address), address)
    }

    private fun resolveRevisionRoot(bucket: String, revision: Long): Path {
        val revisionsRoot = ensureRealDirectory(
            resolveCachedRoot(bucket).resolve(REVISIONS_DIR_NAME),
            "server-pack revisions"
        )
        return ensureRealDirectory(revisionsRoot.resolve(revision.toString()), "server-pack revision")
    }

    /** Keeps only the active immutable snapshot; failed and superseded revisions are disposable. */
    private fun cleanupRevisionDirectories(bucket: String, keepRevision: Long?) {
        val revisionsRoot = resolveCachedRoot(bucket).resolve(REVISIONS_DIR_NAME)
        if (!Files.isDirectory(revisionsRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(revisionsRoot)) return
        runCatching {
            Files.newDirectoryStream(revisionsRoot).use { revisions ->
                revisions.forEach { revisionPath ->
                    if (revisionPath.fileName.toString() != keepRevision?.toString()) {
                        deleteDirectory(revisionPath)
                    }
                }
            }
        }.onFailure {
            LOGGER.warn("Failed to clean stale Katton server-pack revisions for {}", bucket, it)
        }
    }

    private fun deleteRevisionQuietly(bucket: String, revision: Long) {
        if (revision <= 0L) return
        val revisionPath = resolveCachedRoot(bucket)
            .resolve(REVISIONS_DIR_NAME)
            .resolve(revision.toString())
        runCatching { deleteDirectory(revisionPath) }
            .onFailure { LOGGER.debug("Failed to delete stale script-pack revision {}", revision, it) }
    }

    /**
     * Rebuilds a new revision from the bytes captured by the validated scan.
     * Re-reading [ScriptPack.location] here would let a local edit race the hash
     * check and would waste time copying ignored files from a directory pack.
     */
    private fun copyCachedPack(pack: ScriptPack, target: Path) {
        Files.createDirectories(target)
        val manifestPath = target.resolve(MANIFEST_FILE_NAME)
        SafePackFileIo.writeUtf8Atomically(
            manifestPath,
            pack.manifestJson,
            ScriptPackFileLimits.MAX_MANIFEST_BYTES,
            "cached script pack manifest"
        )

        val outputPaths = HashSet<Path>(pack.contentFiles.size)
        pack.contentFiles.forEach { file ->
            val relative = Path.of(file.relativePath)
            require(!relative.isAbsolute) { "Cached pack path must be relative: ${file.relativePath}" }
            val output = target.resolve(relative).normalize()
            require(output.startsWith(target) && output != target && output != manifestPath) {
                "Cached pack path escapes its revision container: ${file.relativePath}"
            }
            require(outputPaths.add(output)) { "Duplicate cached pack path: ${file.relativePath}" }
            output.parent?.let(Files::createDirectories)
            Files.write(
                output,
                file.bytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            )
        }
    }

    private fun normalizeAddress(address: String?): String? {
        val normalized = address
            ?.trim()
            ?.lowercase(Locale.ROOT)
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
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return
        }

        Files.walk(path).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    /** Cache components must be real directories so cleanup cannot traverse a planted link. */
    private fun ensureRealDirectory(path: Path, label: String): Path {
        val normalized = path.toAbsolutePath().normalize()
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            runCatching { Files.createDirectory(normalized) }
                .onFailure { failure ->
                    if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) throw failure
                }
        }
        require(Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(normalized)) {
            "$label path is not a safe directory: $normalized"
        }
        return normalized
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(text.toByteArray(StandardCharsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
