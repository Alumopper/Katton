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
    private val owners = mutableMapOf<Identifier, String?>()
    private val persistentIds = mutableSetOf<Identifier>()
    private val persistentRecords = mutableMapOf<Identifier, top.katton.engine.ManagedResources.Record>()
    private fun canOwn(id: Identifier, owner: String?): Boolean {
        if (id !in owners || owners[id] == owner) return true
        if (id !in persistentIds) return false
        val previous = top.katton.util.ScriptExecutionContext.identityOf(owners[id]) ?: return false
        val next = top.katton.util.ScriptExecutionContext.identityOf(owner) ?: return false
        return previous.environment == next.environment && previous.lifecycle == next.lifecycle && previous.syncId == next.syncId
    }

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
        ).also { removed -> removed.forEach { id ->
            owners.remove(id)
            persistentIds.remove(id)
            persistentRecords.remove(id)?.let(top.katton.engine.ManagedResources::release)
        } }
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
        val owner = top.katton.util.ScriptExecutionContext.currentScriptOwner()
        require(canOwn(id, owner)) { "Registry entry $id is owned by another script instance" }
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
        val owner = top.katton.util.ScriptExecutionContext.currentScriptOwner()
        require(canOwn(id, owner)) { "Registry entry $id is owned by another script instance" }
        // Reusing a persistent value must retain the generation that actually defined its type.
        if (id in persistentIds) {
            tracker.markManaged(id, mode)
            return
        }
        owners[id] = owner
        if (mode != RegisterMode.RELOADABLE) persistentIds += id
        run {
            var restore: (() -> Unit)? = null
            val persistent = mode != RegisterMode.RELOADABLE
            val record = top.katton.engine.ManagedResources.record(
                attach = { checkNotNull(restore).invoke(); owners[id] = owner; if (persistent) persistentIds += id },
                detach = {
                    restore = captureRegistryEntry(builtInRegistry as MappedRegistry<T>, ResourceKey.create(registryKey, id))
                    unregisterAll(builtInRegistry as MappedRegistry<T>, listOf(id)) { ResourceKey.create(registryKey, it) }
                    owners.remove(id)
                    persistentIds.remove(id)
                },
                dispose = { if (persistent) persistentRecords.remove(id) },
                persistent = persistent
            )
            if (persistent && record != null) persistentRecords[id] = record
        }
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
