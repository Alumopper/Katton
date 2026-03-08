package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.ServerChatEvent
import top.katton.util.createCancellableUnit
import top.katton.util.setCancel

@Suppress("unused")
@EventBusSubscriber(
    modid = top.katton.Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerMessageEvent {
    @SubscribeEvent
    private fun onServerChat(e: ServerChatEvent) {
        onServerChat(ServerChatArg(e.player, e.rawText, e.message))
        setCancel(onServerChat, e)
    }

    val onServerChat = createCancellableUnit<ServerChatArg>()
}