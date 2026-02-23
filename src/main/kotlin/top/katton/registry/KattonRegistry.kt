package top.katton.registry

import com.mojang.logging.LogUtils
import com.mojang.serialization.Codec
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import top.katton.Katton
import top.katton.Katton.MOD_ID
import top.katton.LoadState
import top.katton.mixin.MappedRegistryAccessor
import top.katton.registry.KattonItemProperties.Companion.components
import top.katton.util.Event
import java.util.concurrent.ConcurrentHashMap

fun id(namespace: String, path: String) = Identifier.fromNamespaceAndPath(namespace, path)
fun id(string: String) = Identifier.parse(string)


interface Identifiable {
    val id: Identifier
}


enum class RegisterMode {
    GLOBAL,
    RELOADABLE,
    AUTO
}


object KattonRegistry {

    private val LOGGER = LogUtils.getLogger()

    abstract class KattonRegistries<T : Identifiable>(
        override val id: Identifier,
        entries: MutableMap<Identifier, T> = mutableMapOf()
    ) : Identifiable, MutableMap<Identifier, T> by entries {
        abstract val default: T

        @Deprecated("Use Function In KattonRegistry Instead")
        internal fun register(implement: T): T {
            this[implement.id] = implement
            return implement
        }

        fun getOrDefault(id: Identifier) = this[id] ?: default

        fun initialize() {}
    }


    data class KattonItemEntry(
        override val id: Identifier,
        val item: Item,
        val agentItem: KattonItemInterface? = null
    ) : Identifiable {
        fun getDefaultInstance() = item.defaultInstance.apply {
            agentItem?.let { applyComponents(it.components()) }
        }
    }

    object ITEMS : KattonRegistries<KattonItemEntry>(id(MOD_ID, "item")) {
        override val default = new(components(id), RegisterMode.GLOBAL)

        private val managedIds = linkedSetOf<Identifier>()
        private val idsByOwner = ConcurrentHashMap<String, MutableSet<Identifier>>()

        // pending native registrations queued when scripts run during class-init / too-early phase
        private val pendingNativeRegistrations: MutableList<Pair<Identifier, () -> Item>> = mutableListOf()

        private fun registerGlobalItem(id: Identifier, item: Item): Item =
            Registry.register(BuiltInRegistries.ITEM, id, item)

        private fun currentOwner(): String = Event.currentScriptOwner() ?: "global"

        @Synchronized
        fun beginReload() {
            managedIds.forEach { remove(it) }
            managedIds.clear()
            idsByOwner.clear()
        }

        @Synchronized
        fun clearByOwner(owner: String) {
            val ids = idsByOwner.remove(owner) ?: return
            ids.forEach {
                remove(it)
                managedIds.remove(it)
            }
        }

        fun processPendingNativeRegistrations() {
            val toProcess: List<Pair<Identifier, () -> Item>>
            synchronized(pendingNativeRegistrations) {
                toProcess = pendingNativeRegistrations.toList()
                pendingNativeRegistrations.clear()
            }
            toProcess.forEach { (id, factory) ->
                try {
                    val item = ensureGlobalItemRegistered(id, factory)
                    // replace placeholder entry if present
                    this[id] = KattonItemEntry(id = id, item = item, agentItem = null)
                } catch (e: Throwable) {
                    LOGGER.error("Failed to process pending native registration for $id", e)
                }
            }
        }

        private fun ensureGlobalItemRegistered(
            id: Identifier,
            itemBuilder: () -> Item
        ): Item {
            val existing = BuiltInRegistries.ITEM.getOptional(id)
            if (existing.isPresent) {
                return existing.get()
            }

            val itemRegistry = BuiltInRegistries.ITEM as MappedRegistry<Item>
            val accessor = itemRegistry as MappedRegistryAccessor

            // Prepare to allow intrusive holder creation if the registry wasn't created with that support.
            val fieldNames = listOf("unregisteredIntrusiveHolders", "unregisteredIntrusiveHolder", "unregisteredIntrusive", "intrusiveHolders")
            var unregisteredField: java.lang.reflect.Field? = null
            run {
                var cls: Class<*>? = itemRegistry.javaClass
                while (cls != null) {
                    for (name in fieldNames) {
                        try {
                            val f = cls.getDeclaredField(name)
                            f.isAccessible = true
                            unregisteredField = f
                            break
                        } catch (_: Throwable) {
                        }
                    }
                    if (unregisteredField != null) break
                    cls = cls.superclass
                }
            }
            val previousUnregistered = try { unregisteredField?.get(itemRegistry) } catch (_: Throwable) { null }
            var injectedUnregistered = false
            try {
                if (previousUnregistered == null && unregisteredField != null) {
                    try {
                        // create an IdentityHashMap compatible with the registry's expected generic types
                        unregisteredField!!.set(itemRegistry, java.util.IdentityHashMap<Any, Any>())
                        injectedUnregistered = true
                    } catch (e: Throwable) {
                        // If injection fails, continue and rely on unfreezing boolean fields below
                    }
                }
            } catch (_: Throwable) {
            }

            // Try to unfreeze registry via accessor first (preferred).
            try {
                accessor.setFrozen(false)
            } catch (_: Throwable) {
                // ignore if mixin not applied
            }

            // As a fallback, try to clear any boolean fields that might block intrusive holder creation
            val registryClass = itemRegistry.javaClass
            val booleanFields = registryClass.declaredFields.filter { it.type == Boolean::class.javaPrimitiveType }
            val savedValues = mutableMapOf<java.lang.reflect.Field, Boolean>()
            booleanFields.forEach { f ->
                try {
                    f.isAccessible = true
                    val name = f.name.lowercase()
                    if (name.contains("frozen") || name.contains("intrusive") || name.contains("allow") || name.contains("cancreate") || name.contains("enabled")) {
                        savedValues[f] = f.getBoolean(itemRegistry)
                        f.setBoolean(itemRegistry, false)
                    }
                } catch (_: Throwable) {
                }
            }

            return try {
                val item = itemBuilder()
                val registered = registerGlobalItem(id, item)
                registered
            } finally {
                // restore booleans
                savedValues.forEach { (f, v) ->
                    try {
                        f.setBoolean(itemRegistry, v)
                    } catch (_: Throwable) {
                    }
                }
                // restore unregisteredIntrusiveHolders if we injected one
                try {
                    if (injectedUnregistered && unregisteredField != null) {
                        unregisteredField.set(itemRegistry, previousUnregistered)
                    }
                } catch (_: Throwable) {
                }
                try {
                    accessor.setFrozen(true)
                } catch (_: Throwable) {
                }
            }
        }

