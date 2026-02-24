package top.katton.registry

import com.mojang.logging.LogUtils
import com.mojang.serialization.Codec
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
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
        val agentItem: KattonItemInterface? = null,
        private val properties: KattonItemProperties? = null
    ) : Identifiable {
        fun getDefaultInstance(): ItemStack {
            // 使用 Holder.direct(item) 构造 ItemStack，避免 "Components not bound yet" 错误
            // 这个错误发生在 Item 的 Holder 还未绑定 components 时访问 item.defaultInstance
            return try {
                val stack = ItemStack(Holder.direct(item), 1)
                // 优先使用 agentItem 的 components
                agentItem?.let { stack.apply { applyComponents(it.components()) } }
                // 如果没有 agentItem 但有 properties，使用 properties 的 components
                    ?: properties?.let { props ->
                        stack.apply { 
                            val components = props.buildComponent()
                            applyComponents(components)
                        }
                    }
                stack
            } catch (_: Throwable) {
                ItemStack(Items.AIR, 1)
            }
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
            hotReloadableItems.clear()
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

        // 存储热重载注册的物品
        private val hotReloadableItems = ConcurrentHashMap<Identifier, Item>()
        
        private fun ensureGlobalItemRegistered(
            id: Identifier,
            itemBuilder: () -> Item,
            properties: KattonItemProperties? = null
        ): Item {
            // 首先检查全局注册表
            val existing = BuiltInRegistries.ITEM.getOptional(id)
            if (existing.isPresent) {
                return existing.get()
            }
            
            // 检查热重载物品缓存
            hotReloadableItems[id]?.let { return it }

            val itemRegistry = BuiltInRegistries.ITEM as MappedRegistry<Item>
            val accessor = itemRegistry as MappedRegistryAccessor

            // 查找 unregisteredIntrusiveHolders 字段
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
                        } catch (_: Throwable) {}
                    }
                    if (unregisteredField != null) break
                    cls = cls.superclass
                }
            }
            
            val previousUnregistered = try { unregisteredField?.get(itemRegistry) } catch (_: Throwable) { null }
            var injectedUnregistered = false
            
            // 保存 frozen 状态
            val savedFrozen = try { accessor.isFrozen() } catch (_: Throwable) { true }
            
            return try {
                // 1. 注入 unregisteredIntrusiveHolders（如果需要）
                if (previousUnregistered == null && unregisteredField != null) {
                    try {
                        unregisteredField!!.set(itemRegistry, java.util.IdentityHashMap<Any, Any>())
                        injectedUnregistered = true
                    } catch (_: Throwable) {}
                }
                
                // 2. 解冻注册表
                try { accessor.setFrozen(false) } catch (_: Throwable) {}
                
                // 3. 创建物品
                val item = itemBuilder()
                
                // 4. 注册到全局注册表
                val registered = Registry.register(BuiltInRegistries.ITEM, id, item)
                
                // 5. 手动绑定 Holder 的 components 和 tags
                try {
                    val holderField = Item::class.java.getDeclaredField("builtInRegistryHolder")
                    holderField.isAccessible = true
                    val holder = holderField.get(item) as? Holder.Reference<Item>
                    if (holder != null) {
                        // 绑定 components
                        val componentsField = Holder.Reference::class.java.getDeclaredField("components")
                        componentsField.isAccessible = true
                        
                        // 直接使用传入的 properties 构建组件
                        val itemComponents = if (properties != null) {
                            val comps = properties.buildComponent()
                            LOGGER.info("Got components from properties for $id: ${comps.keySet()}")
                            comps
                        } else {
                            LOGGER.warn("No properties provided for $id, using EMPTY components")
                            DataComponentMap.EMPTY
                        }
                        componentsField.set(holder, itemComponents)
                        LOGGER.info("Set components for $id holder: ${itemComponents.keySet()}")
                        
                        // 绑定 tags
                        val tagsField = Holder.Reference::class.java.getDeclaredField("tags")
                        tagsField.isAccessible = true
                        tagsField.set(holder, java.util.Collections.emptySet<TagKey<Item>>())
                    }
                } catch (e: Throwable) {
                    LOGGER.warn("Failed to bind holder for $id: ${e.message}")
                }
                
                // 6. 缓存物品
                hotReloadableItems[id] = registered
                LOGGER.info("Registered hot-reloadable item: $id")
                registered
            } finally {
                // 7. 恢复 frozen 状态（不调用 freeze()）
                if (savedFrozen) {
                    try { accessor.setFrozen(true) } catch (_: Throwable) {}
                }
                
                // 8. 恢复 unregisteredIntrusiveHolders
                if (injectedUnregistered && unregisteredField != null) {
                    try { unregisteredField.set(itemRegistry, previousUnregistered) } catch (_: Throwable) {}
                }
            }
        }

        private fun registerItemWithMode(
            id: Identifier,
            registerMode: RegisterMode,
            itemBuilder: () -> Item,
            properties: KattonItemProperties? = null
        ): Item = when (registerMode) {
            RegisterMode.GLOBAL -> {
                registerGlobalItem(id, itemBuilder())
            }

            RegisterMode.RELOADABLE -> {
                ensureGlobalItemRegistered(id, itemBuilder, properties)
            }

            RegisterMode.AUTO -> {
                Katton.globalState.let {
                    if (it.after(LoadState.INIT)) {
                        ensureGlobalItemRegistered(id, itemBuilder, properties)
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
                    item = registerItemWithMode(components.id, registerMode, { delayedFactory() }, components),
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
                val placeholder = KattonItemEntry(id = components.id, item = Items.AIR, agentItem = null, properties = components)
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
                    item = registerItemWithMode(components.id, registerMode, { delayedFactory() }, components),
                    agentItem = null,
                    properties = components
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
        // Eagerly hold the reference, but registration happens in initialize()
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
