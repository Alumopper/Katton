package top.katton.api.event.managed

import net.fabricmc.fabric.api.event.Event
import org.slf4j.LoggerFactory
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Fabric implementation of [ManagedListenerProvider].
 *
 * Fabric's event system ([Event]) does not support individual callback unregistration.
 * To work around this, each managed listener wraps the user callback in a dynamic proxy
 * with an `active` flag. On reload, all WORLD/SERVER_CACHE-scoped wrappers are deactivated.
 *
 * Initialized once in [KattonFabric.onInitialize] via [initialize].
 */
object FabricManagedEvents {
    internal val LOGGER = LoggerFactory.getLogger(FabricManagedEvents::class.java)
    private var nextId = 0L
    val registrations = mutableMapOf<Long, FabricRegistration>()
    private val scopeRegistrations = mutableMapOf<ScriptPackScope, MutableSet<Long>>()

    class FabricRegistration(
        val id: Long,
        val wrapper: Any,
        val scope: ScriptPackScope?,
        @Volatile var active: Boolean
    )

    @JvmStatic
    fun initialize() {
        if (provider != null) return
        provider = object : ManagedListenerProvider {
            override fun register(
                eventClass: Class<*>,
                owner: String,
                scope: ScriptPackScope?,
                priority: Int,
                ignoreCancelled: Boolean,
                handler: (Any) -> Unit
            ): ManagedEventHandle {
                val id = nextId++
                val wrapper = Proxy.newProxyInstance(
                    eventClass.classLoader,
                    arrayOf(eventClass)
                ) { _, _, args ->
                    val reg = registrations[id] ?: return@newProxyInstance null
                    if (reg.active && args != null && args.isNotEmpty()) {
                        try {
                            ScriptExecutionContext.withScope(scope) {
                                ScriptExecutionContext.withOwner(owner) {
                                    handler(args[0])
                                }
                            }
                        } catch (t: Throwable) {
                            LOGGER.warn("Managed Fabric event handler failed for {}", owner, t)
                        }
                    }
                    null
                }
                val registration = FabricRegistration(id, wrapper, scope, active = true)
                registrations[id] = registration
                if (scope != null) {
                    scopeRegistrations.getOrPut(scope) { mutableSetOf() }.add(id)
                }
                return ManagedEventHandle(id, eventClass)
            }

            override fun unregister(handle: ManagedEventHandle) {
                registrations[handle.id]?.active = false
                registrations.remove(handle.id)
                scopeRegistrations.values.forEach { it.remove(handle.id) }
            }

            override fun clearByScope(scope: ScriptPackScope) {
                val ids = scopeRegistrations.remove(scope) ?: return
                ids.forEach { id ->
                    registrations.remove(id)?.active = false
                }
            }

            override fun clearAll() {
                registrations.values.forEach { it.active = false }
                registrations.clear()
                scopeRegistrations.clear()
            }
        }
    }

    @JvmStatic
    fun shutdown() {
        provider?.clearAll()
        registrations.clear()
        scopeRegistrations.clear()
    }
}

// ═══════════════════════════════════════════════════════════════
//  Fabric-specific script API
// ═══════════════════════════════════════════════════════════════

/**
 * Register a managed Fabric-native event listener.
 *
 * Fabric events are callback-based and don't support individual unregistration.
 * Managed listeners use a dynamic proxy wrapper with an `active` flag —
 * on reload, all WORLD/SERVER_CACHE-scoped wrappers are deactivated.
 *
 * Usage:
 * ```kotlin
 * registerFabricEvent(ServerTickEvents.START_SERVER_TICK, ServerTickEvents.StartTick { server ->
 *     // handler
 * })
 * ```
 *
 * @param event The Fabric event object to register on
 * @param callback The functional interface callback
 * @return A handle for manual unregistration
 */
fun <T : Any> registerFabricEvent(
    event: Event<T>,
    callback: T
): ManagedEventHandle {
    val provider = provider
        ?: error("ManagedEvents.provider not initialized — call FabricManagedEvents.initialize() first")

    val scope = ScriptExecutionContext.currentScriptScope()
    val owner = ScriptExecutionContext.currentScriptOwner() ?: "unknown"
    val iface = callback::class.java

    val handle = provider.register(iface, owner, scope, 2, false) { /* handled by proxy */ }

    val activeWrapper = Proxy.newProxyInstance(
        iface.classLoader,
        arrayOf(iface)
    ) { _, method, args ->
        val reg = FabricManagedEvents.registrations[handle.id]
        if (reg != null && reg.active) {
            try {
                ScriptExecutionContext.withScope(scope) {
                    ScriptExecutionContext.withOwner(owner) {
                        method.invoke(callback, *(args ?: emptyArray()))
                    }
                }
            } catch (t: Throwable) {
                val failure = (t as? InvocationTargetException)?.targetException ?: t
                FabricManagedEvents.LOGGER.warn("Managed Fabric event handler failed for {}", owner, failure)
                null
            }
        } else null
    }

    @Suppress("UNCHECKED_CAST")
    event.register(activeWrapper as T)

    return handle
}

/**
 * Manually unregister a managed Fabric listener created by [registerFabricEvent].
 */
fun unregisterFabricEvent(handle: ManagedEventHandle) {
    provider?.unregister(handle)
}
