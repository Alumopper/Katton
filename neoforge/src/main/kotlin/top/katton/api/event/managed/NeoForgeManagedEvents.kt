package top.katton.api.event.managed

import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.LoggerFactory
import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * %en
 * NeoForge implementation of [ManagedListenerProvider].
 *
 * Registers native NeoForge event listeners via [IEventBus.addListener],
 * tracks them by scope for automatic cleanup on reload, and supports manual
 * unregistration via [ManagedEventHandle].
 *
 * Initialized once in [KattonNeoForge] constructor via [initialize].
 *
 * %zh
 * NeoForge 版 [ManagedListenerProvider] 的实现。
 * 通过 [IEventBus.addListener] 注册原生 NeoForge 事件监听器，按作用域跟踪以便在重载时自动清理，并支持通过 [ManagedEventHandle] 手动注销。
 * 在 [KattonNeoForge] 构造期间通过 [initialize] 初始化一次。
 */
object NeoForgeManagedEvents {
    private val LOGGER = LoggerFactory.getLogger(NeoForgeManagedEvents::class.java)
    private val nextId = AtomicLong()
    private val registrationLock = Any()
    private val registrations = ConcurrentHashMap<Long, ManagedRegistration>()

    /**
     * %en
     * Must be called once during mod construction.
     * Installs the NeoForge-specific [provider] on [ManagedEvents].
     *
     * %zh
     * 必须在模组构造期间调用一次。
     * 在 [ManagedEvents] 上安装 NeoForge 专用的 [provider]。
     */
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

                @Suppress("UNCHECKED_CAST")
                val eventType = eventClass as Class<Event>
                val active = AtomicBoolean(true)

                val listener = Consumer<Event> { event ->
                    if (!active.get()) return@Consumer
                    try {
                        ScriptExecutionContext.withEnvironment(environment) {
                            ScriptExecutionContext.withScope(scope) {
                                ScriptExecutionContext.withOwner(owner) {
                                    handler(event)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        LOGGER.warn("Managed NeoForge event handler failed for {}", owner, t)
                    }
                }

                val registration = ManagedRegistration(id, eventClass, listener, owner, scope, environment, active)
                synchronized(registrationLock) {
                    try {
                        NeoForge.EVENT_BUS.addListener(
                            toNeoForgePriority(priority),
                            ignoreCancelled,
                            eventType,
                            listener
                        )
                        registrations[id] = registration
                    } catch (failure: Throwable) {
                        active.set(false)
                        runCatching { NeoForge.EVENT_BUS.unregister(listener) }
                        throw failure
                    }
                }

                return ManagedEventHandle(id, eventClass)
            }

            override fun unregister(handle: ManagedEventHandle) {
                val reg = synchronized(registrationLock) {
                    registrations.remove(handle.id)?.also { it.active.set(false) }
                } ?: return
                NeoForge.EVENT_BUS.unregister(reg.listener)
            }

            override fun clearByScope(scope: ScriptPackScope) {
                removeMatching { it.scope == scope }.forEach { NeoForge.EVENT_BUS.unregister(it.listener) }
            }

            override fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
                removeMatching { it.scope == scope && it.environment == environment }
                    .forEach { NeoForge.EVENT_BUS.unregister(it.listener) }
            }

            override fun clearByOwnerPrefix(ownerPrefix: String) {
                removeMatching { it.owner.startsWith(ownerPrefix) }
                    .forEach { NeoForge.EVENT_BUS.unregister(it.listener) }
            }

            override fun clearAll() {
                removeMatching { true }.forEach { NeoForge.EVENT_BUS.unregister(it.listener) }
            }
        }
    }

    @JvmStatic
    fun shutdown() {
        provider?.clearAll()
        synchronized(registrationLock) { registrations.clear() }
    }

    /** Deactivate under the map lock, then let the caller unregister natively. */
    private fun removeMatching(predicate: (ManagedRegistration) -> Boolean): List<ManagedRegistration> =
        synchronized(registrationLock) {
            registrations.values.filter(predicate).onEach { registration ->
                registration.active.set(false)
                registrations.remove(registration.id)
            }
        }

    /** Keep the script API's LOWEST..HIGHEST numbering independent of enum declaration order. */
    private fun toNeoForgePriority(priority: Int): EventPriority = when (priority) {
        0 -> EventPriority.LOWEST
        1 -> EventPriority.LOW
        3 -> EventPriority.HIGH
        4, 5 -> EventPriority.HIGHEST // NeoForge has no Bukkit-style MONITOR priority.
        else -> EventPriority.NORMAL
    }

    private data class ManagedRegistration(
        val id: Long,
        val eventClass: Class<*>,
        val listener: Any,
        val owner: String,
        val scope: ScriptPackScope?,
        val environment: ScriptEnvironment?,
        val active: AtomicBoolean
    )
}
