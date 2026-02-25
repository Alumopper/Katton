package top.katton.network

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
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
     * Queue of effects waiting to be registered.
     */
    private val pendingEffects: Queue<EffectSyncPacket.EffectData> = ConcurrentLinkedQueue()

    /**
     * Queue of blocks waiting to be registered.
     */
    private val pendingBlocks: Queue<BlockSyncPacket.BlockData> = ConcurrentLinkedQueue()
    
    /**
     * Stores registered item data for re-applying components after
     * RegistryDataCollector.collectGameRegistries() runs DataComponentInitializers.build(),
     * which overwrites holder.components with values from finalizeInitializer().
     * 
     * This map is NOT cleared on disconnect - items persist in the registry across
     * reconnections, so we need to keep the data to re-apply components each time.
     */
    private val registeredItemData: MutableMap<Identifier, ItemSyncPacket.ItemData> = mutableMapOf()

    /**
     * Stores registered effect data for reconnection scenarios.
     */
    private val registeredEffectData: MutableMap<Identifier, EffectSyncPacket.EffectData> = mutableMapOf()

    /**
     * Stores registered block data for reconnection scenarios.
     */
    private val registeredBlockData: MutableMap<Identifier, BlockSyncPacket.BlockData> = mutableMapOf()
    
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

        ClientConfigurationNetworking.registerGlobalReceiver(EffectSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                // Queue effects for registration
                pendingEffects.addAll(packet.effects)
            }
        }

        ClientConfigurationNetworking.registerGlobalReceiver(BlockSyncPacket.TYPE) { packet, context ->
            if (context.client().isLocalServer) return@registerGlobalReceiver
            context.client().execute {
                // Queue blocks for registration
                pendingBlocks.addAll(packet.blocks)
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

        var effectData: EffectSyncPacket.EffectData? = pendingEffects.poll()
        while (effectData != null) {
            registerOrUpdateEffectOnClient(effectData)
            effectData = pendingEffects.poll()
        }

        var blockData: BlockSyncPacket.BlockData? = pendingBlocks.poll()
        while (blockData != null) {
            registerOrUpdateBlockOnClient(blockData)
            blockData = pendingBlocks.poll()
        }
    }

    /**
     * Registers or updates a block on the client.
     */
    private fun registerOrUpdateBlockOnClient(blockData: BlockSyncPacket.BlockData) {
        registeredBlockData[blockData.id] = blockData

        val existingBlock = BuiltInRegistries.BLOCK.getOptional(blockData.id)
        if (existingBlock.isPresent) {
            return
        }

        registerNewBlock(blockData)
    }

    /**
     * Registers a new block on the client.
     */
    private fun registerNewBlock(blockData: BlockSyncPacket.BlockData) {
        @Suppress("UNCHECKED_CAST")
        val blockRegistry = BuiltInRegistries.BLOCK as MappedRegistry<Block>
        val accessor = blockRegistry as MappedRegistryAccessor

        // Inject unregisteredIntrusiveHolders if not present (same as item registration)
        val previousUnregistered = blockRegistry.unregisteredIntrusiveHolders
        val injectedUnregistered = previousUnregistered == null
        if (injectedUnregistered) {
            blockRegistry.unregisteredIntrusiveHolders = IdentityHashMap()
        }

        val savedFrozen = accessor.isFrozen

        try {
            accessor.setFrozen(false)
            val properties = BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, blockData.id))
                .strength(blockData.destroyTime)
            if (blockData.requiresCorrectTool) {
                properties.requiresCorrectToolForDrops()
            }
            val block = Block(properties)
            Registry.register(BuiltInRegistries.BLOCK, blockData.id, block)
        } finally {
            if (savedFrozen) accessor.setFrozen(true)
            if (injectedUnregistered) blockRegistry.unregisteredIntrusiveHolders = previousUnregistered
        }
    }

    /**
     * Registers or updates an effect on the client.
     */
    private fun registerOrUpdateEffectOnClient(effectData: EffectSyncPacket.EffectData) {
        registeredEffectData[effectData.id] = effectData

        val existingEffect = BuiltInRegistries.MOB_EFFECT.getOptional(effectData.id)
        if (existingEffect.isPresent) {
            return
        }

        registerNewEffect(effectData)
    }

    /**
     * Registers a new effect on the client.
     */
    private fun registerNewEffect(effectData: EffectSyncPacket.EffectData) {
        @Suppress("UNCHECKED_CAST")
        val effectRegistry = BuiltInRegistries.MOB_EFFECT as MappedRegistry<MobEffect>
        val accessor = effectRegistry as MappedRegistryAccessor
        val savedFrozen = accessor.isFrozen

        try {
            accessor.setFrozen(false)
            val effect = object : MobEffect(effectData.category, effectData.color) {}
            Registry.register(BuiltInRegistries.MOB_EFFECT, effectData.id, effect)
        } finally {
            if (savedFrozen) accessor.setFrozen(true)
        }
    }
    
    /**
     * Re-applies custom components to all registered Katton items.
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
            
            // Decode the full DataComponentMap from NBT
            val components = itemData.decodeComponents(NbtOps.INSTANCE)
            if (components != DataComponentMap.EMPTY) {
                holder.components = components
            }
        }
    }
    
    /**
     * Registers or updates an item on the client.
     * 
     * - If the item already exists (e.g., in local world or from previous connection),
     *   we still update registeredItemData so reapplyCustomComponents works.
     * - If the item doesn't exist, we register a new item with the received components.
     * 
     * @param itemData The item data to register
     */
    private fun registerOrUpdateItemOnClient(itemData: ItemSyncPacket.ItemData) {
        // Always update the stored data (needed for reconnection scenarios)
        registeredItemData[itemData.id] = itemData
        
        val existingItem = BuiltInRegistries.ITEM.getOptional(itemData.id)
        if (existingItem.isPresent) {
            // Item already exists - just update its components
            val holder = existingItem.get().builtInRegistryHolder
            val components = itemData.decodeComponents(NbtOps.INSTANCE)
            if (components != DataComponentMap.EMPTY) {
                holder.components = components
            }
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
                .setId(ResourceKey.create(Registries.ITEM, itemData.id))
            
            val item = Item(props)
            
            // Register the item
            Registry.register(BuiltInRegistries.ITEM, itemData.id, item)

            // Set holder components from the full DataComponentMap
            val holder = item.builtInRegistryHolder
            val components = itemData.decodeComponents(NbtOps.INSTANCE)
            if (components != DataComponentMap.EMPTY) {
                holder.components = components
            }
            holder.tags = emptySet()
            
        } finally {
            // Restore registry state
            if (savedFrozen) accessor.setFrozen(true)
            if (injectedUnregistered) itemRegistry.unregisteredIntrusiveHolders = previousUnregistered
        }
    }
    
    /**
     * Checks if there are pending items to register.
     * 
     * @return true if there are pending items
     */
    fun hasPendingItems(): Boolean = pendingItems.isNotEmpty()

    /**
     * Checks if there are pending effects to register.
     */
    fun hasPendingEffects(): Boolean = pendingEffects.isNotEmpty()

    /**
     * Checks if there are pending blocks to register.
     */
    fun hasPendingBlocks(): Boolean = pendingBlocks.isNotEmpty()
    
    /**
     * Checks if there are registered items that need component re-application.
     */
    fun hasRegisteredItems(): Boolean = registeredItemData.isNotEmpty()

    /**
     * Checks if there are registered effects.
     */
    fun hasRegisteredEffects(): Boolean = registeredEffectData.isNotEmpty()

    /**
     * Checks if there are registered blocks.
     */
    fun hasRegisteredBlocks(): Boolean = registeredBlockData.isNotEmpty()
    
    /**
     * Resets the pending queue.
     * Note: registeredItemData is NOT cleared because items persist in the registry
     * across reconnections and we need the data for reapplyCustomComponents().
     * Called when disconnecting from server.
     */
    fun reset() {
        pendingItems.clear()
        pendingEffects.clear()
        pendingBlocks.clear()
        // Do NOT clear registeredItemData - items stay in registry across reconnections
    }
}
