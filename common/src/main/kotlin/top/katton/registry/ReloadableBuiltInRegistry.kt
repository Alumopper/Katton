package top.katton.registry

import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import top.katton.Katton
import top.katton.LoadState
import org.slf4j.LoggerFactory

/**
 * Generic reloadable registry for Minecraft's BuiltInRegistries.
 *
 * Handles registration, unregistration, ownership tracking, and pending
 * registration queuing for any type stored in a BuiltInRegistry.
 *
 * @param T The Minecraft registry value type (e.g. Item, Block, MobEffect)
 */
internal class ReloadableBuiltInRegistry<T : Any>(
    val builtInRegistry: Registry<T>,
    private val registryKey: ResourceKey<out Registry<T>>,
    private val requiresIntrusiveHolders: Boolean,
    private val unregisterOnReload: Boolean = true
) {
    private val logger = LoggerFactory.getLogger("KattonRegistry")
    private val tracker = OwnershipTracker()
    private val pendingRegistrations = mutableListOf<Pair<Identifier, () -> T>>()
    private val staleManagedIds = linkedSetOf<Identifier>()

    @Synchronized
    fun beginReload(): List<Identifier> {
        if (Katton.debugRegistryLogging) logger.info("beginReload()")
        return tracker.beginReload(
            registry = builtInRegistry as MappedRegistry<T>,
            resourceKey = { id -> ResourceKey.create(registryKey, id) },
            unregisterFromRegistry = unregisterOnReload
        )
        .also {
            if (!unregisterOnReload) {
                synchronized(staleManagedIds) {
                    staleManagedIds.addAll(it)
                }
            }
            if (!unregisterOnReload && Katton.debugRegistryLogging) {
                logger.info("beginReload(): keeping registry entries, clearing ownership only")
            }
        }
    }

    fun registerGlobal(id: Identifier, value: T): T {
        if (Katton.debugRegistryLogging) logger.info("registerGlobal id={} mode=GLOBAL", id)
        return if (requiresIntrusiveHolders) {
            @Suppress("UNCHECKED_CAST")
            val registry = builtInRegistry as MappedRegistry<T>
            RegistryMutationUtil.withUnfrozenAndHolders(registry) {
                registry.createIntrusiveHolder(value)
                Registry.register(builtInRegistry, id, value)
            }
        } else {
            Registry.register(builtInRegistry, id, value)
        }
    }

    fun ensureRegistered(
        id: Identifier,
        builder: () -> T,
        onExisting: ((T) -> Unit)? = null
    ): T {
        val existing = builtInRegistry.getOptional(id)
        if (existing.isPresent) {
            onExisting?.invoke(existing.get())
            synchronized(staleManagedIds) { staleManagedIds.remove(id) }
            return existing.get()
        }
        return registerNew(id, builder)
    }

    fun registerNew(
        id: Identifier,
        builder: () -> T,
        onRegistered: ((T) -> Unit)? = null
    ): T {
        if (Katton.debugRegistryLogging) logger.info("registerNew id={} (unfreezing registry)...", id)
        @Suppress("UNCHECKED_CAST")
        val registry = builtInRegistry as MappedRegistry<T>
        val result = if (requiresIntrusiveHolders) {
            RegistryMutationUtil.withUnfrozenAndHolders(registry) {
                val value = builder()
                registry.createIntrusiveHolder(value)
                Registry.register(builtInRegistry, id, value)
            }
        } else {
            RegistryMutationUtil.withUnfrozenRegistry(registry) {
                Registry.register(builtInRegistry, id, builder())
            }
        }
        onRegistered?.invoke(result)
        return result
    }

    fun registerWithMode(
        id: Identifier,
        mode: RegisterMode,
        builder: () -> T
    ): T {
        if (Katton.debugRegistryLogging) logger.info("registerWithMode id={} mode={}", id, mode)
        return when (mode) {
        RegisterMode.GLOBAL -> registerGlobal(id, builder())
        RegisterMode.RELOADABLE -> ensureRegistered(id, builder)
        RegisterMode.AUTO -> {
            if (Katton.globalState.after(LoadState.INIT)) {
                ensureRegistered(id, builder)
            } else {
                registerGlobal(id, builder())
            }
        }
        }
    }

    fun markManaged(id: Identifier, mode: RegisterMode) {
        val tracked = tracker.markManaged(id, mode)
        if (tracked) {
            synchronized(staleManagedIds) { staleManagedIds.remove(id) }
        }
    }

    fun addPendingRegistration(id: Identifier, factory: () -> T) {
        synchronized(pendingRegistrations) {
            pendingRegistrations.add(id to factory)
        }
    }

    fun flushPendingRegistrations(onFlushed: (Identifier, T) -> Unit = { _, _ -> }) {
        val copy = synchronized(pendingRegistrations) {
            val c = pendingRegistrations.toList()
            pendingRegistrations.clear()
            c
        }
        copy.forEach { (id, factory) ->
            val value = registerWithMode(id, RegisterMode.RELOADABLE, factory)
            onFlushed(id, value)
        }
    }

    fun hasPendingRegistrations(): Boolean = synchronized(pendingRegistrations) {
        pendingRegistrations.isNotEmpty()
    }

    fun staleManagedIdsSnapshot(): Set<Identifier> = synchronized(staleManagedIds) {
        staleManagedIds.toSet()
    }

    fun managedIdsSnapshot(): Set<Identifier> = tracker.managedIdsSnapshot()
}
