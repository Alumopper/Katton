package top.katton.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.CLIENTBOUND_PLAY

/**
 * Common networking initialization.
 * Registers payload types that are used by both client and server.
 */
object Networking {
    
    /**
     * Initializes common networking.
     * Must be called during mod initialization before any packets are sent/received.
     */
    @Suppress("UnstableApiUsage")
    @JvmStatic
    fun initialize() {
        // Register payload type for server->client communication
        // This must be done on both sides before registering handlers
        if(PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(ItemSyncPacket.TYPE.id) == null){
            PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(ItemSyncPacket.TYPE, ItemSyncPacket.STREAM_CODEC)
        }
    }
}
