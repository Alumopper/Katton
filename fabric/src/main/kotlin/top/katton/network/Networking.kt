package top.katton.network

import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.CLIENTBOUND_PLAY
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.SERVERBOUND_CONFIGURATION

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

        //when a client connects, the server will send a ScriptPackHashListPacket to the client
        if(PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(ScriptPackHashListPacket.TYPE.id) == null){
            PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(ScriptPackHashListPacket.TYPE, ScriptPackHashListPacket.STREAM_CODEC)
        }

        //after checking the hash list, the client will request the script pack bundle if needed
        if(SERVERBOUND_CONFIGURATION.get(ScriptPackRequestPacket.TYPE.id) == null){
            SERVERBOUND_CONFIGURATION.register(ScriptPackRequestPacket.TYPE, ScriptPackRequestPacket.STREAM_CODEC)
        }

        if(PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(ScriptPackBundlePacket.TYPE.id) == null){
            PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(ScriptPackBundlePacket.TYPE, ScriptPackBundlePacket.STREAM_CODEC)
        }
    }
}
