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
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import top.katton.Katton
import top.katton.Katton.MOD_ID
import top.katton.LoadState
import top.katton.mixin.BlockAccessor
import top.katton.mixin.MappedRegistryAccessor
import top.katton.util.Event
import java.util.*
import java.util.Collections.emptySet
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates an [Identifier] from a namespace and path.
 * 
 * This is a convenience function for creating Minecraft resource identifiers.
 * 
 * @param namespace The namespace (mod id) for the identifier
 * @param path The path component of the identifier
 * @return A new [Identifier] instance
 */
fun id(namespace: String, path: String) = Identifier.fromNamespaceAndPath(namespace, path)

/**
 * Parses an [Identifier] from a string in the format "namespace:path".
 * 
 * @param string The string to parse
 * @return The parsed [Identifier]
 * @throws IllegalArgumentException if the string is not a valid identifier format
 */
fun id(string: String) = Identifier.parse(string)

/**
 * Interface for objects that have an [Identifier].
 * 
 * This interface provides a common contract for objects that can be identified
 * by a Minecraft resource identifier. It is used throughout the registry system
 * to track and manage registered objects.
 */
interface Identifiable {
    /**
     * The unique identifier for this object.
     */
    val id: Identifier
}

/**
 * Registration mode for game objects.
 * 
 * This enum defines how objects should be registered with Minecraft's registries,
 * particularly in relation to the hot-reload system.
 * 
 * - **GLOBAL**: Register during mod initialization, cannot be hot-reloaded.
 *   Use this for objects that need to persist across reloads or are required
 *   for the mod to function properly.
 *   
 * - **RELOADABLE**: Register with hot-reload support. Objects registered this
 *   way can be redefined during script reloads. Use this for content that
 *   may change during development or configuration.
 *   
 * - **AUTO**: Automatically choose based on current load state. During initial
 *   mod loading, behaves like GLOBAL. After initialization, behaves like
 *   RELOADABLE. This is the recommended mode for most use cases.
 */
enum class RegisterMode {
    GLOBAL,
    RELOADABLE,
    AUTO
}

/**
 * Central registry for Katton mod components.
 * 
 * This object provides a unified interface for registering custom game objects
 * such as items, blocks, and mob effects. It supports both traditional global
 * registration and hot-reloadable registration for script-defined content.
 * 
 * The registry uses a custom approach to handle registration after Minecraft's
 * registries are frozen, temporarily unfreeze registries
 * and inject intrusive holders as needed.
 */
object KattonRegistry {

    //这个热重载怎么这么难写啊QwQ
    //感觉会有一堆bug但是现在能运行那就先不管了吧qwq

    /**
     * Base class for Katton registries.
     * 
     * Provides a map-like interface for storing registered entries, allowing
     * lookup by identifier. Each registry has its own unique identifier for
     * debugging and logging purposes.
     * 
     * @param T The type of entries stored in this registry, must implement [Identifiable]
     * @property id The identifier for this registry instance
     * @param entries The initial entries map, defaults to an empty mutable map
     */
    abstract class KattonRegistries<T : Identifiable>(
        override val id: Identifier,
        entries: MutableMap<Identifier, T> = mutableMapOf()
    ) : Identifiable, MutableMap<Identifier, T> by entries {

        /**
         * Registers an entry in this registry.
         * 
         * @param implement The entry to register
         * @return The registered entry
         * @deprecated Use the specific registration methods in ITEMS, BLOCKS, or EFFECTS instead
         */
        @Deprecated("Use Function In KattonRegistry Instead")
        internal fun register(implement: T): T {
            this[implement.id] = implement
            return implement
        }

        /**
         * Initializes this registry. Override to perform initialization logic.
         */
        @Suppress("unused")
        fun initialize() {}
    }

    /**
     * Represents a registered item entry.
     * 
     * Stores the item instance along with its properties for potential
     * component rebuilding during hot-reload operations.
     * 
     * @property id The unique identifier for this item
     * @property item The registered Item instance
     * @property properties The properties used to create this item, may be null for non-reloadable items
     */
    data class KattonItemEntry(
        override val id: Identifier,
        val item: Item,
        private val properties: KattonItemProperties? = null
    ) : Identifiable

