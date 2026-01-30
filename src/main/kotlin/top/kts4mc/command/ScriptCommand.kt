package top.kts4mc.command

import com.mojang.brigadier.CommandDispatcher
import kotlinx.coroutines.runBlocking
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import top.kts4mc.KTS4MC
import top.kts4mc.engine.ScriptLoader
import kotlin.script.experimental.jvm.util.isError

object ScriptCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("script")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("script", IdentifierArgument.id())
                    .suggests { context, builder ->
                        KTS4MC.scriptLoader.scripts.forEach {
                            builder.suggest(it.key.toString())
                        }
                        builder.buildFuture()
                    }
                    .executes { context ->
                        val id = IdentifierArgument.getId(context, "script")
                        executeScript(context.source, id, KTS4MC.scriptLoader)
                    })
        )
    }

    fun executeScript(source: CommandSourceStack, identifier: Identifier, loader: ScriptLoader): Int {
        val server = source.server

        //找脚本
        val script = loader.getScript(identifier)
        if (script.isEmpty) {
            source.sendFailure(Component.literal("§c找不到脚本: $identifier"))
            return 0
        }
        //执行脚本
        val compiled = script.get()
        val result = runBlocking {
            loader.engine.execute(
                compiled,
                mapOf(
                    "server" to server,
                    "source" to source
                )
            )
        }

        if(result.isError()){
            source.sendFailure(
                Component.literal("脚本执行失败: \n" + result.reports.joinToString("\n") { it.message })
            )
            return 0
        }else{
            source.sendSuccess(
                { Component.literal("脚本执行成功: $identifier") },
                false
            )
            return 1
        }
    }
}