package top.katton.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
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
                            val source = it.source
                            if (reloadScript(source.server)) {
                                source.sendSuccess({ Component.literal("[Katton] Reloaded datapack scripts.") }, true)
                                1
                            } else {
                                source.sendFailure(Component.literal("[Katton] Failed to reload datapack scripts."))
                                0
                            }
                        }
                )
        )
    }

    fun reloadScript(server: MinecraftServer): Boolean {
        val reloaded = Katton.reloadScripts(server)
        if (reloaded) {
            syncCommandTree(server)
        }
        return reloaded
    }

    @JvmStatic
    fun syncCommandTree(server: MinecraftServer) {
        server.playerList.players.forEach(server.commands::sendCommands)
    }
}