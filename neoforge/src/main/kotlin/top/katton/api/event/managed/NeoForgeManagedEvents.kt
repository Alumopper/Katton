package top.katton.api.event.managed

import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import org.slf4j.LoggerFactory
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext

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
    private var nextId = 0L
    private val registrations = mutableMapOf<Long, ManagedRegistration>()
    private val scopeRegistrations = mutableMapOf<ScriptPackScope, MutableSet<Long>>()

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
                val id = nextId++

                @Suppress("UNCHECKED_CAST")
                val eventType = eventClass as Class<out Event>

                val listener = object {
                    // Dummy object - IEventBus tracks by identity for unregister()
                }

                NeoForge.EVENT_BUS.addListener(
                    EventPriority.entries.toTypedArray().getOrElse(priority) { EventPriority.NORMAL },
                    ignoreCancelled,
                    eventType
                ) { event ->
                    try {
                        ScriptExecutionContext.withScope(scope) {
                            ScriptExecutionContext.withOwner(owner) {
                                handler(event)
                            }
                        }
                    } catch (t: Throwable) {
                        LOGGER.warn("Managed NeoForge event handler failed for {}", owner, t)
                    }
                }

                val registration = ManagedRegistration(id, eventClass, listener, scope)
                registrations[id] = registration
                if (scope != null) {
                    scopeRegistrations.getOrPut(scope) { mutableSetOf() }.add(id)
                }

                return ManagedEventHandle(id, eventClass)
            }

            override fun unregister(handle: ManagedEventHandle) {
                val reg = registrations.remove(handle.id) ?: return
                NeoForge.EVENT_BUS.unregister(reg.listener)
                reg.scope?.let { scopeRegistrations[it]?.remove(handle.id) }
            }

            override fun clearByScope(scope: ScriptPackScope) {
                val ids = scopeRegistrations.remove(scope) ?: return
                ids.forEach { id ->
                    registrations.remove(id)?.let { NeoForge.EVENT_BUS.unregister(it.listener) }
                }
            }

            override fun clearAll() {
                registrations.values.forEach { NeoForge.EVENT_BUS.unregister(it.listener) }
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

    private data class ManagedRegistration(
        val id: Long,
        val eventClass: Class<*>,
        val listener: Any,
        val scope: ScriptPackScope?
    )
}
