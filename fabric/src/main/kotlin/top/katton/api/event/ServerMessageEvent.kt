package top.katton.api.event

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * Server-side message events for Fabric platform.
 *
 * This object provides events related to chat messages, game messages, and command messages.
 * Includes both allow events (can cancel) and handler events (notification only).
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
     * Event triggered to allow or deny a chat message from being sent.
     *
     * @return true to allow the message, false to cancel it.
     */
    val onAllowChatMessage = createAll<AllowChatMessageArg>()

    /**
     * Event triggered to allow or deny a game message from being sent.
     *
     * @return true to allow the message, false to cancel it.
     */
    val onAllowGameMessage = createAll<AllowGameMessageArg>()

    /**
     * Event triggered to allow or deny a command message from being sent.
     *
     * @return true to allow the message, false to cancel it.
     */
    val onAllowCommandMessage = createAll<AllowCommandMessageArg>()

    /**
     * Event triggered when a chat message is sent (after being allowed).
     */
    val onChatMessage = createUnit<ChatMessageArg>()

    /**
     * Event triggered when a game message is sent (after being allowed).
     */
    val onGameMessage = createUnit<GameMessageArg>()

    /**
     * Event triggered when a command message is sent (after being allowed).
     */
    val onCommandMessage = createUnit<CommandMessageArg>()
}
