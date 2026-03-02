package top.katton.registry

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.RootCommandNode
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import top.katton.util.Event
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for managing script-defined commands with hot-reload support.
 * 
 * This object provides functionality to register, track, and remove commands
 * that are defined by scripts. It supports hot-reloading by tracking which
 * commands belong to which script owners and properly cleaning them up
 * during reload operations.
 * 
 * Commands registered through this registry are "managed", meaning they can
 * be replaced or removed during script reloads without affecting other
 * commands in the game.
 */
object ScriptCommandRegistry {

    private val managedRoots = linkedSetOf<String>()
    private val rootsByOwner = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * Begins a reload cycle by removing all managed commands.
     * 
     * This method should be called at the start of a script reload to clear
     * all previously registered script commands. It removes all managed
     * root commands from the dispatcher and clears the tracking data structures.
     *
     * @param server The Minecraft server instance whose command dispatcher will be modified
     */
    @Synchronized
    fun beginReload(server: MinecraftServer) {
        val dispatcherRoot = server.commands.dispatcher.root
        managedRoots.forEach { removeRootCommand(dispatcherRoot, it) }
        managedRoots.clear()
        rootsByOwner.clear()
    }

    /**
     * Registers a new command with the server's command dispatcher.
     * 
     * This method handles the registration of script-defined commands with
     * proper conflict detection and management tracking. If a command with
     * the same name already exists and is not managed by scripts, an
     * exception is thrown. If it is managed, the old command is replaced.
     *
     * @param server The Minecraft server instance to register the command with
     * @param rootBuilder The literal argument builder defining the command structure
     * @throws IllegalArgumentException if a command with the same root name exists
     *         and is not managed by scripts
     */
    @Synchronized
    fun register(server: MinecraftServer, rootBuilder: LiteralArgumentBuilder<CommandSourceStack>) {
        val rootName = rootBuilder.literal
        require(rootName.isNotBlank()) { "Command root name cannot be blank" }

        val owner = Event.Companion.currentScriptOwner() ?: "global"
        val dispatcherRoot = server.commands.dispatcher.root

        val existingNode = dispatcherRoot.getChild(rootName)
        if (existingNode != null && rootName !in managedRoots) {
            throw IllegalArgumentException("Command root '$rootName' already exists and is not managed by scripts")
        }

        if (rootName in managedRoots) {
            removeRootCommand(dispatcherRoot, rootName)
            rootsByOwner.values.forEach { it.remove(rootName) }
        }

        server.commands.dispatcher.register(rootBuilder)
        managedRoots.add(rootName)
        rootsByOwner.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(rootName)
    }

    /**
     * Synchronizes the command tree with all connected players.
     * 
     * This method sends the updated command tree to all currently connected
     * players, ensuring they see the latest available commands for tab
     * completion and command execution.
     *
     * @param server The Minecraft server instance whose players will receive the updated command tree
     */
    fun syncTree(server: MinecraftServer) {
        server.playerList.players.forEach(server.commands::sendCommands)
    }

    /**
     * Removes a root command from the dispatcher's node tree.
     * 
     * This method uses reflection to remove the command node from all
     * relevant internal maps in the root command node, ensuring complete
     * removal of the command.
     *
     * @param root The root command node to modify
     * @param name The name of the command to remove
     */
    private fun removeRootCommand(root: RootCommandNode<CommandSourceStack>, name: String) {
        removeFromMapField(root, "children", name)
        removeFromMapField(root, "literals", name)
        removeFromMapField(root, "arguments", name)
    }

    /**
     * Removes a key from a map field using reflection.
     * 
     * This is a utility method that uses reflection to access and modify
     * private map fields in Brigadier's command node classes. This is
     * necessary because Brigadier does not provide a public API for
     * removing registered commands.
     *
     * @param target The object containing the map field
     * @param fieldName The name of the map field to modify
     * @param key The key to remove from the map
     */
    @Suppress("UNCHECKED_CAST")
    private fun removeFromMapField(target: Any, fieldName: String, key: String) {
        runCatching {
            val field = target.javaClass.superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            val map = field.get(target) as? MutableMap<String, Any?> ?: return
            map.remove(key)
        }
    }
}
