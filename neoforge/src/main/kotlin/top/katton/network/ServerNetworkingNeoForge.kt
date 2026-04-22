package top.katton.network

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import top.katton.Katton

@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerNetworkingNeoForge {

    @JvmStatic
    @SubscribeEvent
    fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.configurationToServer(ScriptPackRequestPacket.TYPE, ScriptPackRequestPacket.STREAM_CODEC) { packet, context ->
            context.enqueueWork {
                val response = ServerNetworking.createScriptPackBundlePacket(packet.requestedSyncIds)
                if (response.packs.isNotEmpty()) {
                    context.reply(response)
                }
            }
        }
    }
}