    /**
     * Represents a registered mob effect entry.
     * 
     * @property id The unique identifier for this effect
     * @property effect The registered MobEffect instance
     */
    data class KattonMobEffectEntry(
        override val id: Identifier,
        val effect: MobEffect
    ) : Identifiable

    /**
     * Represents a registered block entry.
     * 
     * @property id The unique identifier for this block
     * @property block The registered Block instance
     */
    data class KattonBlockEntry(
        override val id: Identifier,
        val block: Block
    ) : Identifiable

    /**
     * Registry for Katton items.
     * 
     * Supports both global registration (during mod initialization) and
     * hot-reloadable registration (after the server has started). Items
     * registered through this registry are tracked by their script owner
     * and can be cleared during reload operations.
     * 
     * The registry handles the complexity of registering items after
     * Minecraft's item registry is frozen by:
     * 1. Temporarily unfreezing the registry
     * 2. Injecting intrusive holders if needed
     * 3. Registering the item
     * 4. Restoring the registry state
     */
    object ITEMS : KattonRegistries<KattonItemEntry>(id(MOD_ID, "item")) {

        private val managedIds = linkedSetOf<Identifier>()
        private val idsByOwner = ConcurrentHashMap<String, MutableSet<Identifier>>()
        private val pendingNativeRegistrations = mutableListOf<Pair<Identifier, () -> Item>>()
        private val hotReloadableItems = ConcurrentHashMap<Identifier, Item>()

        /**
         * Registers an item in the global Minecraft registry.
         * 
         * @param id The identifier for the item
         * @param item The item to register
         * @return The registered item
         */
        private fun registerGlobalItem(id: Identifier, item: Item): Item =
            Registry.register(BuiltInRegistries.ITEM, id, item)

        /**
         * Gets the current script owner for tracking purposes.
         * 
         * @return The current script owner name, or "global" if not in a script context
         */
        private fun currentOwner(): String = Event.currentScriptOwner() ?: "global"

        /**
         * Clears all managed entries. Called at the start of a reload.
         * 
         * This method removes all script-defined items from tracking but does
         * not remove them from the global Minecraft registry. The items remain
         * registered but their entries in this registry are cleared.
         */
        @Synchronized
        fun beginReload() {
            managedIds.forEach { remove(it) }
            managedIds.clear()
            idsByOwner.clear()
            hotReloadableItems.clear()
        }

        /**
         * Ensures an item is registered in the global registry with hot-reload support.
         * 
         * If the item already exists in the global registry, updates its components
         * for hot-reload. If not, registers a new item with the necessary machinery
         * for future hot-reloads.
         * 
         * @param id The identifier for the item
         * @param itemBuilder Factory function to create the item
         * @param properties The item properties, used for component updates
         * @return The registered or existing item
         */
        private fun ensureGlobalItemRegistered(
            id: Identifier,
            itemBuilder: () -> Item,
            properties: KattonItemProperties? = null
        ): Item {
            val existing = BuiltInRegistries.ITEM.getOptional(id)
            if (existing.isPresent) {
                val item = existing.get()
                properties?.let { updateHolderComponents(item, id, it) }
                return item
            }
            
            hotReloadableItems[id]?.let { return it }

            return registerNewItem(id, itemBuilder, properties)
        }

        /**
         * Updates the components of an existing item's holder.
         * 
         * Called during hot-reload to refresh item properties without
         * creating a new item instance.
         * 
         * @param item The item to update
         * @param id The item's identifier
         * @param properties The new properties to apply
         */
        private fun updateHolderComponents(item: Item, id: Identifier, properties: KattonItemProperties) {
            properties.setId(ResourceKey.create(Registries.ITEM, id))
            val holder = item.builtInRegistryHolder
            holder.components = properties.buildComponent()
        }

