package top.katton.network

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import top.katton.pack.ServerPackCacheManager

/**
 * Client-side networking handler for Katton.
 * Handles receiving item sync packets and registering items on client.
 */
object ClientNetworkingFabric: ClientNetworking() {
    /**
     * Initializes client networking.
     * Registers packet handlers. Payload type is registered in common code.
     */
    fun initialize() {
        // Register packet handler
        ClientConfigurationNetworking.registerGlobalReceiver(ItemSyncPacket.TYPE) { packet, context ->
            if(context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                queueItemSnapshot(packet.items)
            }
        }

        ClientConfigurationNetworking.registerGlobalReceiver(EffectSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                queueEffectSnapshot(packet.effects)
            }
        }

        ClientConfigurationNetworking.registerGlobalReceiver(BlockSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                queueBlockSnapshot(packet.blocks)
            }
        }

        ClientConfigurationNetworking.registerGlobalReceiver(ScriptPackHashListPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                ServerPackCacheManager.handleHashList(packet) { request ->
                    ClientConfigurationNetworking.send(request)
                }
            }
        }

        ClientConfigurationNetworking.registerGlobalReceiver(ScriptPackBundlePacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                ServerPackCacheManager.handleBundle(packet)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(ItemSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                applyItemSnapshot(packet.items)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(EffectSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                applyEffectSnapshot(packet.effects)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(BlockSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                applyBlockSnapshot(packet.blocks)
            }
        }
    }

}
