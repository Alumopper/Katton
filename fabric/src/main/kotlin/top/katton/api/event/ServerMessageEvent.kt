package top.katton.api.event

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import top.katton.util.createAll
import top.katton.util.createUnit

/**
 * Server-side message events (chat/game/command allow and handlers).
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

    val onAllowChatMessage = createAll<AllowChatMessageArg>()

    val onAllowGameMessage = createAll<AllowGameMessageArg>()

    val onAllowCommandMessage = createAll<AllowCommandMessageArg>()

    val onChatMessage = createUnit<ChatMessageArg>()

    val onGameMessage = createUnit<GameMessageArg>()

    val onCommandMessage = createUnit<CommandMessageArg>()
}