        /**
         * Registers a new item with hot-reload support.
         * 
         * This method handles the complex process of registering an item after
         * the Minecraft registry is frozen by temporarily unfreezing it and
         * injecting intrusive holders.
         * 
         * @param id The identifier for the item
         * @param itemBuilder Factory function to create the item
         * @param properties The item properties
         * @return The newly registered item
         */
        private fun registerNewItem(
            id: Identifier,
            itemBuilder: () -> Item,
            properties: KattonItemProperties?
        ): Item {
            @Suppress("UNCHECKED_CAST") 
            val itemRegistry = BuiltInRegistries.ITEM as MappedRegistry<Item>
            val accessor = itemRegistry as MappedRegistryAccessor

            val previousUnregistered = itemRegistry.unregisteredIntrusiveHolders
            val injectedUnregistered = previousUnregistered == null
            if (injectedUnregistered) {
                itemRegistry.unregisteredIntrusiveHolders = IdentityHashMap()
            }
            
            val savedFrozen = accessor.isFrozen
            
            return try {
                accessor.setFrozen(false)
                val item = itemBuilder()
                val registered = Registry.register(BuiltInRegistries.ITEM, id, item)
                
                bindHolderComponents(item, properties)
                hotReloadableItems[id] = registered
                registered
            } finally {
                if (savedFrozen) accessor.setFrozen(true)
                if (injectedUnregistered) itemRegistry.unregisteredIntrusiveHolders = previousUnregistered
            }
        }

        /**
         * Binds components and tags to an item's holder.
         * 
         * Required for items registered after the registry is frozen,
         * as the normal binding process may not have occurred.
         * 
         * @param item The item to bind components to
         * @param properties The properties containing component data
         */
        private fun bindHolderComponents(item: Item, properties: KattonItemProperties?) {
            val holder = item.builtInRegistryHolder
            holder.components = properties?.buildComponent() ?: DataComponentMap.EMPTY
            holder.tags = emptySet()
        }

        /**
         * Registers an item with the specified mode.
         * 
         * @param id The identifier for the item
         * @param registerMode The registration mode
         * @param itemBuilder Factory function to create the item
         * @param properties The item properties
         * @return The registered item
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
         * 
         * Use this method to register custom Item subclasses from scripts.
         * The method handles all the complexity of late registration and
         * hot-reload support automatically.
         * 
         * If called before the mod is fully initialized, the registration
         * will be queued and processed later.
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
            if (!Katton.globalState.after(LoadState.INIT)) {
                val placeholder = KattonItemEntry(id = components.id, item = Items.AIR, properties = components)
                @Suppress("DEPRECATION")
                register(placeholder)
                markManaged(components.id, registerMode)
                synchronized(pendingNativeRegistrations) {
                    pendingNativeRegistrations.add(components.id to {
                        components.setId(ResourceKey.create(Registries.ITEM, components.id))
                        components.finalizeComponentInitializer()
                        itemFactory(components)
                    })
                }
                return placeholder
            }

            val delayedFactory = {
                components.setId(ResourceKey.create(Registries.ITEM, components.id))
                components.finalizeComponentInitializer()
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
         * 
         * Managed items are tracked for cleanup during reload operations.
         * 
         * @param itemId The identifier of the item to mark
         * @param registerMode The registration mode
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
     * Registry for Katton mob effects.
     */
    object EFFECTS : KattonRegistries<KattonMobEffectEntry>(id(MOD_ID, "effect")) {

        private val managedIds = linkedSetOf<Identifier>()
        private val idsByOwner = ConcurrentHashMap<String, MutableSet<Identifier>>()
        private val hotReloadableEffects = ConcurrentHashMap<Identifier, MobEffect>()

        /**
         * Registers a mob effect in the global Minecraft registry.
         */
        private fun registerGlobalEffect(id: Identifier, effect: MobEffect): MobEffect =
            Registry.register(BuiltInRegistries.MOB_EFFECT, id, effect)

        /**
         * Gets the current script owner for tracking purposes.
         */
        private fun currentOwner(): String = Event.currentScriptOwner() ?: "global"

        /**
         * Clears all managed entries. Called at the start of a reload.
         */
        @Synchronized
        fun beginReload() {
            managedIds.forEach { remove(it) }
            managedIds.clear()
            idsByOwner.clear()
            hotReloadableEffects.clear()
        }

