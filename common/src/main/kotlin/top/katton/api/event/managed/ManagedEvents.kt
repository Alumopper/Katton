package top.katton.api.event.managed

import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext

/**
 * Managed event listener handle returned to scripts when registering a native listener.
 * 注册原生事件监听器后返回给脚本的托管监听器句柄。
 */
data class ManagedEventHandle(
    val id: Long,
    val eventClass: Class<*>
)

/**
 * Platform bridge for managed native event listeners.
 * Paper, Fabric, and NeoForge can provide their own implementation through [provider].
 * Implementations track scope and environment so integrated client and server listeners can reload independently.
 *
 * 事件会按脚本所有者和作用域记录。WORLD/SERVER_CACHE 作用域会在重载或清理时自动移除，
 * GLOBAL 作用域需要显式注销或在全局清理时移除。
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
    fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment)
    fun clearByOwnerPrefix(ownerPrefix: String)
    fun clearAll()
}

/**
 * Active managed-listener provider installed by the current platform.
 * Paper currently initializes this from `PaperManagedEvents.initialize()`.
 */
@Volatile
@JvmField
var provider: ManagedListenerProvider? = null

// ---------------------------------------------------------------------------
//  Script-facing API
// ---------------------------------------------------------------------------

/**
 * Register a native platform event listener from script code.
 *
 * Listeners in WORLD/SERVER_CACHE scope are cleaned up during `/katton reload`.
 * GLOBAL listeners stay active until [unregisterEvent] or a full managed cleanup removes them.
 *
 * @param T Native event type, for example `org.bukkit.event.player.PlayerMoveEvent`.
 * @param priority Event priority. Paper uses 0=LOWEST, 1=LOW, 2=NORMAL, 3=HIGH, 4=HIGHEST, 5=MONITOR.
 * @param ignoreCancelled When `true`, cancelled events are ignored when the platform supports that behavior.
 * @param handler Callback invoked with the native event instance.
 * @return Handle that can be passed to [unregisterEvent].
 */
inline fun <reified T : Any> registerEvent(
    priority: Int = 2, // EventPriority.NORMAL
    ignoreCancelled: Boolean = false,
    noinline handler: (T) -> Unit
): ManagedEventHandle {
    val p = provider ?: error("ManagedEvents.provider not initialized - ensure platform calls ManagedListenerProvider.initialize()")
    val owner = ScriptExecutionContext.currentScriptOwner() ?: "unknown"
    val scope = ScriptExecutionContext.currentScriptScope()
    @Suppress("UNCHECKED_CAST")
    return p.register(T::class.java, owner, scope, priority, ignoreCancelled, handler as (Any) -> Unit)
}

/**
 * Unregister a listener previously created by [registerEvent].
 */
fun unregisterEvent(handle: ManagedEventHandle) {
    provider?.unregister(handle)
}

// ---------------------------------------------------------------------------
//  Lifecycle integration (called by Katton.kt)
// ---------------------------------------------------------------------------

/**
 * Clear all managed listeners registered under [scope].
 * Called from [top.katton.Katton.clearWorldAndServerEvents].
 */
fun clearManagedByScope(scope: ScriptPackScope) {
    provider?.clearByScope(scope)
}

fun clearManagedByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
    provider?.clearByScopeAndEnvironment(scope, environment)
}

fun clearManagedByOwnerPrefix(ownerPrefix: String) {
    provider?.clearByOwnerPrefix(ownerPrefix)
}

/**
 * Clear every managed listener from the active provider.
 * Used during full shutdown or global cleanup.
 */
fun clearAllManaged() {
    provider?.clearAll()
}
