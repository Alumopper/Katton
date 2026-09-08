package top.katton.network

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackManager
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

fun interface ServerConfigurationNetworkingSender {
   operator fun invoke(handler: ServerConfigurationPacketListenerImpl, payload: CustomPacketPayload)
}

fun interface ServerPlayNetworkingSender {
    operator fun invoke(player: ServerPlayer, payload: CustomPacketPayload)
}

/**
 * Server-side networking handler for Katton.
 * Handles sending item sync packets to connecting clients.
 */
object ServerNetworking {
    private val LOGGER = LoggerFactory.getLogger(ServerNetworking::class.java)
    // A slow client may need to finish one already-running compilation before
    // applying the latest coalesced revision. Keep this aligned with the server
    // reload wait budget instead of disconnecting healthy clients after 30 s.
    private val ACK_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(120L)
    private data class PendingAck(
        val revision: Long,
        val deadlineNanos: Long,
        /** TCP is reliable; a second bundle request for one revision is abusive or stale. */
        val bundleSent: AtomicBoolean = AtomicBoolean(false)
    )

    private data class PublishedPackSnapshot(
        val revision: Long,
        val packs: List<ScriptPack>
    )

    private val revisionCounter = AtomicLong(0L)
    private val pendingAcks = ConcurrentHashMap<UUID, PendingAck>()

    /**
     * Legacy hash-then-request configuration handlers need the exact pack bytes
     * that produced their hash packet. Weak keys avoid retaining disconnected
     * configuration listeners that never send a request.
     */
    private val configurationSnapshots = Collections.synchronizedMap(
        WeakHashMap<ServerConfigurationPacketListenerImpl, List<ScriptPack>>()
    )

    /**
     * Revision and pack bytes must be observed as one value. Separate volatile
     * fields could pair revision N with revision N+1's bytes during a reload.
     */
    @Volatile
    private var publishedSnapshot = PublishedPackSnapshot(0L, emptyList())

    @Volatile
    private var playSender: ServerPlayNetworkingSender? = null

    @JvmStatic
    fun setPlaySender(sender: ServerPlayNetworkingSender?) {
        playSender = sender
    }

    /**
     * Sends a play-phase custom payload to a specific player.
     * The playSender must be initialized by the platform entrypoint.
     */
    @JvmStatic
    fun sendPlayPacket(player: ServerPlayer, payload: CustomPacketPayload) {
        playSender?.invoke(player, payload)
    }

    /**
     * Sends script pack hash snapshot during configuration.
     * This packet is always sent so the client can clear stale cached packs when empty.
     */
    @JvmStatic
    fun sendScriptPackHashPacket(handler: ServerConfigurationPacketListenerImpl, sender: ServerConfigurationNetworkingSender) {
        val snapshot = collectSyncSnapshot()
        configurationSnapshots[handler] = snapshot
        try {
            sender(handler, createScriptPackHashPacket(snapshot, 0L))
        } catch (failure: Throwable) {
            configurationSnapshots.remove(handler)
            throw failure
        }
    }

    /**
     * Sends the full configuration-time script sync payload in stream order:
     * hashes first, then the full bundle snapshot when packs exist.
     *
     * The client-side registry sync hook depends on the bundle packet being
     * available before registry validation starts, so this path avoids an
     * extra request/response round-trip during login.
     */
    @JvmStatic
    fun sendInitialScriptPackSync(handler: ServerConfigurationPacketListenerImpl, sender: ServerConfigurationNetworkingSender) {
        configurationSnapshots.remove(handler)
        val snapshot = collectSyncSnapshot()
        val hashPacket = createScriptPackHashPacket(snapshot, 0L)
        sender(handler, hashPacket)
        if (hashPacket.entries.isEmpty()) {
            return
        }
        sender(handler, createScriptPackBundlePacket(hashPacket.entries.map { it.syncId }, 0L, snapshot))
    }

