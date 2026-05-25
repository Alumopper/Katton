package top.katton.network

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import top.katton.client.ClientItemRenderMarkerManager
import top.katton.client.ClientPostEffectManager
import top.katton.pack.ServerPackCacheManager

/**
 * Client-side networking handler for Katton.
 * Handles receiving item sync packets and registering items on client.
 */
object ClientNetworkingFabric {
    /**
     * Initializes client networking.
     * Registers packet handlers. Payload type is registered in common code.
     */
    fun initialize() {
        ClientConfigurationNetworking.registerGlobalReceiver(ScriptPackHashListPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            ServerPackCacheManager.prepareMainThreadSync()
            context.client().execute {
                try {
                    ServerPackCacheManager.handleHashList(packet) { request ->
                        ClientConfigurationNetworking.send(request)
                    }
                } finally {
                    ServerPackCacheManager.completeMainThreadSync()
                }
            }
            ServerPackCacheManager.awaitMainThreadSync()
        }

        ClientConfigurationNetworking.registerGlobalReceiver(ScriptPackBundlePacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            ServerPackCacheManager.prepareMainThreadSync()
            context.client().execute {
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

        ClientPlayNetworking.registerGlobalReceiver(ClientDataSyncPacket.TYPE) { packet, context ->
            context.client().execute {
                ClientDataManager.putAll(packet.entries)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ClientItemRenderMarkerPacket.TYPE) { packet, context ->
            context.client().execute {
                ClientItemRenderMarkerManager.handlePacket(packet)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ClientPostEffectPacket.TYPE) { packet, context ->
            context.client().execute {
                ClientPostEffectManager.handlePacket(packet)
            }
        }
    }

}
