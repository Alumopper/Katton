package top.katton.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.server.MinecraftServer

object ScriptCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("script")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("reload"))
                .executes {
                    reloadScript(it.source.server)
                    return@executes 1
                }
        )
    }

    fun reloadScript(server: MinecraftServer) {

    }
}