    /**
     * Sends scripts to client if needed.
     */
    @JvmStatic
    fun sendScriptPackBundle(
        handler: ServerConfigurationPacketListenerImpl,
        requestedSyncIds: List<String>,
        sender: ServerConfigurationNetworkingSender
    ) {
        val snapshot = configurationSnapshots.remove(handler) ?: collectSyncSnapshot()
        val packet = createScriptPackBundlePacket(requestedSyncIds, 0L, snapshot)
        if (packet.packs.isEmpty()) {
            return
        }
        sender(handler, packet)
    }

    fun createScriptPackBundlePacket(
        requestedSyncIds: List<String>,
        revision: Long = 0L
    ): ScriptPackBundlePacket = createScriptPackBundlePacket(
        requestedSyncIds,
        revision,
        collectSyncSnapshot()
    )

    private fun createScriptPackBundlePacket(
        requestedSyncIds: List<String>,
        revision: Long,
        snapshot: List<ScriptPack>
    ): ScriptPackBundlePacket {
        if (requestedSyncIds.isEmpty()) {
            return ScriptPackBundlePacket(emptyList(), revision)
        }

        val requestedSet = requestedSyncIds.toSet()
        val packs = snapshot
            .asSequence()
            .filter { it.syncId in requestedSet }
            .map { pack ->
                ScriptPackBundlePacket.PackData(
                    syncId = pack.syncId,
                    scope = pack.scope.serializedName,
                    hash = pack.hash,
                    manifestJson = pack.manifestJson,
                    files = pack.contentFiles.map { file ->
                        ScriptPackBundlePacket.ScriptFileData(
                            relativePath = file.relativePath,
                            content = file.bytes
                        )
                    }
                )
            }
            .toList()

        return ScriptPackBundlePacket(packs, revision)
    }

    fun createScriptPackHashPacket(revision: Long = 0L): ScriptPackHashListPacket {
        return createScriptPackHashPacket(collectSyncSnapshot(), revision)
    }

    private fun createScriptPackHashPacket(
        snapshot: List<ScriptPack>,
        revision: Long
    ): ScriptPackHashListPacket {
        val entries = snapshot
            .map { pack ->
                ScriptPackHashListPacket.HashEntry(
                    syncId = pack.syncId,
                    scope = pack.scope.serializedName,
                    hash = pack.hash,
                    name = pack.manifest.name
                )
            }
        return ScriptPackHashListPacket(entries, revision)
    }

    /** Publishes a play-phase snapshot after a successful server reload. */
    @JvmStatic
    fun publishPackRevision(server: MinecraftServer): Long {
        // Capture once before publishing. A later reload may refresh the pack
        // manager while a slow client is still requesting this revision.
        val snapshot = collectSyncSnapshot()
        val revision = revisionCounter.incrementAndGet()
        publishedSnapshot = PublishedPackSnapshot(revision, snapshot)
        pendingAcks.clear()
        val packet = createScriptPackHashPacket(snapshot, revision)
        // nanoTime is monotonic; wall-clock corrections must not instantly time
        // out clients or extend a synchronization deadline indefinitely.
        val deadline = System.nanoTime() + ACK_TIMEOUT_NANOS
        server.playerList.players
            .filterNot { player -> !server.isDedicatedServer && server.isSingleplayerOwner(player.nameAndId()) }
            .forEach { player ->
                pendingAcks[player.uuid] = PendingAck(revision, deadline)
                sendPlayPacket(player, packet)
            }
        LOGGER.info("Published Katton script-pack revision {} to {} remote players", revision, pendingAcks.size)
        return revision
    }

    @JvmStatic
    fun handlePlayRequest(player: ServerPlayer, request: ScriptPackRequestPacket) {
        val pending = pendingAcks[player.uuid] ?: return
        val published = publishedSnapshot
        if (request.revision != published.revision || request.revision != pending.revision) {
            sendPlayPacket(player, createScriptPackHashPacket(published.packs, published.revision))
            return
        }
        if (!pending.bundleSent.compareAndSet(false, true)) {
            LOGGER.debug(
                "Ignoring duplicate Katton script-pack request from {} for revision {}",
                player.scoreboardName,
                request.revision
            )
            return
        }
        try {
            sendPlayPacket(
                player,
                createScriptPackBundlePacket(request.requestedSyncIds, request.revision, published.packs)
            )
        } catch (failure: Throwable) {
            // Allow one retry when packet construction or the platform sender
            // failed synchronously; an encoded TCP packet itself is reliable.
            pending.bundleSent.set(false)
            throw failure
        }
    }

