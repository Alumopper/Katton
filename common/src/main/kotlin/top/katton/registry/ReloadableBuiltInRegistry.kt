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
        }
    }

    @Synchronized
    fun beginWorldCleanup(): List<Identifier> {
        if (Katton.debugRegistryLogging) logger.info("beginWorldCleanup()")
        return tracker.beginWorldCleanup(
            registry = builtInRegistry as MappedRegistry<T>,
            resourceKey = { id -> ResourceKey.create(registryKey, id) }
        )
    }

    fun registerGlobal(id: Identifier, value: T): T {
        if (Katton.debugRegistryLogging) logger.info("registerGlobal id={} mode=GLOBAL", id)
        return if (requiresIntrusiveHolders) {
            @Suppress("UNCHECKED_CAST")
            val registry = builtInRegistry as MappedRegistry<T>
            withUnfrozenAndHolders(registry) {
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
            withUnfrozenAndHolders(registry) {
                val value = builder()
                registry.createIntrusiveHolder(value)
                Registry.register(builtInRegistry, id, value)
            }
        } else {
            withUnfrozenRegistry(registry) {
                Registry.register(builtInRegistry, id, builder())
            }
        }
        onRegistered?.invoke(result)
        return result
    }

    /**
     * Dispatches registration to the correct strategy based on [mode].
     *
     * - [RegisterMode.GLOBAL]: permanent, only allowed during [LoadState.INIT]
     * - [RegisterMode.WORLD]: lives for one world session, survives `/katton reload` but
     *   unregistered on world leave
     * - [RegisterMode.RELOADABLE]: cleaned up and re-registered on `/katton reload`
     */
    fun registerWithMode(
        id: Identifier,
        mode: RegisterMode,
        builder: () -> T
    ): T {
        if (Katton.debugRegistryLogging) logger.info("registerWithMode id={} mode={}", id, mode)
        return when (mode) {
            RegisterMode.GLOBAL -> {
                if (Katton.globalState.after(LoadState.INIT)) {
                    error("RegisterMode.GLOBAL can only be used during the INIT phase. " +
                        "Current state: ${Katton.globalState}. Use RegisterMode.WORLD or RegisterMode.RELOADABLE instead.")
                }
                registerGlobal(id, builder())
            }
            RegisterMode.WORLD -> ensureRegistered(id, builder)
            RegisterMode.RELOADABLE -> ensureRegistered(id, builder)
        }
    }

    fun markManaged(id: Identifier, mode: RegisterMode) {
        val tracked = tracker.markManaged(id, mode)
        if (tracked) {
            synchronized(staleManagedIds) { staleManagedIds.remove(id) }
        }
    }

    fun staleManagedIdsSnapshot(): Set<Identifier> = synchronized(staleManagedIds) {
        staleManagedIds.toSet()
    }

    fun managedIdsSnapshot(): Set<Identifier> = tracker.managedIdsSnapshot()
}
