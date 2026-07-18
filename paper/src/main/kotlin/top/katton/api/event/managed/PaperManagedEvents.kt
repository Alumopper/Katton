package top.katton.api.event.managed

import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext

/**
 * %en
 * Paper (Bukkit) implementation of [ManagedListenerProvider].
 *
 * Registers native Bukkit event listeners via [org.bukkit.plugin.PluginManager.registerEvent],
 * tracks them by scope for automatic cleanup on reload, and supports manual
 * unregistration via [ManagedEventHandle].
 *
 * Initialized once in [KattonPaperPlugin.onEnable] via [initialize].
 *
 * %zh
 * [ManagedListenerProvider] 的 Paper (Bukkit) 实现。
 *
 * 通过 [org.bukkit.plugin.PluginManager.registerEvent] 注册原生 Bukkit 事件监听器，
 * 按作用域跟踪它们以便在重载时自动清理，并支持通过 [ManagedEventHandle] 手动注销。
 *
 * 该对象通过 [initialize] 在 [KattonPaperPlugin.onEnable] 中完成一次初始化。
 */
object PaperManagedEvents {
    private val LOGGER = LoggerFactory.getLogger(PaperManagedEvents::class.java)
    private var nextId = 0L
    private val registrations = mutableMapOf<Long, ManagedRegistration>()
    private val scopeRegistrations = mutableMapOf<ScriptPackScope, MutableSet<Long>>()
    private var pluginRef: JavaPlugin? = null

    /**
     * %en
     * Must be called once during plugin initialization.
     * Installs the Paper-specific [provider] on [ManagedEvents].
     *
     * %zh
     * 必须在插件初始化期间调用一次。
     * 将 Paper 专用的 [provider] 安装到 [ManagedEvents] 上。
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
                val environment = ScriptExecutionContext.currentScriptEnvironment()

                val listener = object : org.bukkit.event.Listener {}

                val executor = EventExecutor { _, event ->
                    try {
                        ScriptExecutionContext.withEnvironment(environment) {
                            ScriptExecutionContext.withScope(scope) {
                                ScriptExecutionContext.withOwner(owner) {
                                    handler(event)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        LOGGER.warn("Managed Paper event handler failed for {}", owner, t)
                    }
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

                val registration = ManagedRegistration(id, eventClass, listener, scope, environment)
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

            override fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
                val ids = scopeRegistrations[scope] ?: return
                val matchingIds = ids.filter { id -> registrations[id]?.environment == environment }
                matchingIds.forEach { id ->
                    registrations.remove(id)?.let { HandlerList.unregisterAll(it.listener) }
                    ids.remove(id)
                }
                if (ids.isEmpty()) {
                    scopeRegistrations.remove(scope)
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
     * %en
     * Unregister all native listeners registered through this API.
     * Called on full server shutdown.
     *
     * %zh
     * 注销通过此 API 注册的所有原生监听器。
     * 在服务器完全关闭时调用。
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
        val scope: ScriptPackScope?,
        val environment: ScriptEnvironment?
    )
}
