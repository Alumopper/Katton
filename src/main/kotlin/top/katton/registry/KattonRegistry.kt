package top.katton.registry

import com.mojang.serialization.Codec
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentMap
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
import top.katton.util.Event
import java.util.*
import java.util.Collections.emptySet
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates an Identifier from namespace and path.
 */
fun id(namespace: String, path: String) = Identifier.fromNamespaceAndPath(namespace, path)

/**
 * Parses an Identifier from string format "namespace:path".
 */
fun id(string: String) = Identifier.parse(string)

/**
 * Interface for objects that have an Identifier.
 */
interface Identifiable {
    val id: Identifier
}

/**
 * Registration mode for items.
 * - GLOBAL: Register during mod initialization, cannot be hot-reloaded
 * - RELOADABLE: Register with hot-reload support
 * - AUTO: Choose based on current load state
 */
enum class RegisterMode {
    GLOBAL,
    RELOADABLE,
    AUTO
}

/**
 * Central registry for Katton mod components.
 * Handles registration of custom items with hot-reload support.
 */
object KattonRegistry {

    /**
     * Base class for Katton registries.
     * Provides a map-like interface for storing registered entries.
     */
    abstract class KattonRegistries<T : Identifiable>(
        override val id: Identifier,
        entries: MutableMap<Identifier, T> = mutableMapOf()
    ) : Identifiable, MutableMap<Identifier, T> by entries {

        @Deprecated("Use Function In KattonRegistry Instead")
        internal fun register(implement: T): T {
            this[implement.id] = implement
            return implement
        }

        fun initialize() {}
    }

    /**
     * Represents a registered item entry.
     * Stores the item instance and its properties for component rebuilding.
     */
    data class KattonItemEntry(
        override val id: Identifier,
        val item: Item,
        private val properties: KattonItemProperties? = null
    ) : Identifiable

    /**
     * Registry for Katton items.
     * Supports both global registration and hot-reloadable registration.
     */
    object ITEMS : KattonRegistries<KattonItemEntry>(id(MOD_ID, "item")) {

        private val managedIds = linkedSetOf<Identifier>()
        private val idsByOwner = ConcurrentHashMap<String, MutableSet<Identifier>>()
        private val pendingNativeRegistrations = mutableListOf<Pair<Identifier, () -> Item>>()
        private val hotReloadableItems = ConcurrentHashMap<Identifier, Item>()

        private fun registerGlobalItem(id: Identifier, item: Item): Item =
            Registry.register(BuiltInRegistries.ITEM, id, item)

        private fun currentOwner(): String = Event.currentScriptOwner() ?: "global"

        /**
         * Clears all managed entries. Called at the start of a reload.
         */
        @Synchronized
        fun beginReload() {
            managedIds.forEach { remove(it) }
            managedIds.clear()
            idsByOwner.clear()
            hotReloadableItems.clear()
        }

        /**
         * Ensures an item is registered in the global registry.
         * If the item already exists, updates its components for hot-reload.
         * If not, registers a new item with hot-reload support.
         */
        private fun ensureGlobalItemRegistered(
            id: Identifier,
            itemBuilder: () -> Item,
            properties: KattonItemProperties? = null
        ): Item {
            // Check if already registered in global registry
            val existing = BuiltInRegistries.ITEM.getOptional(id)
            if (existing.isPresent) {
                val item = existing.get()
                properties?.let { updateHolderComponents(item, id, it) }
                return item
            }
            
            // Check hot-reload cache
            hotReloadableItems[id]?.let { return it }

            return registerNewItem(id, itemBuilder, properties)
        }

        /**
         * Updates the components of an existing item's holder.
         * Called during hot-reload to refresh item properties.
         */
        private fun updateHolderComponents(item: Item, id: Identifier, properties: KattonItemProperties) {
            properties.setId(ResourceKey.create(Registries.ITEM, id))
            val holder = item.builtInRegistryHolder
            holder.components = properties.buildComponent()
        }

