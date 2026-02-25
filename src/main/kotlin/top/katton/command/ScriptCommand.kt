package top.katton.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.server.MinecraftServer
import top.katton.Katton

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