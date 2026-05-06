package top.katton.registry

import net.minecraft.core.MappedRegistry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which registry entries belong to which script owner,
 * enabling scoped cleanup during hot-reload.
 */
internal class OwnershipTracker {
    private val managedIds = linkedSetOf<Identifier>()
    private val idsByOwner = ConcurrentHashMap<String, MutableSet<Identifier>>()

    fun currentOwner() = top.katton.util.ScriptExecutionContext.currentScriptOwner() ?: "global"

    fun markManaged(id: Identifier, registerMode: RegisterMode): Boolean {
        val owner = currentOwner()
        if (owner != "global" && registerMode != RegisterMode.GLOBAL) {
            managedIds.add(id)
            idsByOwner.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(id)
            return true
        }
        return false
    }

    fun managedIdsSnapshot(): Set<Identifier> = synchronized(this) { managedIds.toSet() }

    @Synchronized
    fun <T : Any> beginReload(
        registry: MappedRegistry<T>,
        resourceKey: (Identifier) -> ResourceKey<T>,
        unregisterFromRegistry: Boolean = true
    ): List<Identifier> {
        val removed = managedIds.toList()
        if (unregisterFromRegistry) {
            unregisterAll(registry, removed, resourceKey)
        }
        managedIds.clear()
        idsByOwner.clear()
        return removed
    }
}
