package top.katton.network

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import top.katton.mixin.MappedRegistryAccessor
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Client-side networking handler for Katton.
 * Handles receiving item sync packets and registering items on client.
 */
object ClientNetworking {
    
    /**
     * Queue of items waiting to be registered.
     * Items are registered before Fabric's registry sync check.
     */
    private val pendingItems: Queue<ItemSyncPacket.ItemData> = ConcurrentLinkedQueue()
    
    /**
     * Stores registered item data for re-applying components after
     * RegistryDataCollector.collectGameRegistries() runs DataComponentInitializers.build(),
     * which overwrites holder.components with values from finalizeInitializer().
     */
    private val registeredItemData: MutableMap<Identifier, ItemSyncPacket.ItemData> = mutableMapOf()
    
    /**
     * Initializes client networking.
     * Registers packet handlers. Payload type is registered in common code.
     */
    fun initialize() {
        // Register packet handler
        ClientConfigurationNetworking.registerGlobalReceiver(ItemSyncPacket.TYPE) { packet, context ->
            if(context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                // Queue items for registration
                pendingItems.addAll(packet.items)
            }
        }
    }
    
    /**
     * Processes all pending item registrations.
     * Called from mixin before Fabric's registry sync check.
     */
    fun processPendingRegistrations() {
        var itemData: ItemSyncPacket.ItemData? = pendingItems.poll()
        while (itemData != null) {
            registerOrUpdateItemOnClient(itemData)
            itemData = pendingItems.poll()
        }
    }
    
    /**
     * Re-applies custom ITEM_NAME and ITEM_MODEL components to all registered Katton items.
     *
     * This must be called after RegistryDataCollector.collectGameRegistries() completes,
     * because that method calls DataComponentInitializers.build() which runs all registered
     * initializers and overwrites holder.components. The finalizeInitializer() in Item's
     * constructor always sets ITEM_NAME to Component.translatable(descriptionId) and
     * ITEM_MODEL to the default model path, overriding any custom values we set earlier.
     *
     * Called from RegistryDataCollectorMixin.
     */
    fun reapplyCustomComponents() {
        for ((id, itemData) in registeredItemData) {
            val item = BuiltInRegistries.ITEM.getOptional(id)
            if (item.isEmpty) continue
            
            val holder = item.get().builtInRegistryHolder
            
            // Re-build the component map, merging with existing components from the initializer
            val existingComponents = if (holder.areComponentsBound()) {
                holder.components()
            } else {
                DataComponentMap.EMPTY
            }
            
            val builder = DataComponentMap.builder()
            
            // Copy all existing components first using keySet + get
            for (componentType in existingComponents.keySet()) {
                @Suppress("UNCHECKED_CAST")
                val typedKey = componentType as DataComponentType<Any>
                val value = existingComponents.get(typedKey)
                if (value != null) {
                    builder.set(typedKey, value)
                }
            }
            
            // Override with our custom values
            itemData.itemName?.let {
                builder.set(DataComponents.ITEM_NAME, it)
            }
            itemData.itemModel?.let {
                builder.set(DataComponents.ITEM_MODEL, it)
            }
            
            holder.components = builder.build()
        }
    }
    
    /**
     * Registers or updates an item on the client.
     *
     * - If the item already exists (e.g., in local world where server and client share JVM),
     *   we skip registration to preserve the full Item instance with custom behavior.
     * - If the item doesn't exist, we register a new item with the received components.
     *
     * @param itemData The item data to register
     */
    private fun registerOrUpdateItemOnClient(itemData: ItemSyncPacket.ItemData) {
        val existingItem = BuiltInRegistries.ITEM.getOptional(itemData.id)
        if (existingItem.isPresent) {
            return
        }
        registerNewItem(itemData)
    }
    
    /**
     * Registers a new item on the client.
     *
     * @param itemData The item data to register
     */
    private fun registerNewItem(itemData: ItemSyncPacket.ItemData) {
        @Suppress("UNCHECKED_CAST")
        val itemRegistry = BuiltInRegistries.ITEM as MappedRegistry<Item>
        val accessor = itemRegistry as MappedRegistryAccessor
        
        // Inject unregisteredIntrusiveHolders if not present
        val previousUnregistered = itemRegistry.unregisteredIntrusiveHolders
        val injectedUnregistered = previousUnregistered == null
        if (injectedUnregistered) {
            itemRegistry.unregisteredIntrusiveHolders = IdentityHashMap()
        }
        
        val savedFrozen = accessor.isFrozen
        
        try {
            // Temporarily unfreeze registry
            accessor.setFrozen(false)

            // Create item properties with ResourceKey set
            val props = Item.Properties()
                .stacksTo(itemData.maxStackSize)
                .setId(ResourceKey.create(Registries.ITEM, itemData.id))
            
            val item = Item(props)
            
            // Register the item
            Registry.register(BuiltInRegistries.ITEM, itemData.id, item)

            // Set holder components and tags
            val holder = item.builtInRegistryHolder
            holder.components = buildComponentMap(itemData)
            holder.tags = emptySet()
            
            // Store item data for re-applying after DataComponentInitializers.build()
            registeredItemData[itemData.id] = itemData
            
        } finally {
            // Restore registry state
            if (savedFrozen) accessor.setFrozen(true)
            if (injectedUnregistered) itemRegistry.unregisteredIntrusiveHolders = previousUnregistered
        }
    }
    
    /**
     * Builds a DataComponentMap from the received item data.
     *
     * @param itemData The item data containing components
     * @return A DataComponentMap with the item's components
     */
    private fun buildComponentMap(itemData: ItemSyncPacket.ItemData): DataComponentMap {
        val builder = DataComponentMap.builder()
        
        // Add item name if present - this controls the display name
        itemData.itemName?.let {
            builder.set(DataComponents.ITEM_NAME, it)
        }
        
        // Add item model if present - this controls the model/texture
        itemData.itemModel?.let {
            builder.set(DataComponents.ITEM_MODEL, it)
        }
        
        return builder.build()
    }
    
    /**
     * Checks if there are pending items to register.
     *
     * @return true if there are pending items
     */
    fun hasPendingItems(): Boolean = pendingItems.isNotEmpty()
    
    /**
     * Checks if there are registered items that need component re-application.
     */
    fun hasRegisteredItems(): Boolean = registeredItemData.isNotEmpty()
    
    /**
     * Resets the pending queue and registered items.
     * Called when disconnecting from server.
     */
    fun reset() {
        pendingItems.clear()
        registeredItemData.clear()
    }
}
