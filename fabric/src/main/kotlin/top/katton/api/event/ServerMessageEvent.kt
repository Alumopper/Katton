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
            onAllowChatMessage(ServerAllowChatMessageArg(a, b, c)).getOrElse { true }
        }

        ServerMessageEvents.ALLOW_GAME_MESSAGE.register { a, b, c ->
            onAllowGameMessage(ServerAllowGameMessageArg(a, b, c)).getOrElse { true }
        }

        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register { a, b, c ->
            onAllowCommandMessage(ServerAllowCommandMessageArg(a, b, c)).getOrElse { true }
        }

        ServerMessageEvents.CHAT_MESSAGE.register { a, b, c ->
            onChatMessage(ServerChatMessageArg(a, b, c))
        }

        ServerMessageEvents.GAME_MESSAGE.register { a, b, c ->
            onGameMessage(ServerGameMessageArg(a, b, c))
        }

        ServerMessageEvents.COMMAND_MESSAGE.register { a, b, c ->
            onCommandMessage(ServerCommandMessageArg(a, b, c))
        }
    }

    val onAllowChatMessage = createAll<ServerAllowChatMessageArg>()

    val onAllowGameMessage = createAll<ServerAllowGameMessageArg>()

    val onAllowCommandMessage = createAll<ServerAllowCommandMessageArg>()

    val onChatMessage = createUnit<ServerChatMessageArg>()

    val onGameMessage = createUnit<ServerGameMessageArg>()

    val onCommandMessage = createUnit<ServerCommandMessageArg>()
}