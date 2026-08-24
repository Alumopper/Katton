package top.katton.api.event.managed

import net.fabricmc.fabric.api.event.Event
import org.slf4j.LoggerFactory
import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    private val nextId = AtomicLong()
    private val registrationLock = Any()
    val registrations = ConcurrentHashMap<Long, FabricRegistration>()

    /**
     * The Fabric event permanently owns the proxy, so the proxy may only retain
     * this small host-loaded state object. Clearing [callback] releases the old
     * script lambda and its classloader even though Fabric cannot remove the
     * proxy itself.
     */
    class FabricListenerState(@Volatile var callback: Any? = null)

    class FabricRegistration(
        val id: Long,
        val owner: String,
        val scope: ScriptPackScope?,
        val environment: ScriptEnvironment?,
        val state: FabricListenerState
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
                val id = nextId.getAndIncrement()
                val environment = ScriptExecutionContext.currentScriptEnvironment()
                // Fabric callbacks are installed by registerFabricEvent below.
                // This provider entry only supplies lifecycle tracking; creating a
                // second, never-registered proxy here wasted one proxy per listener.
                val registration = FabricRegistration(id, owner, scope, environment, FabricListenerState())
                synchronized(registrationLock) { registrations[id] = registration }
                return ManagedEventHandle(id, eventClass)
            }

            override fun unregister(handle: ManagedEventHandle) {
                synchronized(registrationLock) {
                    registrations.remove(handle.id)?.state?.callback = null
                }
            }

            override fun clearByScope(scope: ScriptPackScope) {
                synchronized(registrationLock) {
                    registrations.values.filter { it.scope == scope }.map { it.id }.forEach { id ->
                        registrations.remove(id)?.state?.callback = null
                    }
                }
            }

            override fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
                synchronized(registrationLock) {
                    registrations.values
                        .filter { it.scope == scope && it.environment == environment }
                        .map { it.id }
                        .forEach { id -> registrations.remove(id)?.state?.callback = null }
                }
            }

            override fun clearByOwnerPrefix(ownerPrefix: String) {
                synchronized(registrationLock) {
                    registrations.values
                        .filter { it.owner.startsWith(ownerPrefix) }
                        .map { it.id }
                        .forEach { id -> registrations.remove(id)?.state?.callback = null }
                }
            }

            override fun clearAll() {
                synchronized(registrationLock) {
                    registrations.values.forEach { it.state.callback = null }
                    registrations.clear()
                }
            }
        }
    }

    @JvmStatic
    fun shutdown() {
        provider?.clearAll()
        synchronized(registrationLock) { registrations.clear() }
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
    val listenerInterface = resolveFabricListenerInterface(event, callback)

    val handle = provider.register(listenerInterface, owner, scope, 2, false) { /* handled by proxy */ }
    val state = FabricManagedEvents.registrations[handle.id]?.state
        ?: error("Fabric managed-listener registration disappeared before native registration")
    state.callback = callback

    val activeWrapper = try {
        // Compute neutral values once. Inactive proxies remain in Fabric's callback
        // array forever, so their hot path must not perform reflection or allocate.
        val inactiveReturns = listenerInterface.methods.associateWith { method ->
            inferInactiveReturnValue(method.returnType)
        }

        Proxy.newProxyInstance(
            listenerInterface.classLoader,
            arrayOf(listenerInterface)
        ) { proxy, method, args ->
            if (method.declaringClass == Any::class.java) {
                return@newProxyInstance when (method.name) {
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "KattonFabricManagedListener(${listenerInterface.name})"
                    else -> null
                }
            }

            val currentCallback = state.callback
            if (currentCallback != null) {
                try {
                    ScriptExecutionContext.withEnvironment(environment) {
                        ScriptExecutionContext.withScope(scope) {
                            ScriptExecutionContext.withOwner(owner) {
                                method.invoke(currentCallback, *(args ?: emptyArray()))
                            }
                        }
                    }
                } catch (t: Throwable) {
                    val failure = (t as? InvocationTargetException)?.targetException ?: t
                    FabricManagedEvents.LOGGER.warn("Managed Fabric event handler failed for {}", owner, failure)
                    inactiveReturns[method]
                }
            } else {
                inactiveReturns[method]
            }
        }
    } catch (failure: Throwable) {
        provider.unregister(handle)
        throw failure
    }

    @Suppress("UNCHECKED_CAST")
    try {
        event.register(activeWrapper as T)
    } catch (failure: Throwable) {
        // Do not retain a lifecycle registration when Fabric rejected the
        // callback type or the event implementation failed during registration.
        provider.unregister(handle)
        throw failure
    }

    return handle
}

fun unregisterFabricEvent(handle: ManagedEventHandle) {
    provider?.unregister(handle)
}

/** Finds the callback contract from Fabric's aggregate invoker, not the lambda implementation class. */
private fun <T : Any> resolveFabricListenerInterface(event: Event<T>, callback: T): Class<*> {
    val aggregateInvoker = event.invoker()
    return collectInterfaces(aggregateInvoker.javaClass)
        .firstOrNull { candidate -> candidate.isInstance(callback) }
        ?: collectInterfaces(callback.javaClass)
            .firstOrNull { candidate -> candidate.isInstance(aggregateInvoker) }
        ?: error(
            "Cannot determine Fabric callback interface shared by ${callback.javaClass.name} " +
                "and ${aggregateInvoker.javaClass.name}"
        )
}

private fun collectInterfaces(type: Class<*>): List<Class<*>> {
    val interfaces = LinkedHashSet<Class<*>>()
    fun visit(current: Class<*>?) {
        if (current == null) return
        current.interfaces.forEach { candidate ->
            if (interfaces.add(candidate)) visit(candidate)
        }
        visit(current.superclass)
    }
    visit(type)
    return interfaces.toList()
}

/**
 * Best-effort neutral result for a proxy Fabric can no longer unregister.
 * Fabric result enums conventionally expose PASS or DEFAULT; primitive and
 * common JDK return types have allocation-free neutral values.
 */
private fun inferInactiveReturnValue(returnType: Class<*>): Any? {
    if (returnType == Void.TYPE) return null
    if (returnType == Unit::class.java) return Unit
    if (returnType == Optional::class.java) return Optional.empty<Any>()
    if (returnType.isPrimitive) {
        return when (returnType) {
            Boolean::class.javaPrimitiveType -> false
            Char::class.javaPrimitiveType -> '\u0000'
            Byte::class.javaPrimitiveType -> 0.toByte()
            Short::class.javaPrimitiveType -> 0.toShort()
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0F
            Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
    }

    val conventionalNames = listOf("PASS", "DEFAULT", "CONTINUE")
    return returnType.enumConstants
        ?.firstOrNull { constant -> (constant as Enum<*>).name in conventionalNames }
        ?: conventionalNames.firstNotNullOfOrNull { fieldName ->
            runCatching { returnType.getField(fieldName).get(null) }
                .getOrNull()
                ?.takeIf(returnType::isInstance)
        }
}