    @JvmStatic
    fun handleSyncAck(player: ServerPlayer, ack: ScriptPackSyncAckPacket) {
        val pending = pendingAcks[player.uuid]
        if (pending == null || pending.revision != ack.revision) return
        // Do not let a delayed acknowledgement remove the pending state that a
        // concurrently published newer revision installed for this player.
        if (!pendingAcks.remove(player.uuid, pending)) return
        if (ack.success) {
            LOGGER.info("Player {} applied Katton script-pack revision {}", player.scoreboardName, ack.revision)
        } else {
            val safeMessage = sanitizePeerMessage(ack.message)
            LOGGER.warn("Player {} rejected Katton script-pack revision {}: {}", player.scoreboardName, ack.revision, safeMessage)
            player.connection.disconnect(Component.literal("Katton script-pack sync failed: $safeMessage"))
        }
    }

    /** Called from a server tick hook to enforce revision acknowledgements. */
    @JvmStatic
    fun pollSyncTimeouts(server: MinecraftServer) {
        val now = System.nanoTime()
        pendingAcks.entries.removeIf { (playerId, pending) ->
            if (pending.deadlineNanos - now > 0L) return@removeIf false
            server.playerList.getPlayer(playerId)?.let { player ->
                LOGGER.warn("Player {} timed out applying Katton script-pack revision {}", player.scoreboardName, pending.revision)
                player.connection.disconnect(Component.literal("Timed out applying Katton script-pack revision ${pending.revision}"))
            }
            true
        }
    }

    @JvmStatic
    fun resetPackRevisions() {
        pendingAcks.clear()
        configurationSnapshots.clear()
        publishedSnapshot = PublishedPackSnapshot(0L, emptyList())
    }

    /**
     * Enforces bundle-wide limits before a platform starts encoding or sends a
     * hash list that it cannot later satisfy with one bounded bundle.
     */
    private fun collectSyncSnapshot(): List<ScriptPack> {
        val packs = ScriptPackManager.collectServerSyncPacks().toList()
        val graph = top.katton.pack.ScriptPackDependencyGraph.resolve(packs)
        require(graph.invalidPacks.isEmpty()) { "Synchronized pack dependencies must be contained in the sync set: ${graph.errors.joinToString()}" }
        require(packs.size <= ScriptPackPacketLimits.MAX_PACKS) {
            "Too many client-synchronized script packs (${packs.size}, maximum ${ScriptPackPacketLimits.MAX_PACKS})"
        }

        var fileCount = 0L
        var contentBytes = 0L
        packs.forEach { pack ->
            fileCount += pack.contentFiles.size
            contentBytes += pack.manifestJson.toByteArray(StandardCharsets.UTF_8).size
            pack.contentFiles.forEach { file -> contentBytes += file.bytes.size }
        }
        require(fileCount <= ScriptPackPacketLimits.MAX_FILES_PER_BUNDLE) {
            "Client-synchronized packs contain too many files " +
                "($fileCount, maximum ${ScriptPackPacketLimits.MAX_FILES_PER_BUNDLE})"
        }
        require(contentBytes <= ScriptPackPacketLimits.MAX_BUNDLE_CONTENT_BYTES) {
            "Client-synchronized packs exceed the bundle byte budget " +
                "($contentBytes, maximum ${ScriptPackPacketLimits.MAX_BUNDLE_CONTENT_BYTES})"
        }
        return packs
    }

    /** Prevents a remote acknowledgement from injecting control characters into logs/UI. */
    private fun sanitizePeerMessage(message: String): String = buildString(minOf(message.length, 256)) {
        message.take(256).forEach { character ->
            append(
                if (character.isISOControl() || character == '\u2028' || character == '\u2029') ' '
                else character
            )
        }
    }.trim().ifEmpty { "unspecified client error" }
}
