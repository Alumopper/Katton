@file:Suppress("unused")

package top.katton.registry

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import top.katton.api.requireServer

typealias CommandExecutor = (CommandContext<CommandSourceStack>) -> Int

fun registerCommand(name: String, block: LiteralCommandDsl.() -> Unit) {
	val server = requireServer()
	val builder = Commands.literal(name)
	LiteralCommandDsl(builder).apply(block)
	ScriptCommandRegistry.register(server, builder)
	ScriptCommandRegistry.syncTree(server)
}

class LiteralCommandDsl internal constructor(
	private val builder: LiteralArgumentBuilder<CommandSourceStack>
) {
	fun requires(predicate: (CommandSourceStack) -> Boolean) {
		builder.requires(predicate)
	}

	fun executes(executor: CommandExecutor) {
		builder.executes { context -> executor(context) }
    }

	fun literal(name: String, block: LiteralCommandDsl.() -> Unit = {}) {
		val child = Commands.literal(name)
		LiteralCommandDsl(child).apply(block)
		builder.then(child)
	}

	fun <T : Any> argument(
		name: String,
		type: ArgumentType<T>,
		block: ArgumentCommandDsl<T>.() -> Unit = {}
	) {
		val child: RequiredArgumentBuilder<CommandSourceStack, T> = Commands.argument(name, type)
		ArgumentCommandDsl(child).apply(block)
		builder.then(child)
	}
}

class ArgumentCommandDsl<T> internal constructor(
	private val builder: RequiredArgumentBuilder<CommandSourceStack, T>
) {
	fun requires(predicate: (CommandSourceStack) -> Boolean) {
		builder.requires(predicate)
	}

	fun executes(executor: CommandExecutor) {
		builder.executes { context -> executor(context) }
    }

	fun suggests(provider: SuggestionProvider<CommandSourceStack>) {
		builder.suggests(provider)
	}

	fun literal(name: String, block: LiteralCommandDsl.() -> Unit = {}) {
		val child = Commands.literal(name)
		LiteralCommandDsl(child).apply(block)
		builder.then(child)
	}

	fun <U : Any> argument(
		name: String,
		type: ArgumentType<U>,
		block: ArgumentCommandDsl<U>.() -> Unit = {}
	) {
		val child: RequiredArgumentBuilder<CommandSourceStack, U> = Commands.argument(name, type)
		ArgumentCommandDsl(child).apply(block)
		builder.then(child)
	}
}
