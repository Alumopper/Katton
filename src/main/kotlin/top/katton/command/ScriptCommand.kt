package top.katton.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import top.katton.Katton
import top.katton.registry.KattonRegistry

object ScriptCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("katton")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    literal("reload")
                        .executes {
                            reloadScript(it.source.server)
                            1
                        }
                )
                .then(
                    literal("give")
                        .then(argument("item", IdentifierArgument())
                            .suggests {_, builder ->
                                KattonRegistry.ITEMS.keys.forEach { id ->
                                    builder.suggest(id.toString())
                                }
                                builder.buildFuture()
                            }
                            .executes {
                                val itemId = IdentifierArgument.getId(it, "item")
                                val itemEntry = KattonRegistry.ITEMS[itemId]
                                if (itemEntry == null) {
                                    it.source.sendFailure(
                                        Component.literal("Unknown item: $itemId")
                                    )
                                    return@executes 0
                                }
                                val player = it.source.playerOrException
                                player.addItem(itemEntry.getDefaultInstance())
                                it.source.sendSuccess(
                                    { Component.literal("Gave ${player.name.string} 1 $itemId") },
                                    false
                                )
                                return@executes 1
                            })
                )
        )
    }

    fun reloadScript(server: MinecraftServer) {
        Katton.reloadScripts(server)
        syncCommandTree(server)
    }

    @JvmStatic
    fun syncCommandTree(server: MinecraftServer) {
        server.playerList.players.forEach(server.commands::sendCommands)
    }
}