        /**
         * Ensures a mob effect is registered in the global registry with hot-reload support.
         */
        private fun ensureGlobalEffectRegistered(
            id: Identifier,
            effectBuilder: () -> MobEffect
        ): MobEffect {
            val existing = BuiltInRegistries.MOB_EFFECT.getOptional(id)
            if (existing.isPresent) {
                return existing.get()
            }

            hotReloadableEffects[id]?.let { return it }

            return registerNewEffect(id, effectBuilder)
        }

        /**
         * Registers a new mob effect with hot-reload support.
         */
        private fun registerNewEffect(
            id: Identifier,
            effectBuilder: () -> MobEffect
        ): MobEffect {
            @Suppress("UNCHECKED_CAST")
            val effectRegistry = BuiltInRegistries.MOB_EFFECT as MappedRegistry<MobEffect>
            val accessor = effectRegistry as MappedRegistryAccessor
            val savedFrozen = accessor.isFrozen

            return try {
                accessor.setFrozen(false)
                val effect = effectBuilder()
                val registered = Registry.register(BuiltInRegistries.MOB_EFFECT, id, effect)
                hotReloadableEffects[id] = registered
                registered
            } finally {
                if (savedFrozen) accessor.setFrozen(true)
            }
        }

        /**
         * Registers a mob effect with the specified mode.
         */
        private fun registerEffectWithMode(
            id: Identifier,
            registerMode: RegisterMode,
            effectBuilder: () -> MobEffect
        ): MobEffect = when (registerMode) {
            RegisterMode.GLOBAL -> registerGlobalEffect(id, effectBuilder())
            RegisterMode.RELOADABLE -> ensureGlobalEffectRegistered(id, effectBuilder)
            RegisterMode.AUTO -> {
                if (Katton.globalState.after(LoadState.INIT)) {
                    ensureGlobalEffectRegistered(id, effectBuilder)
                } else {
                    registerGlobalEffect(id, effectBuilder())
                }
            }
        }

        /**
         * Registers a native MobEffect instance with hot-reload support.
         * 
         * @param id The identifier for the effect
         * @param registerMode The registration mode
         * @param effectFactory Factory function to create the MobEffect instance
         * @return The registered KattonMobEffectEntry
         */
        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            effectFactory: () -> MobEffect
        ): KattonMobEffectEntry {
            val entry = KattonMobEffectEntry(
                id = id,
                effect = registerEffectWithMode(id, registerMode, effectFactory)
            )
            @Suppress("DEPRECATION")
            register(entry)
            markManaged(id, registerMode)
            return entry
        }

        /**
         * Marks a mob effect as managed by a script owner.
         */
        private fun markManaged(effectId: Identifier, registerMode: RegisterMode) {
            val owner = currentOwner()
            if (owner != "global" && registerMode != RegisterMode.GLOBAL) {
                managedIds.add(effectId)
                idsByOwner.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(effectId)
            }
        }
    }

    /**
     * Registry for Katton blocks.
     */
    object BLOCKS : KattonRegistries<KattonBlockEntry>(id(MOD_ID, "block")) {

        private val managedIds = linkedSetOf<Identifier>()
        private val idsByOwner = ConcurrentHashMap<String, MutableSet<Identifier>>()
        private val hotReloadableBlocks = ConcurrentHashMap<Identifier, Block>()

        /**
         * Registers a block in the global Minecraft registry.
         */
        private fun registerGlobalBlock(id: Identifier, block: Block): Block =
            Registry.register(BuiltInRegistries.BLOCK, id, block)

        /**
         * Gets the current script owner for tracking purposes.
         */
        private fun currentOwner(): String = Event.currentScriptOwner() ?: "global"

        /**
         * Clears all managed entries. Called at the start of a reload.
         */
        @Synchronized
        fun beginReload() {
            managedIds.forEach { remove(it) }
            managedIds.clear()
            idsByOwner.clear()
            hotReloadableBlocks.clear()
        }

