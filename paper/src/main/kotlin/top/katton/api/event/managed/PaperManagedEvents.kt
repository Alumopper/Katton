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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    private val nextId = AtomicLong()
    private val registrationLock = Any()
    private val registrations = ConcurrentHashMap<Long, ManagedRegistration>()
    private var pluginRef: JavaPlugin? = null
    private var installedProvider: ManagedListenerProvider? = null

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
        if (pluginRef === plugin && provider === installedProvider) return
        installedProvider?.clearAll()
        pluginRef = plugin

        val paperProvider = object : ManagedListenerProvider {
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

                val listener = object : org.bukkit.event.Listener {}
                val active = AtomicBoolean(true)

                val executor = EventExecutor { _, event ->
                    if (!active.get()) return@EventExecutor
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

                val registration = ManagedRegistration(id, eventClass, listener, owner, scope, environment, active)
                synchronized(registrationLock) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        plugin.server.pluginManager.registerEvent(
                            eventClass as Class<out Event>,
                            listener,
                            toPaperPriority(priority),
                            executor,
                            plugin,
                            ignoreCancelled
                        )
                        registrations[id] = registration
                    } catch (failure: Throwable) {
                        active.set(false)
                        HandlerList.unregisterAll(listener)
                        throw failure
                    }
                }

                return ManagedEventHandle(id, eventClass)
            }

            override fun unregister(handle: ManagedEventHandle) {
                val reg = synchronized(registrationLock) {
                    registrations.remove(handle.id)?.also { it.active.set(false) }
                } ?: return
                HandlerList.unregisterAll(reg.listener)
            }

            override fun clearByScope(scope: ScriptPackScope) {
                removeMatching { it.scope == scope }.forEach { HandlerList.unregisterAll(it.listener) }
            }

            override fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
                removeMatching { it.scope == scope && it.environment == environment }
                    .forEach { HandlerList.unregisterAll(it.listener) }
            }

            override fun clearByOwnerPrefix(ownerPrefix: String) {
                removeMatching { it.owner.startsWith(ownerPrefix) }
                    .forEach { HandlerList.unregisterAll(it.listener) }
            }

            override fun clearAll() {
                removeMatching { true }.forEach { HandlerList.unregisterAll(it.listener) }
            }
        }
        installedProvider = paperProvider
        provider = paperProvider
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
        installedProvider?.clearAll()
        if (provider === installedProvider) provider = null
        installedProvider = null
        pluginRef = null
        synchronized(registrationLock) { registrations.clear() }
    }

    private fun removeMatching(predicate: (ManagedRegistration) -> Boolean): List<ManagedRegistration> =
        synchronized(registrationLock) {
            registrations.values.filter(predicate).onEach { registration ->
                registration.active.set(false)
                registrations.remove(registration.id)
            }
        }

    /** Do not rely on Bukkit preserving EventPriority's source declaration order. */
    private fun toPaperPriority(priority: Int): EventPriority = when (priority) {
        0 -> EventPriority.LOWEST
        1 -> EventPriority.LOW
        3 -> EventPriority.HIGH
        4 -> EventPriority.HIGHEST
        5 -> EventPriority.MONITOR
        else -> EventPriority.NORMAL
    }

    private data class ManagedRegistration(
        val id: Long,
        val eventClass: Class<*>,
        val listener: org.bukkit.event.Listener,
        val owner: String,
        val scope: ScriptPackScope?,
        val environment: ScriptEnvironment?,
        val active: AtomicBoolean
    )
}
