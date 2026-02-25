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

    data class KattonItemEntry(
        override val id: Identifier,
        val item: Item,
        private val properties: KattonItemProperties? = null
    ) : Identifiable

    object ITEMS : KattonRegistries<KattonItemEntry>(id(MOD_ID, "item")) {

        private val managedIds = linkedSetOf<Identifier>()
        private val idsByOwner = ConcurrentHashMap<String, MutableSet<Identifier>>()
        private val pendingNativeRegistrations = mutableListOf<Pair<Identifier, () -> Item>>()
        private val hotReloadableItems = ConcurrentHashMap<Identifier, Item>()

        private fun registerGlobalItem(id: Identifier, item: Item): Item =
            Registry.register(BuiltInRegistries.ITEM, id, item)

        private fun currentOwner(): String = Event.currentScriptOwner() ?: "global"

        @Synchronized
        fun beginReload() {
            managedIds.forEach { remove(it) }
            managedIds.clear()
            idsByOwner.clear()
            hotReloadableItems.clear()
        }

        private fun ensureGlobalItemRegistered(
            id: Identifier,
            itemBuilder: () -> Item,
            properties: KattonItemProperties? = null
        ): Item {
            // 检查全局注册表
            val existing = BuiltInRegistries.ITEM.getOptional(id)
            if (existing.isPresent) {
                val item = existing.get()
                properties?.let { updateHolderComponents(item, id, it) }
                return item
            }
            
            // 检查热重载缓存
            hotReloadableItems[id]?.let { return it }

            return registerNewItem(id, itemBuilder, properties)
        }

        private fun updateHolderComponents(item: Item, id: Identifier, properties: KattonItemProperties) {
            properties.setId(ResourceKey.create(Registries.ITEM, id))
            val holder = item.builtInRegistryHolder()
            holder.components = properties.buildComponent()
        }

        private fun registerNewItem(
            id: Identifier,
            itemBuilder: () -> Item,
            properties: KattonItemProperties?
        ): Item {
            @Suppress("UNCHECKED_CAST") val itemRegistry = BuiltInRegistries.ITEM as MappedRegistry<Item>
            val accessor = itemRegistry as MappedRegistryAccessor

            val previousUnregistered = itemRegistry.unregisteredIntrusiveHolders

            val injectedUnregistered = previousUnregistered == null
            if (injectedUnregistered) {
                itemRegistry.unregisteredIntrusiveHolders = IdentityHashMap()
            }
            
            val savedFrozen = accessor.isFrozen()
            
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

        private fun bindHolderComponents(item: Item, properties: KattonItemProperties?) {
            val holder = item.builtInRegistryHolder()
            holder.components = properties?.buildComponent() ?: DataComponentMap.EMPTY
            holder.tags = emptySet()
        }

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
                        itemFactory(components)
                    })
                }
                return placeholder
            }

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

        private fun markManaged(itemId: Identifier, registerMode: RegisterMode) {
            val owner = currentOwner()
            if (owner != "global" && registerMode != RegisterMode.GLOBAL) {
                managedIds.add(itemId)
                idsByOwner.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(itemId)
            }
        }
    }

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
