package top.katton.network

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import top.katton.pack.ScriptPackManager

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

    @Volatile
    private var playSender: ServerPlayNetworkingSender? = null

    @JvmStatic
    fun setPlaySender(sender: ServerPlayNetworkingSender?) {
        playSender = sender
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
        val hashPacket = createScriptPackHashPacket()
        sender(handler, hashPacket)
        if (hashPacket.entries.isEmpty()) {
            return
        }
        sender(handler, createScriptPackBundlePacket(hashPacket.entries.map { it.syncId }))
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
        val packet = createScriptPackBundlePacket(requestedSyncIds)
        if (packet.packs.isEmpty()) {
            return
        }
        sender(handler, packet)
    }

    fun createScriptPackBundlePacket(requestedSyncIds: List<String>): ScriptPackBundlePacket {
        if (requestedSyncIds.isEmpty()) {
            return ScriptPackBundlePacket(emptyList())
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

        return ScriptPackBundlePacket(packs)
    }

    fun createScriptPackHashPacket(): ScriptPackHashListPacket {
        val entries = ScriptPackManager.collectServerSyncPacks()
            .map { pack ->
                ScriptPackHashListPacket.HashEntry(
                    syncId = pack.syncId,
                    scope = pack.scope.serializedName,
                    hash = pack.hash,
                    name = pack.manifest.name
                )
            }
        return ScriptPackHashListPacket(entries)
    }
}
