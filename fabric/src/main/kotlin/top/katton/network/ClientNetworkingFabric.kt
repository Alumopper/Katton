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
        ClientPlayNetworking.registerGlobalReceiver(AudioPacket.TYPE) { packet, context ->
            context.client().execute {
                top.katton.client.audio.ClientAudioNetwork.receive(packet) { response -> ClientPlayNetworking.send(response) }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(ClientScenePacket.TYPE) { packet, context ->
            context.client().execute { top.katton.client.scene.ClientSceneManager.handlePacket(packet) }
        }
        ClientConfigurationNetworking.registerGlobalReceiver(ScriptPackHashListPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            val sync = ServerPackCacheManager.beginMainThreadSync()
            try {
                context.client().execute {
                    var completedImmediately = true
                    try {
                        completedImmediately = ServerPackCacheManager.handleHashListWithCompletion(packet, { request ->
                            ClientConfigurationNetworking.send(request)
                        }) {
                            ServerPackCacheManager.completeMainThreadSync(sync)
                        }
                    } finally {
                        if (completedImmediately) {
                            ServerPackCacheManager.completeMainThreadSync(sync)
                        }
                    }
                }
                ServerPackCacheManager.awaitMainThreadSync(sync)
            } catch (failure: Throwable) {
                ServerPackCacheManager.completeMainThreadSync(sync)
                throw failure
            }
        }

        ClientConfigurationNetworking.registerGlobalReceiver(ScriptPackBundlePacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            val sync = ServerPackCacheManager.beginMainThreadSync()
            try {
                context.client().execute {
                    var completedImmediately = true
                    try {
                        completedImmediately = ServerPackCacheManager.handleBundleWithTrustPrompt(packet) {
                            ServerPackCacheManager.completeMainThreadSync(sync)
                        }
                    } finally {
                        if (completedImmediately) {
                            ServerPackCacheManager.completeMainThreadSync(sync)
                        }
                    }
                }
                ServerPackCacheManager.awaitMainThreadSync(sync)
            } catch (failure: Throwable) {
                ServerPackCacheManager.completeMainThreadSync(sync)
                throw failure
            }
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

        ClientPlayNetworking.registerGlobalReceiver(ScriptPackHashListPacket.TYPE) { packet, context ->
            context.client().execute {
                ServerPackCacheManager.handlePlayHashList(
                    packet,
                    ClientPlayNetworking::send,
                    ClientPlayNetworking::send
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ScriptPackBundlePacket.TYPE) { packet, context ->
            context.client().execute {
                ServerPackCacheManager.handlePlayBundle(packet)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ClientPostEffectPacket.TYPE) { packet, context ->
            context.client().execute {
                ClientPostEffectManager.handlePacket(packet)
            }
        }
    }

}
