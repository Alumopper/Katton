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

/**
 * Type alias for a command execution function.
 * Takes a [CommandContext] and returns an integer result code.
 * A return value of 1 indicates success, 0 indicates failure.
 */
typealias CommandExecutor = (CommandContext<CommandSourceStack>) -> Int

/**
 * Registers a new command with the specified name and configuration block.
 * 
 * This function provides a DSL-based approach to registering commands using
 * Minecraft's Brigadier command system. The command is registered with the
 * server's command dispatcher and synchronized with all connected players.
 *
 * Example usage:
 * ```kotlin
 * registerCommand("mycommand") {
 *     requires { it.hasPermission(2) }
 *     executes { context ->
 *         context.source.sendSuccess("Hello!", true)
 *         1
 *     }
 *     literal("subcommand") {
 *         executes { 1 }
 *     }
 *     argument("value", IntegerArgumentType.integer()) {
 *         executes { context ->
 *             val value = IntegerArgumentType.getInteger(context, "value")
 *             1
 *         }
 *     }
 * }
 * ```
 *
 * @param name The root name of the command to register
 * @param block A configuration lambda that builds the command structure using [LiteralCommandDsl]
 * @throws IllegalStateException if the server is not available
 */
fun registerCommand(name: String, block: LiteralCommandDsl.() -> Unit) {
	val server = requireServer()
	val builder = Commands.literal(name)
	LiteralCommandDsl(builder).apply(block)
	ScriptCommandRegistry.register(server, builder)
	ScriptCommandRegistry.syncTree(server)
}

/**
 * A DSL builder for constructing literal command nodes.
 * 
 * This class wraps a [LiteralArgumentBuilder] and provides a Kotlin-idiomatic
 * way to build command structures. It supports nested literals, arguments,
 * execution handlers, and permission requirements.
 */
class LiteralCommandDsl internal constructor(
	private val builder: LiteralArgumentBuilder<CommandSourceStack>
) {
	/**
	 * Sets a requirement predicate that must be satisfied for the command to be executable.
	 *
	 * @param predicate A function that takes a [CommandSourceStack] and returns
	 *                  true if the command should be available to that source
	 */
	fun requires(predicate: (CommandSourceStack) -> Boolean) {
		builder.requires(predicate)
	}

	/**
	 * Sets the execution handler for this command node.
	 *
	 * @param executor A function that handles command execution and returns a result code
	 */
	fun executes(executor: CommandExecutor) {
		builder.executes { context -> executor(context) }
    }

	/**
	 * Adds a nested literal child node to this command.
	 *
	 * @param name The name of the literal node
	 * @param block A configuration lambda for the child node
	 */
	fun literal(name: String, block: LiteralCommandDsl.() -> Unit = {}) {
		val child = Commands.literal(name)
		LiteralCommandDsl(child).apply(block)
		builder.then(child)
	}

	/**
	 * Adds an argument child node to this command.
	 *
	 * @param T The type of the argument value
	 * @param name The name of the argument, used to retrieve the value later
	 * @param type The [ArgumentType] that defines how to parse this argument
	 * @param block A configuration lambda for the argument node
	 */
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

/**
 * A DSL builder for constructing argument command nodes.
 * 
 * This class wraps a [RequiredArgumentBuilder] and provides additional functionality
 * specific to argument nodes, such as suggestion providers for tab completion.
 *
 * @param T The type of the argument value
 */
class ArgumentCommandDsl<T> internal constructor(
	private val builder: RequiredArgumentBuilder<CommandSourceStack, T>
) {
	/**
	 * Sets a requirement predicate that must be satisfied for the command to be executable.
	 *
	 * @param predicate A function that takes a [CommandSourceStack] and returns
	 *                  true if the command should be available to that source
	 */
	fun requires(predicate: (CommandSourceStack) -> Boolean) {
		builder.requires(predicate)
	}

	/**
	 * Sets the execution handler for this command node.
	 *
	 * Use [CommandContext.getArgument] to retrieve the argument value.
	 *
	 * @param executor A function that handles command execution and returns a result code
	 */
	fun executes(executor: CommandExecutor) {
		builder.executes { context -> executor(context) }
    }

	/**
	 * Sets a suggestion provider for tab completion of this argument.
	 * 
	 * Suggestion providers are used to generate possible completions when
	 * a player presses Tab while typing the command.
	 *
	 * @param provider A [SuggestionProvider] that generates suggestions based on context
	 */
	fun suggests(provider: SuggestionProvider<CommandSourceStack>) {
		builder.suggests(provider)
	}

	/**
	 * Adds a nested literal child node to this argument node.
	 *
	 * @param name The name of the literal node
	 * @param block A configuration lambda for the child node
	 */
	fun literal(name: String, block: LiteralCommandDsl.() -> Unit = {}) {
		val child = Commands.literal(name)
		LiteralCommandDsl(child).apply(block)
		builder.then(child)
	}

	/**
	 * Adds another argument child node to this argument node.
	 *
	 * @param U The type of the new argument value
	 * @param name The name of the argument, used to retrieve the value later
	 * @param type The [ArgumentType] that defines how to parse this argument
	 * @param block A configuration lambda for the argument node
	 */
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
