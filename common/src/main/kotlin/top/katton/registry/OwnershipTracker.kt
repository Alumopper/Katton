package top.katton.registry

import net.minecraft.core.MappedRegistry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext.currentScriptScope

/**
 * Tracks which registry entries are managed for hot-reload and world-lifetime cleanup.
 *
 * Only WORLD-scoped script pack entries are trackable. GLOBAL-scoped pack entries
 * are permanent and never tracked.
 *
 * Tracked entries are split into two categories:
 * - **reloadable**: cleaned up by [beginReload] (triggered by `/katton reload`)
 * - **world**: cleaned up by [beginWorldCleanup] (triggered on world leave)
 */
internal class OwnershipTracker {
    private val reloadableIds = linkedSetOf<Identifier>()
    private val worldIds = linkedSetOf<Identifier>()

    private fun currentScope(): ScriptPackScope? = currentScriptScope()

    /**
     * Adds [id] to the appropriate tracking set based on [mode].
     * Only WORLD-scoped entries are tracked; GLOBAL-scoped entries are ignored.
     *
     * @return true if the id was added to any tracking set
     */
    fun markManaged(id: Identifier, mode: RegisterMode): Boolean {
        val scope = currentScope()
        if (scope != ScriptPackScope.WORLD) return false
        return when (mode) {
            RegisterMode.RELOADABLE -> { reloadableIds.add(id); true }
            RegisterMode.WORLD -> { worldIds.add(id); true }
            RegisterMode.GLOBAL -> false
        }
    }

    fun managedIdsSnapshot(): Set<Identifier> = synchronized(this) {
        (reloadableIds + worldIds).toSet()
    }

    // ═══════════════════════════════════════════════════════════
    //  Reload cleanup (for RegisterMode.RELOADABLE)
    // ═══════════════════════════════════════════════════════════

    /**
     * Clears all RELOADABLE tracked entries. Optionally unregisters them from
     * the Minecraft [registry] if [unregisterFromRegistry] is true.
     *
     * WORLD entries are NOT affected — they persist across reloads.
     */
    @Synchronized
    fun <T : Any> beginReload(
        registry: MappedRegistry<T>,
        resourceKey: (Identifier) -> ResourceKey<T>,
        unregisterFromRegistry: Boolean = true
    ): List<Identifier> {
        val removed = reloadableIds.toList()
        if (unregisterFromRegistry) {
            unregisterAll(registry, removed, resourceKey)
        }
        reloadableIds.clear()
        return removed
    }

    // ═══════════════════════════════════════════════════════════
    //  World-leave cleanup (for RegisterMode.WORLD)
    // ═══════════════════════════════════════════════════════════

    /**
     * Clears all WORLD tracked entries. Always unregisters them from the
     * Minecraft [registry] because the world session has ended.
     */
    @Synchronized
    fun <T : Any> beginWorldCleanup(
        registry: MappedRegistry<T>,
        resourceKey: (Identifier) -> ResourceKey<T>
    ): List<Identifier> {
        val removed = worldIds.toList()
        unregisterAll(registry, removed, resourceKey)
        worldIds.clear()
        return removed
    }
}
