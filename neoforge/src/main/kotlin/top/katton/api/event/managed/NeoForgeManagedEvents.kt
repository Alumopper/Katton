package top.katton.api.event.managed

import net.neoforged.bus.api.Event
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import top.katton.pack.ScriptPackScope

/**
 * NeoForge implementation of [ManagedListenerProvider].
 *
 * Registers native NeoForge event listeners via [IEventBus.addListener],
 * tracks them by scope for automatic cleanup on reload, and supports manual
 * unregistration via [ManagedEventHandle].
 *
 * Initialized once in [KattonNeoForge] constructor via [initialize].
 */
object NeoForgeManagedEvents {
    private var nextId = 0L
    private val registrations = mutableMapOf<Long, ManagedRegistration>()
    private val scopeRegistrations = mutableMapOf<ScriptPackScope, MutableSet<Long>>()

    /**
     * Must be called once during mod construction.
     * Installs the NeoForge-specific [provider] on [ManagedEvents].
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
                    // Dummy object — IEventBus tracks by identity for unregister()
                }

                NeoForge.EVENT_BUS.addListener(
                    EventPriority.entries.toTypedArray().getOrElse(priority) { EventPriority.NORMAL },
                    ignoreCancelled,
                    eventType
                ) { event ->
                    handler(event)
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