        /**
         * Ensures a block is registered in the global registry with hot-reload support.
         */
        private fun ensureGlobalBlockRegistered(
            id: Identifier,
            blockBuilder: (BlockBehaviour.Properties) -> Block
        ): Block {
            val existing = BuiltInRegistries.BLOCK.getOptional(id)
            if (existing.isPresent) {
                val block = existing.get()
                (block as BlockAccessor).`katton$getBuiltInRegistryHolder`().tags = emptySet()
                return block
            }

            hotReloadableBlocks[id]?.let { return it }

            return registerNewBlock(id, blockBuilder)
        }

        /**
         * Registers a new block with hot-reload support.
         */
        private fun registerNewBlock(
            id: Identifier,
            blockBuilder: (BlockBehaviour.Properties) -> Block
        ): Block {
            @Suppress("UNCHECKED_CAST")
            val blockRegistry = BuiltInRegistries.BLOCK as MappedRegistry<Block>
            val accessor = blockRegistry as MappedRegistryAccessor

            val previousUnregistered = blockRegistry.unregisteredIntrusiveHolders
            val injectedUnregistered = previousUnregistered == null
            if (injectedUnregistered) {
                blockRegistry.unregisteredIntrusiveHolders = IdentityHashMap()
            }

            val savedFrozen = accessor.isFrozen

            return try {
                accessor.setFrozen(false)
                val props = BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))
                val block = blockBuilder(props)
                val registered = Registry.register(BuiltInRegistries.BLOCK, id, block)
                (registered as BlockAccessor).`katton$getBuiltInRegistryHolder`().tags = emptySet()
                hotReloadableBlocks[id] = registered
                registered
            } finally {
                if (savedFrozen) accessor.setFrozen(true)
                if (injectedUnregistered) blockRegistry.unregisteredIntrusiveHolders = previousUnregistered
            }
        }

        /**
         * Registers a block with the specified mode.
         */
        private fun registerBlockWithMode(
            id: Identifier,
            registerMode: RegisterMode,
            blockBuilder: (BlockBehaviour.Properties) -> Block
        ): Block = when (registerMode) {
            RegisterMode.GLOBAL -> {
                val props = BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))
                registerGlobalBlock(id, blockBuilder(props))
            }
            RegisterMode.RELOADABLE -> ensureGlobalBlockRegistered(id, blockBuilder)
            RegisterMode.AUTO -> {
                if (Katton.globalState.after(LoadState.INIT)) {
                    ensureGlobalBlockRegistered(id, blockBuilder)
                } else {
                    val props = BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))
                    registerGlobalBlock(id, blockBuilder(props))
                }
            }
        }

        /**
         * Registers a native Block instance with hot-reload support.
         * 
         * @param id The identifier for the block
         * @param registerMode The registration mode
         * @param blockFactory Factory function to create the Block instance
         * @return The registered KattonBlockEntry
         */
        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            blockFactory: (BlockBehaviour.Properties) -> Block
        ): KattonBlockEntry {
            val entry = KattonBlockEntry(
                id = id,
                block = registerBlockWithMode(id, registerMode, blockFactory)
            )
            @Suppress("DEPRECATION")
            register(entry)
            markManaged(id, registerMode)
            return entry
        }

        /**
         * Marks a block as managed by a script owner.
         */
        private fun markManaged(blockId: Identifier, registerMode: RegisterMode) {
            val owner = currentOwner()
            if (owner != "global" && registerMode != RegisterMode.GLOBAL) {
                managedIds.add(blockId)
                idsByOwner.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(blockId)
            }
        }
    }

    /**
     * Custom data component types for Katton.
     * 
     * This object defines and registers custom data component types that
     * can be attached to items for storing mod-specific data.
     */
    object COMPONENTS {
        /**
         * A data component type for storing Katton-specific string identifiers.
         */
        lateinit var KATTON_ID: DataComponentType<String>

        /**
         * Initializes and registers the custom data component types.
         * 
         * This method is idempotent and can be called multiple times safely.
         */
        fun initialize() {
            if (::KATTON_ID.isInitialized) return
            KATTON_ID = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "id"),
                DataComponentType.builder<String>().persistent(Codec.STRING).build()
            )
        }
    }

    /**
     * Initializes all Katton registries.
     * 
     * This method should be called during mod initialization to ensure
     * all custom data component types are registered.
     */
    fun initialize() {
        COMPONENTS.initialize()
    }
}
