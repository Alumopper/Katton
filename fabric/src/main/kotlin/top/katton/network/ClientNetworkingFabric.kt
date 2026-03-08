package top.katton.network

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking

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
                // Queue items for registration
                pendingItems.addAll(packet.items)
            }
        }

        ClientConfigurationNetworking.registerGlobalReceiver(EffectSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                // Queue effects for registration
                pendingEffects.addAll(packet.effects)
            }
        }

        ClientConfigurationNetworking.registerGlobalReceiver(BlockSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                // Queue blocks for registration
                pendingBlocks.addAll(packet.blocks)
            }
        }
    }

}
