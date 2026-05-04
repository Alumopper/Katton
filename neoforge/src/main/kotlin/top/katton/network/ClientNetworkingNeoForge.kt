package top.katton.network

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import top.katton.Katton
import top.katton.pack.ServerPackCacheManager

/**
 * Client-side networking handler for Katton.
 * Handles receiving item sync packets and registering items on client.
 */
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.CLIENT]
)
object ClientNetworkingNeoForge {

    @JvmStatic
    @SubscribeEvent
    fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")

        registrar.configurationToClient(ScriptPackHashListPacket.TYPE, ScriptPackHashListPacket.STREAM_CODEC) { packet, context ->
            if (context.connection().isMemoryConnection) return@configurationToClient
            ServerPackCacheManager.prepareMainThreadSync()
            context.enqueueWork {
                try {
                    ServerPackCacheManager.handleHashList(packet) { request ->
                        context.reply(request)
                    }
                } finally {
                    ServerPackCacheManager.completeMainThreadSync()
                }
            }
            ServerPackCacheManager.awaitMainThreadSync()
        }

        registrar.configurationToClient(ScriptPackBundlePacket.TYPE, ScriptPackBundlePacket.STREAM_CODEC) { packet, context ->
            if (context.connection().isMemoryConnection) return@configurationToClient
            ServerPackCacheManager.prepareMainThreadSync()
            context.enqueueWork {
                try {
                    ServerPackCacheManager.handleBundle(packet)
                } finally {
                    ServerPackCacheManager.completeMainThreadSync()
                }
            }
            ServerPackCacheManager.awaitMainThreadSync()
        }
    }
}
