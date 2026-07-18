package top.katton.api.event.managed

import net.fabricmc.fabric.api.event.Event
import org.slf4j.LoggerFactory
import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * %en
 * Fabric implementation of [ManagedListenerProvider].
 *
 * Fabric's event system ([Event]) does not support individual callback unregistration.
 * To work around this, each managed listener wraps the user callback in a dynamic proxy
 * with an `active` flag. On reload, all WORLD/SERVER_CACHE-scoped wrappers are deactivated.
 *
 * Initialized once in [KattonFabric.onInitialize] via [initialize].
 *
 * %zh
 * [ManagedListenerProvider] 的 Fabric 实现。
 *
 * Fabric 的事件系统 ([Event]) 不支持单个回调的注销。
 * 为了绕开这一点，每个 managed listener 都会用带有 `active` 标记的动态代理包装用户回调。
 * 在重载时，所有 WORLD/SERVER_CACHE 作用域的包装器都会被停用。
 *
 * 该对象通过 [initialize] 在 [KattonFabric.onInitialize] 中完成一次初始化。
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
        val environment: ScriptEnvironment?,
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
                val environment = ScriptExecutionContext.currentScriptEnvironment()
                val wrapper = Proxy.newProxyInstance(
                    eventClass.classLoader,
                    arrayOf(eventClass)
                ) { _, _, args ->
                    val reg = registrations[id] ?: return@newProxyInstance null
                    if (reg.active && args != null && args.isNotEmpty()) {
                        try {
                            ScriptExecutionContext.withEnvironment(environment) {
                                ScriptExecutionContext.withScope(scope) {
                                    ScriptExecutionContext.withOwner(owner) {
                                        handler(args[0])
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            LOGGER.warn("Managed Fabric event handler failed for {}", owner, t)
                        }
                    }
                    null
                }
                val registration = FabricRegistration(id, wrapper, scope, environment, active = true)
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

            override fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
                val ids = scopeRegistrations[scope] ?: return
                val matchingIds = ids.filter { id -> registrations[id]?.environment == environment }
                matchingIds.forEach { id ->
                    registrations.remove(id)?.active = false
                    ids.remove(id)
                }
                if (ids.isEmpty()) {
                    scopeRegistrations.remove(scope)
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

// Fabric-specific script API

fun <T : Any> registerFabricEvent(
    event: Event<T>,
    callback: T
): ManagedEventHandle {
    val provider = provider
        ?: error("ManagedEvents.provider not initialized - call FabricManagedEvents.initialize() first")

    val scope = ScriptExecutionContext.currentScriptScope()
    val owner = ScriptExecutionContext.currentScriptOwner() ?: "unknown"
    val environment = ScriptExecutionContext.currentScriptEnvironment()
    val iface = callback::class.java

    val handle = provider.register(iface, owner, scope, 2, false) { /* handled by proxy */ }

    val activeWrapper = Proxy.newProxyInstance(
        iface.classLoader,
        arrayOf(iface)
    ) { _, method, args ->
        val reg = FabricManagedEvents.registrations[handle.id]
        if (reg != null && reg.active) {
            try {
                ScriptExecutionContext.withEnvironment(environment) {
                    ScriptExecutionContext.withScope(scope) {
                        ScriptExecutionContext.withOwner(owner) {
                            method.invoke(callback, *(args ?: emptyArray()))
                        }
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

fun unregisterFabricEvent(handle: ManagedEventHandle) {
    provider?.unregister(handle)
}
