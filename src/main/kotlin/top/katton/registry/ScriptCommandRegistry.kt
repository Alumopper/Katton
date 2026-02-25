package top.katton.registry

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.RootCommandNode
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import top.katton.util.Event
import java.util.concurrent.ConcurrentHashMap

object ScriptCommandRegistry {

    private val managedRoots = linkedSetOf<String>()
    private val rootsByOwner = ConcurrentHashMap<String, MutableSet<String>>()

    @Synchronized
    fun beginReload(server: MinecraftServer) {
        val dispatcherRoot = server.commands.dispatcher.root
        managedRoots.forEach { removeRootCommand(dispatcherRoot, it) }
        managedRoots.clear()
        rootsByOwner.clear()
    }

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

    fun syncTree(server: MinecraftServer) {
        server.playerList.players.forEach(server.commands::sendCommands)
    }

    private fun removeRootCommand(root: RootCommandNode<CommandSourceStack>, name: String) {
        removeFromMapField(root, "children", name)
        removeFromMapField(root, "literals", name)
        removeFromMapField(root, "arguments", name)
    }

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