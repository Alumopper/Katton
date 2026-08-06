package top.katton.network

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import top.katton.pack.ScriptPackManager
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    private const val ACK_TIMEOUT_MILLIS = 30_000L
    private data class PendingAck(val revision: Long, val deadlineMillis: Long)

    private val revisionCounter = AtomicLong(0L)
    private val pendingAcks = ConcurrentHashMap<UUID, PendingAck>()

    @Volatile
    private var publishedRevision = 0L

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
        sender(handler, createScriptPackHashPacket())
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
        val hashPacket = createScriptPackHashPacket(0L)
        sender(handler, hashPacket)
        if (hashPacket.entries.isEmpty()) {
            return
        }
        sender(handler, createScriptPackBundlePacket(hashPacket.entries.map { it.syncId }, 0L))
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
        val packet = createScriptPackBundlePacket(requestedSyncIds, 0L)
        if (packet.packs.isEmpty()) {
            return
        }
        sender(handler, packet)
    }

    fun createScriptPackBundlePacket(
        requestedSyncIds: List<String>,
        revision: Long = 0L
    ): ScriptPackBundlePacket {
        if (requestedSyncIds.isEmpty()) {
            return ScriptPackBundlePacket(emptyList(), revision)
        }

        val requestedSet = requestedSyncIds.toSet()
        val packs = ScriptPackManager.collectServerSyncPacks()
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
        val entries = ScriptPackManager.collectServerSyncPacks()
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
        val revision = revisionCounter.incrementAndGet()
        publishedRevision = revision
        pendingAcks.clear()
        val packet = createScriptPackHashPacket(revision)
        val deadline = System.currentTimeMillis() + ACK_TIMEOUT_MILLIS
        server.playerList.players
            .filterNot { !server.isDedicatedServer && server.isSingleplayerOwner(it.nameAndId()) }
            .forEach { player ->
            pendingAcks[player.uuid] = PendingAck(revision, deadline)
            sendPlayPacket(player, packet)
        }
        LOGGER.info("Published Katton script-pack revision {} to {} remote players", revision, pendingAcks.size)
        return revision
    }

    @JvmStatic
    fun handlePlayRequest(player: ServerPlayer, request: ScriptPackRequestPacket) {
        if (request.revision != publishedRevision) {
            sendPlayPacket(player, createScriptPackHashPacket(publishedRevision))
            return
        }
        sendPlayPacket(player, createScriptPackBundlePacket(request.requestedSyncIds, request.revision))
    }

    @JvmStatic
    fun handleSyncAck(player: ServerPlayer, ack: ScriptPackSyncAckPacket) {
        val pending = pendingAcks[player.uuid]
        if (pending == null || pending.revision != ack.revision) return
        pendingAcks.remove(player.uuid)
        if (ack.success) {
            LOGGER.info("Player {} applied Katton script-pack revision {}", player.scoreboardName, ack.revision)
        } else {
            LOGGER.warn("Player {} rejected Katton script-pack revision {}: {}", player.scoreboardName, ack.revision, ack.message)
            player.connection.disconnect(Component.literal("Katton script-pack sync failed: ${ack.message}"))
        }
    }

    /** Called from a server tick hook to enforce revision acknowledgements. */
    @JvmStatic
    fun pollSyncTimeouts(server: MinecraftServer) {
        val now = System.currentTimeMillis()
        pendingAcks.entries.removeIf { (playerId, pending) ->
            if (pending.deadlineMillis > now) return@removeIf false
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
        publishedRevision = 0L
    }
}
