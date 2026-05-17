package top.katton.api.event.managed

import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext

/**
 * Managed event listener handle — returned to scripts when registering a native listener.
 * Can be used to manually unregister the listener before reload.
 */
data class ManagedEventHandle(
    val id: Long,
    val eventClass: Class<*>
)

/**
 * Platform-provided implementation of managed event listener registry.
 * Each platform (Paper, Fabric, NeoForge) sets [provider] during initialization.
 *
 * Managed listeners are automatically cleaned up on reload (GLOBAL scope persists,
 * WORLD/SERVER_CACHE scope is cleared, all listeners cleared on full reload).
 */
interface ManagedListenerProvider {
    fun register(
        eventClass: Class<*>,
        owner: String,
        scope: ScriptPackScope?,
        priority: Int,
        ignoreCancelled: Boolean,
        handler: (Any) -> Unit
    ): ManagedEventHandle

    fun unregister(handle: ManagedEventHandle)
    fun clearByScope(scope: ScriptPackScope)
    fun clearAll()
}

/**
 * Platform sets this during initialization (e.g., PaperManagedEvents.initialize()).
 * Must be set before any script calls [registerEvent].
 */
@Volatile
@JvmField
var provider: ManagedListenerProvider? = null

// ═══════════════════════════════════════════════════════════════
//  Script-facing API
// ═══════════════════════════════════════════════════════════════

/**
 * Register a managed native event listener.
 *
 * The listener is automatically cleaned up on `/katton reload` for WORLD/SERVER_CACHE scopes.
 * GLOBAL scope listeners persist across reloads until manually [unregisterEvent]ed.
 *
 * @param T The native event class (e.g., org.bukkit.event.player.PlayerMoveEvent)
 * @param priority Event priority (0=LOWEST, 1=LOW, 2=NORMAL, 3=HIGH, 4=HIGHEST, 5=MONITOR)
 * @param ignoreCancelled If true, the handler is not called for cancelled events
 * @param handler The callback receiving the typed event
 * @return A handle that can be used with [unregisterEvent] to manually remove the listener
 */
inline fun <reified T : Any> registerEvent(
    priority: Int = 2, // EventPriority.NORMAL
    ignoreCancelled: Boolean = false,
    noinline handler: (T) -> Unit
): ManagedEventHandle {
    val p = provider ?: error("ManagedEvents.provider not initialized — ensure platform calls ManagedListenerProvider.initialize()")
    val owner = ScriptExecutionContext.currentScriptOwner() ?: "unknown"
    val scope = ScriptExecutionContext.currentScriptScope()
    @Suppress("UNCHECKED_CAST")
    return p.register(T::class.java, owner, scope, priority, ignoreCancelled, handler as (Any) -> Unit)
}

/**
 * Manually unregister a managed listener created by [registerEvent].
 */
fun unregisterEvent(handle: ManagedEventHandle) {
    provider?.unregister(handle)
}

// ═══════════════════════════════════════════════════════════════
//  Lifecycle integration (called by Katton.kt)
// ═══════════════════════════════════════════════════════════════

/**
 * Unregister all managed listeners registered under [scope].
 * Called by [top.katton.Katton.clearWorldAndServerEvents].
 */
fun clearManagedByScope(scope: ScriptPackScope) {
    provider?.clearByScope(scope)
}

/**
 * Unregister ALL managed listeners.
 * Called on full reload or server shutdown.
 */
fun clearAllManaged() {
    provider?.clearAll()
}
