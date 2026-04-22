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
        if(PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(ItemSyncPacket.TYPE.id) == null){
            PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(ItemSyncPacket.TYPE, ItemSyncPacket.STREAM_CODEC)
        }
        if(CLIENTBOUND_PLAY.get(ItemSyncPacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(ItemSyncPacket.TYPE, ItemSyncPacket.STREAM_CODEC)
        }
        if(PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(EffectSyncPacket.TYPE.id) == null){
            PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(EffectSyncPacket.TYPE, EffectSyncPacket.STREAM_CODEC)
        }
        if(CLIENTBOUND_PLAY.get(EffectSyncPacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(EffectSyncPacket.TYPE, EffectSyncPacket.STREAM_CODEC)
        }
        if(PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(BlockSyncPacket.TYPE.id) == null){
            PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(BlockSyncPacket.TYPE, BlockSyncPacket.STREAM_CODEC)
        }
        if(CLIENTBOUND_PLAY.get(BlockSyncPacket.TYPE.id) == null){
            CLIENTBOUND_PLAY.register(BlockSyncPacket.TYPE, BlockSyncPacket.STREAM_CODEC)
        }

        if(PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(ScriptPackHashListPacket.TYPE.id) == null){
            PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(ScriptPackHashListPacket.TYPE, ScriptPackHashListPacket.STREAM_CODEC)
        }
        if(PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.get(ScriptPackBundlePacket.TYPE.id) == null){
            PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(ScriptPackBundlePacket.TYPE, ScriptPackBundlePacket.STREAM_CODEC)
        }
        if(SERVERBOUND_CONFIGURATION.get(ScriptPackRequestPacket.TYPE.id) == null){
            SERVERBOUND_CONFIGURATION.register(ScriptPackRequestPacket.TYPE, ScriptPackRequestPacket.STREAM_CODEC)
        }
    }
}
