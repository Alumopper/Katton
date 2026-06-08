package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.ServerChatEvent
import top.katton.util.createCancellableUnit
import top.katton.util.setCancel

/**
 * %en
 * Server-side message events for NeoForge platform.
 *
 * This object provides events related to chat messages.
 *
 * %zh
 * NeoForge 平台的服务端消息事件。
 * 此对象提供与聊天消息相关的事件。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = top.katton.Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ServerMessageEvent {

    @JvmStatic
    @SubscribeEvent
    private fun onServerChat(e: ServerChatEvent) {
        onServerChat(ServerChatArg(e.player, e.rawText, e.message))
        setCancel(onServerChat, e)
    }

    /**
     * %en
     * Event triggered when a server chat message is sent.
     * Can be cancelled to prevent the message from being sent.
     *
     * %zh
     * 当服务端聊天消息即将发送时触发。
     * 可取消以阻止消息发送。
     */
    val onServerChat = createCancellableUnit<ServerChatArg>()
}
