package qwq

import com.mojang.brigadier.arguments.IntegerArgumentType.getInteger
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import com.mojang.brigadier.arguments.StringArgumentType.getString
import com.mojang.brigadier.arguments.StringArgumentType.word
import net.minecraft.commands.SharedSuggestionProvider.suggest
import net.minecraft.network.chat.Component
import top.katton.api.ServerScriptEntrypoint
import top.katton.registry.registerCommand

@ServerScriptEntrypoint
fun commandTest() {
    registerCommand("demo") {
        literal("ping") {
            executes { ctx ->
                ctx.source.sendSuccess(
                    { Component.literal("[demo] pong") },
                    false
                )
                1
            }
        }

        literal("echo") {
            argument("text", word()) {
                suggests { _, builder ->
                    suggest(listOf("hello", "world", "katton"), builder)
                }
                executes { ctx ->
                    val text = getString(ctx, "text")
                    ctx.source.sendSuccess(
                        { Component.literal("[demo] $text") },
                        false
                    )
                    1
                }
            }
        }

        literal("add") {
            argument("a", integer()) {
                argument("b", integer()) {
                    executes { ctx ->
                        val a = getInteger(ctx, "a")
                        val b = getInteger(ctx, "b")
                        ctx.source.sendSuccess(
                            { Component.literal("[demo] $a + $b = ${a + b}") },
                            false
                        )
                        1
                    }
                }
            }
        }
    }
}
