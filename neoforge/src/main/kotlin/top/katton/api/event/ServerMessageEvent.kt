package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.ServerChatEvent
import top.katton.util.createCancellableUnit
import top.katton.util.setCancel

/**
 * Server-side message events for NeoForge platform.
 *
 * This object provides events related to chat messages.
 */
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

    /**
     * Event triggered when a server chat message is sent.
     * Can be cancelled to prevent the message from being sent.
     */
    val onServerChat = createCancellableUnit<ServerChatArg>()
}
