package top.katton.api.event

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * %en
 * Server-side message events for Fabric platform.
 *
 * This object provides events related to chat messages, game messages, and command messages.
 * Includes both allow events (can cancel) and handler events (notification only).
 *
 * %zh
 * Fabric 平台的服务端消息事件。
 * 此对象提供与聊天消息、游戏消息和命令消息相关的事件。
 * 包含可取消的允许类事件，以及仅通知的处理类事件。
 */
object ServerMessageEvent {

    fun initialize() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { a, b, c ->
            onAllowChatMessage(AllowChatMessageArg(a, b, c)).getOrElse { true }
        }

        ServerMessageEvents.ALLOW_GAME_MESSAGE.register { a, b, c ->
            onAllowGameMessage(AllowGameMessageArg(a, b, c)).getOrElse { true }
        }

        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register { a, b, c ->
            onAllowCommandMessage(AllowCommandMessageArg(a, b, c)).getOrElse { true }
        }

        ServerMessageEvents.CHAT_MESSAGE.register { a, b, c ->
            onChatMessage(ChatMessageArg(a, b, c))
        }

        ServerMessageEvents.GAME_MESSAGE.register { a, b, c ->
            onGameMessage(GameMessageArg(a, b, c))
        }

        ServerMessageEvents.COMMAND_MESSAGE.register { a, b, c ->
            onCommandMessage(CommandMessageArg(a, b, c))
        }
    }

    /**
     * %en
     * Event triggered to allow or deny a chat message from being sent.
     *
     * %zh
     * 当需要决定是否允许发送聊天消息时触发。
     * @return
     * %en to allow the message, false to cancel it.
     * %zh 返回值允许消息发送，false 表示取消。
     */
    val onAllowChatMessage = createAll<AllowChatMessageArg>()

    /**
     * %en
     * Event triggered to allow or deny a game message from being sent.
     *
     * %zh
     * 当需要决定是否允许发送游戏消息时触发。
     * @return
     * %en to allow the message, false to cancel it.
     * %zh 返回值允许消息发送，false 表示取消。
     */
    val onAllowGameMessage = createAll<AllowGameMessageArg>()

    /**
     * %en
     * Event triggered to allow or deny a command message from being sent.
     *
     * %zh
     * 当需要决定是否允许发送命令消息时触发。
     * @return
     * %en to allow the message, false to cancel it.
     * %zh 返回值允许消息发送，false 表示取消。
     */
    val onAllowCommandMessage = createAll<AllowCommandMessageArg>()

    /**
     * %en
     * Event triggered when a chat message is sent (after being allowed).
     *
     * %zh
     * 当聊天消息发送后触发（在允许之后）。
     */
    val onChatMessage = createUnit<ChatMessageArg>()

    /**
     * %en
     * Event triggered when a game message is sent (after being allowed).
     *
     * %zh
     * 当游戏消息发送后触发（在允许之后）。
     */
    val onGameMessage = createUnit<GameMessageArg>()

    /**
     * %en
     * Event triggered when a command message is sent (after being allowed).
     *
     * %zh
     * 当命令消息发送后触发（在允许之后）。
     */
    val onCommandMessage = createUnit<CommandMessageArg>()
}
