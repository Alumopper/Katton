package top.katton.network

import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.CLIENTBOUND_PLAY
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.SERVERBOUND_CONFIGURATION
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl.SERVERBOUND_PLAY

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
        if (CLIENTBOUND_PLAY.get(AudioPacket.TYPE.id) == null) {
            CLIENTBOUND_PLAY.register(AudioPacket.TYPE, AudioPacket.STREAM_CODEC)
            SERVERBOUND_PLAY.register(AudioPacket.TYPE, AudioPacket.STREAM_CODEC)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(AudioPacket.TYPE) { packet, context ->
                context.server().execute { top.katton.api.audio.AudioServerTransport.receive(context.player(), packet) }
            }
        }
        top.katton.api.audio.AudioServerTransport.send = { player, packet ->
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, AudioPacket.TYPE)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, packet)
                true
            } else false
        }
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

        // Play-phase: server → client data sync
        if(CLIENTBOUND_PLAY.get(ClientDataSyncPacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(ClientDataSyncPacket.TYPE, ClientDataSyncPacket.STREAM_CODEC)
        }

        if(CLIENTBOUND_PLAY.get(ClientItemRenderMarkerPacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(ClientItemRenderMarkerPacket.TYPE, ClientItemRenderMarkerPacket.STREAM_CODEC)
        }

        if(CLIENTBOUND_PLAY.get(ScriptPackHashListPacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(ScriptPackHashListPacket.TYPE, ScriptPackHashListPacket.STREAM_CODEC)
        }
        if(CLIENTBOUND_PLAY.get(ScriptPackBundlePacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(ScriptPackBundlePacket.TYPE, ScriptPackBundlePacket.STREAM_CODEC)
        }
        if(SERVERBOUND_PLAY.get(ScriptPackRequestPacket.TYPE.id) == null){
            SERVERBOUND_PLAY.register(ScriptPackRequestPacket.TYPE, ScriptPackRequestPacket.STREAM_CODEC)
        }
        if(SERVERBOUND_PLAY.get(ScriptPackSyncAckPacket.TYPE.id) == null){
            SERVERBOUND_PLAY.register(ScriptPackSyncAckPacket.TYPE, ScriptPackSyncAckPacket.STREAM_CODEC)
        }

        if(CLIENTBOUND_PLAY.get(ClientScenePacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(ClientScenePacket.TYPE, ClientScenePacket.STREAM_CODEC)
        }
        if(CLIENTBOUND_PLAY.get(ClientPostEffectPacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(ClientPostEffectPacket.TYPE, ClientPostEffectPacket.STREAM_CODEC)
        }
    }
}
