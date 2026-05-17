package top.katton.api.event.managed

import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import top.katton.pack.ScriptPackScope

/**
 * Paper (Bukkit) implementation of [ManagedListenerProvider].
 *
 * Registers native Bukkit event listeners via [org.bukkit.plugin.PluginManager.registerEvent],
 * tracks them by scope for automatic cleanup on reload, and supports manual
 * unregistration via [ManagedEventHandle].
 *
 * Initialized once in [KattonPaperPlugin.onEnable] via [initialize].
 */
object PaperManagedEvents {
    private var nextId = 0L
    private val registrations = mutableMapOf<Long, ManagedRegistration>()
    private val scopeRegistrations = mutableMapOf<ScriptPackScope, MutableSet<Long>>()
    private var pluginRef: JavaPlugin? = null

    /**
     * Must be called once during plugin initialization.
     * Installs the Paper-specific [provider] on [ManagedEvents].
     */
    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        if (provider != null) return
        pluginRef = plugin

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

                val listener = object : org.bukkit.event.Listener {}

                val executor = EventExecutor { _, event ->
                    handler(event)
                }

                @Suppress("UNCHECKED_CAST")
                plugin.server.pluginManager.registerEvent(
                    eventClass as Class<out Event>,
                    listener,
                    EventPriority.entries.toTypedArray().getOrElse(priority) { EventPriority.NORMAL },
                    executor,
                    plugin,
                    ignoreCancelled
                )

                val registration = ManagedRegistration(id, eventClass, listener, scope)
                registrations[id] = registration
                if (scope != null) {
                    scopeRegistrations.getOrPut(scope) { mutableSetOf() }.add(id)
                }

                return ManagedEventHandle(id, eventClass)
            }

            override fun unregister(handle: ManagedEventHandle) {
                val reg = registrations.remove(handle.id) ?: return
                HandlerList.unregisterAll(reg.listener)
                reg.scope?.let { scopeRegistrations[it]?.remove(handle.id) }
            }

            override fun clearByScope(scope: ScriptPackScope) {
                val ids = scopeRegistrations.remove(scope) ?: return
                ids.forEach { id ->
                    registrations.remove(id)?.let { HandlerList.unregisterAll(it.listener) }
                }
            }

            override fun clearAll() {
                registrations.values.forEach { HandlerList.unregisterAll(it.listener) }
                registrations.clear()
                scopeRegistrations.clear()
            }
        }
    }

    /**
     * Unregister all native listeners registered through this API.
     * Called on full server shutdown.
     */
    @JvmStatic
    fun shutdown() {
        provider?.clearAll()
        registrations.clear()
        scopeRegistrations.clear()
    }

    private data class ManagedRegistration(
        val id: Long,
        val eventClass: Class<*>,
        val listener: org.bukkit.event.Listener,
        val scope: ScriptPackScope?
    )
}
