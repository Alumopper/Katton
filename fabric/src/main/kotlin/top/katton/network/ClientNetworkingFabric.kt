package top.katton.network

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.impl.menu.client.ClientNetworking
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
    }

}
