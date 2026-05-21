package top.katton.network

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.LoggerFactory
import top.katton.pack.ServerPackCacheManager

/**
 * Registers all Katton payload types and handlers on both sides.
 *
 * Registered manually in KattonNeoForge's mod constructor via
 * `modEventBus.addListener()` since RegisterPayloadHandlersEvent
 * fires on the mod bus (not accessible via @EventBusSubscriber).
 */
object ServerNetworkingNeoForge {

    private val LOGGER = LoggerFactory.getLogger(ServerNetworkingNeoForge::class.java)

    @SubscribeEvent
    fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        LOGGER.info("Registering Katton payload handlers")
        val registrar = event.registrar("1")

        // Server → Client: script pack hash list
        registrar.configurationToClient(ScriptPackHashListPacket.TYPE, ScriptPackHashListPacket.STREAM_CODEC) { packet, context ->
            if (context.connection().isMemoryConnection) return@configurationToClient
            ServerPackCacheManager.prepareMainThreadSync()
            context.enqueueWork {
                try {
                    ServerPackCacheManager.handleHashList(packet) { request ->
                        context.reply(request)
                    }
                } finally {
                    ServerPackCacheManager.completeMainThreadSync()
                }
            }
            ServerPackCacheManager.awaitMainThreadSync()
        }

        // Server → Client: script pack bundle
        registrar.configurationToClient(ScriptPackBundlePacket.TYPE, ScriptPackBundlePacket.STREAM_CODEC) { packet, context ->
            if (context.connection().isMemoryConnection) return@configurationToClient
            ServerPackCacheManager.prepareMainThreadSync()
            context.enqueueWork {
                var completedImmediately = true
                try {
                    completedImmediately = ServerPackCacheManager.handleBundleWithTrustPrompt(packet) {
                        ServerPackCacheManager.completeMainThreadSync()
                    }
                } finally {
                    if (completedImmediately) {
                        ServerPackCacheManager.completeMainThreadSync()
                    }
                }
            }
            ServerPackCacheManager.awaitMainThreadSync()
        }

        // Client → Server: script pack request
        registrar.configurationToServer(ScriptPackRequestPacket.TYPE, ScriptPackRequestPacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork {
                val response = ServerNetworking.createScriptPackBundlePacket(packet.requestedSyncIds)
                if (response.packs.isNotEmpty()) {
                    context.reply(response)
                }
            }
        }

        // Server → Client: play-phase data sync
        registrar.playToClient(ClientDataSyncPacket.TYPE, ClientDataSyncPacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork {
                ClientDataManager.putAll(packet.entries)
            }
        }
    }
}
