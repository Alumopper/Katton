package top.katton.registry

import net.minecraft.core.MappedRegistry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext.currentScriptScope

/**
 * Tracks which registry entries are managed for hot-reload cleanup.
 * Only WORLD-scoped, non-GLOBAL entries are tracked.
 */
internal class OwnershipTracker {
    private val managedIds = linkedSetOf<Identifier>()

    private fun currentScope(): ScriptPackScope? = currentScriptScope()

    fun markManaged(id: Identifier, registerMode: RegisterMode): Boolean {
        val scope = currentScope()
        // Only track world-scoped, non-GLOBAL entries for reload cleanup
        // GLOBAL-scoped entries are never reloadable (they persist forever)
        if (scope == ScriptPackScope.WORLD && registerMode != RegisterMode.GLOBAL) {
            managedIds.add(id)
            return true
        }
        return false
    }

    fun managedIdsSnapshot(): Set<Identifier> = synchronized(this) { managedIds.toSet() }

    /**
     * Unregister all managed entries from the given registry if needed, and clear the tracking set.
     */
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
        return removed
    }
}