        private fun registerItemWithMode(
            id: Identifier,
            registerMode: RegisterMode,
            itemBuilder: () -> Item
        ): Item = when (registerMode) {
            RegisterMode.GLOBAL -> {
                registerGlobalItem(id, itemBuilder())
            }

            RegisterMode.RELOADABLE -> {
                ensureGlobalItemRegistered(id, itemBuilder)
            }

            RegisterMode.AUTO -> {
                Katton.globalState.let {
                    if (it.after(LoadState.INIT)) {
                        ensureGlobalItemRegistered(id, itemBuilder)
                    } else {
                        registerGlobalItem(id, itemBuilder())
                    }
                }
            }
        }

        fun new(
            components: KattonItemProperties,
            registerMode: RegisterMode = RegisterMode.AUTO,
            itemFactory: (KattonItemProperties) -> KattonItemInterface = { KattonItemWrapper(it, Items.AIR) }
        ): KattonItemEntry {
            // Delay constructing the actual KattonItem until the registry is temporarily unfrozen.
            val delayedFactory = {
                // ensure properties carry a resource id before Item ctor
                components.setId(ResourceKey.create(Registries.ITEM, components.id))
                KattonItem(components)
            }
            val entry = register(
                KattonItemEntry(
                    id = components.id,
                    item = registerItemWithMode(components.id, registerMode) { delayedFactory() },
                    agentItem = itemFactory(components)
                )
            )
            markManaged(components.id, registerMode)
            return entry
        }

        fun newNative(
            components: KattonItemProperties,
            registerMode: RegisterMode = RegisterMode.AUTO,
            itemFactory: (KattonItemProperties) -> Item
        ): KattonItemEntry {
            // If scripts are executed too early (class init), avoid constructing Item now.
            // Queue registration to be executed later during mod initialization.
            if (!Katton.globalState.after(LoadState.INIT)) {
                val placeholder = KattonItemEntry(id = components.id, item = Items.AIR, agentItem = null)
                register(placeholder)
                markManaged(components.id, registerMode)
                synchronized(pendingNativeRegistrations) {
                    pendingNativeRegistrations.add(components.id to {
                        // ensure properties carry a resource id before Item ctor
                        components.setId(ResourceKey.create(Registries.ITEM, components.id))
                        itemFactory(components)
                    })
                }
                return placeholder
            }

            // Wrap itemFactory into a delayed factory so we only construct the Item while the
            // registry is temporarily unfrozen.
            val delayedFactory = {
                // ensure properties carry a resource id before Item ctor
                components.setId(ResourceKey.create(Registries.ITEM, components.id))
                itemFactory(components)
            }
            val entry = register(
                KattonItemEntry(
                    id = components.id,
                    item = registerItemWithMode(components.id, registerMode) { delayedFactory() },
                    agentItem = null
                )
            )
            markManaged(components.id, registerMode)
            return entry
        }

        private fun markManaged(itemId: Identifier, registerMode: RegisterMode) {
            val owner = currentOwner()
            if (owner != "global" && registerMode != RegisterMode.GLOBAL) {
                managedIds.add(itemId)
                idsByOwner.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(itemId)
            }
        }
    }


    object COMPONENTS {
        val KATTON_ID: DataComponentType<String> by lazy {
            Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "id"),
                DataComponentType.builder<String>().persistent(Codec.STRING).build()
            )
        }
    }

    fun initialize() {
        // Ensure components are registered during mod initialization. If registration
        // fails due to registry being frozen, the lazy will be retried later when needed.
        try {
            COMPONENTS.KATTON_ID
        } catch (_: Throwable) {
        }
    }

}