        /**
         * Registers a new item with hot-reload support.
         * Temporarily unfreezes the registry and injects intrusive holders.
         */
        private fun registerNewItem(
            id: Identifier,
            itemBuilder: () -> Item,
            properties: KattonItemProperties?
        ): Item {
            @Suppress("UNCHECKED_CAST") 
            val itemRegistry = BuiltInRegistries.ITEM as MappedRegistry<Item>
            val accessor = itemRegistry as MappedRegistryAccessor

            // Inject unregisteredIntrusiveHolders if not present
            val previousUnregistered = itemRegistry.unregisteredIntrusiveHolders
            val injectedUnregistered = previousUnregistered == null
            if (injectedUnregistered) {
                itemRegistry.unregisteredIntrusiveHolders = IdentityHashMap()
            }
            
            val savedFrozen = accessor.isFrozen()
            
            return try {
                // Temporarily unfreeze registry
                accessor.setFrozen(false)
                val item = itemBuilder()
                val registered = Registry.register(BuiltInRegistries.ITEM, id, item)
                
                // Manually bind holder components
                bindHolderComponents(item, properties)
                hotReloadableItems[id] = registered
                registered
            } finally {
                // Restore registry state
                if (savedFrozen) accessor.setFrozen(true)
                if (injectedUnregistered) itemRegistry.unregisteredIntrusiveHolders = previousUnregistered
            }
        }

        /**
         * Binds components and tags to an item's holder.
         * Required for items registered after the registry is frozen.
         */
        private fun bindHolderComponents(item: Item, properties: KattonItemProperties?) {
            val holder = item.builtInRegistryHolder
            holder.components = properties?.buildComponent() ?: DataComponentMap.EMPTY
            holder.tags = emptySet()
        }

        /**
         * Registers an item with the specified mode.
         */
        private fun registerItemWithMode(
            id: Identifier,
            registerMode: RegisterMode,
            itemBuilder: () -> Item,
            properties: KattonItemProperties? = null
        ): Item = when (registerMode) {
            RegisterMode.GLOBAL -> registerGlobalItem(id, itemBuilder())
            RegisterMode.RELOADABLE -> ensureGlobalItemRegistered(id, itemBuilder, properties)
            RegisterMode.AUTO -> {
                if (Katton.globalState.after(LoadState.INIT)) {
                    ensureGlobalItemRegistered(id, itemBuilder, properties)
                } else {
                    registerGlobalItem(id, itemBuilder())
                }
            }
        }

        /**
         * Registers a native Item instance with hot-reload support.
         * Use this to register custom Item subclasses from scripts.
         * 
         * @param components The item properties containing id, name, model, etc.
         * @param registerMode The registration mode (GLOBAL, RELOADABLE, or AUTO)
         * @param itemFactory Factory function to create the Item instance
         * @return The registered KattonItemEntry
         */
        fun newNative(
            components: KattonItemProperties,
            registerMode: RegisterMode = RegisterMode.AUTO,
            itemFactory: (KattonItemProperties) -> Item
        ): KattonItemEntry {
            // If called too early, queue for later processing
            if (!Katton.globalState.after(LoadState.INIT)) {
                val placeholder = KattonItemEntry(id = components.id, item = Items.AIR, properties = components)
                @Suppress("DEPRECATION")
                register(placeholder)
                markManaged(components.id, registerMode)
                synchronized(pendingNativeRegistrations) {
                    pendingNativeRegistrations.add(components.id to {
                        components.setId(ResourceKey.create(Registries.ITEM, components.id))
                        itemFactory(components)
                    })
                }
                return placeholder
            }

            // Delayed factory ensures Item is created during unfreeze window
            val delayedFactory = {
                components.setId(ResourceKey.create(Registries.ITEM, components.id))
                itemFactory(components)
            }
            val entry = KattonItemEntry(
                id = components.id,
                item = registerItemWithMode(components.id, registerMode, { delayedFactory() }, components),
                properties = components
            )
            @Suppress("DEPRECATION")
            register(entry)
            markManaged(components.id, registerMode)
            return entry
        }

        /**
         * Marks an item as managed by a script owner.
         * Managed items are cleared during reload.
         */
        private fun markManaged(itemId: Identifier, registerMode: RegisterMode) {
            val owner = currentOwner()
            if (owner != "global" && registerMode != RegisterMode.GLOBAL) {
                managedIds.add(itemId)
                idsByOwner.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(itemId)
            }
        }
    }

    /**
     * Custom data component types for Katton.
     */
    object COMPONENTS {
        lateinit var KATTON_ID: DataComponentType<String>

        fun initialize() {
            if (::KATTON_ID.isInitialized) return
            KATTON_ID = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "id"),
                DataComponentType.builder<String>().persistent(Codec.STRING).build()
            )
        }
    }

    fun initialize() {
        COMPONENTS.initialize()
    }
}
