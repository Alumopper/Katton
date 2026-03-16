package top.katton.network

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import top.katton.Katton

/**
 * Client-side networking handler for Katton.
 * Handles receiving item sync packets and registering items on client.
 */
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.CLIENT]
)
object ClientNetworkingNeoForge: ClientNetworking() {

    @JvmStatic
    @SubscribeEvent
    fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.configurationToClient(ItemSyncPacket.TYPE, ItemSyncPacket.STREAM_CODEC) { packet, context ->
            if(context.connection().isMemoryConnection) return@configurationToClient
            context.enqueueWork {
                queueItemSnapshot(packet.items)
            }
        }

        registrar.configurationToClient(EffectSyncPacket.TYPE, EffectSyncPacket.STREAM_CODEC) { packet, context ->
            if(context.connection().isMemoryConnection) return@configurationToClient
            context.enqueueWork {
                queueEffectSnapshot(packet.effects)
            }
        }

        registrar.configurationToClient(BlockSyncPacket.TYPE, BlockSyncPacket.STREAM_CODEC) { packet, context ->
            if(context.connection().isMemoryConnection) return@configurationToClient
            context.enqueueWork {
                queueBlockSnapshot(packet.blocks)
            }
        }

        registrar.playToClient(ItemSyncPacket.TYPE, ItemSyncPacket.STREAM_CODEC) { packet, context ->
            if(context.connection().isMemoryConnection) return@playToClient
            context.enqueueWork {
                applyItemSnapshot(packet.items)
            }
        }

        registrar.playToClient(EffectSyncPacket.TYPE, EffectSyncPacket.STREAM_CODEC) { packet, context ->
            if(context.connection().isMemoryConnection) return@playToClient
            context.enqueueWork {
                applyEffectSnapshot(packet.effects)
            }
        }

        registrar.playToClient(BlockSyncPacket.TYPE, BlockSyncPacket.STREAM_CODEC) { packet, context ->
            if(context.connection().isMemoryConnection) return@playToClient
            context.enqueueWork {
                applyBlockSnapshot(packet.blocks)
            }
        }
    }
}
