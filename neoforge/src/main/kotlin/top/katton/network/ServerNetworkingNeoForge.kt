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
            val sync = ServerPackCacheManager.beginMainThreadSync()
            try {
                context.enqueueWork {
                    var completedImmediately = true
                    try {
                        completedImmediately = ServerPackCacheManager.handleHashListWithCompletion(packet, { request ->
                            context.reply(request)
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

        // Server → Client: script pack bundle
        registrar.configurationToClient(ScriptPackBundlePacket.TYPE, ScriptPackBundlePacket.STREAM_CODEC) { packet, context ->
            if (context.connection().isMemoryConnection) return@configurationToClient
            val sync = ServerPackCacheManager.beginMainThreadSync()
            try {
                context.enqueueWork {
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

        registrar.playToClient(ClientItemRenderMarkerPacket.TYPE, ClientItemRenderMarkerPacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork {
                handleClientItemRenderMarkerPacket(packet)
            }
        }

        registrar.playToClient(ScriptPackHashListPacket.TYPE, ScriptPackHashListPacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork {
                ServerPackCacheManager.handlePlayHashList(packet, context::reply, context::reply)
            }
        }

        registrar.playToClient(ScriptPackBundlePacket.TYPE, ScriptPackBundlePacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork { ServerPackCacheManager.handlePlayBundle(packet) }
        }

        registrar.playToServer(ScriptPackRequestPacket.TYPE, ScriptPackRequestPacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork {
                val player = context.player() as? net.minecraft.server.level.ServerPlayer
                    ?: return@enqueueWork
                ServerNetworking.handlePlayRequest(player, packet)
            }
        }

        registrar.playToServer(ScriptPackSyncAckPacket.TYPE, ScriptPackSyncAckPacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork {
                val player = context.player() as? net.minecraft.server.level.ServerPlayer
                    ?: return@enqueueWork
                ServerNetworking.handleSyncAck(player, packet)
            }
        }

        registrar.playToClient(ClientPostEffectPacket.TYPE, ClientPostEffectPacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork {
                handleClientPostEffectPacket(packet)
            }
        }
    }

    private fun handleClientItemRenderMarkerPacket(packet: ClientItemRenderMarkerPacket) {
        runCatching {
            val managerClass = Class.forName("top.katton.client.ClientItemRenderMarkerManager")
            val instance = managerClass.getField("INSTANCE").get(null)
            managerClass.getMethod("handlePacket", ClientItemRenderMarkerPacket::class.java).invoke(instance, packet)
        }.onFailure {
            LOGGER.warn("Failed to handle client item render marker packet", it)
        }
    }

    private fun handleClientPostEffectPacket(packet: ClientPostEffectPacket) {
        runCatching {
            val managerClass = Class.forName("top.katton.client.ClientPostEffectManager")
            val instance = managerClass.getField("INSTANCE").get(null)
            managerClass.getMethod("handlePacket", ClientPostEffectPacket::class.java).invoke(instance, packet)
        }.onFailure {
            LOGGER.warn("Failed to handle client post effect packet", it)
        }
    }
}
