package top.katton.network

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
import top.katton.platform.DynamicRegistryHooks
import top.katton.registry.RegistryMutationUtil
import top.katton.util.ReflectUtil
import java.util.*

/**
 * Client-side networking handler for Katton.
 * Handles receiving item sync packets and registering items on client.
 */
abstract class ClientNetworking {

    private fun isRegistryFrozen(registry: MappedRegistry<*>): Boolean {
        return ReflectUtil.getT<Boolean>(registry, "frozen").getOrNull() ?: true
    }

    private fun setRegistryFrozen(registry: MappedRegistry<*>, frozen: Boolean) {
        ReflectUtil.set(registry, "frozen", frozen)
    }

    /**
     * Queue of items waiting to be registered.
     * Items are registered before Fabric's registry sync check.
     */
    @Volatile
    private var pendingItemSnapshot: List<ItemSyncPacket.ItemData>? = null

    /**
     * Queue of effects waiting to be registered.
     */
    @Volatile
    private var pendingEffectSnapshot: List<EffectSyncPacket.EffectData>? = null

    /**
     * Queue of blocks waiting to be registered.
     */
    @Volatile
    private var pendingBlockSnapshot: List<BlockSyncPacket.BlockData>? = null

    /**
     * Stores registered item data for re-applying components after
     * RegistryDataCollector.collectGameRegistries() runs DataComponentInitializers.build(),
     * which overwrites holder.components with values from finalizeInitializer().
     */
    protected val registeredItemData: MutableMap<Identifier, ItemSyncPacket.ItemData> = mutableMapOf()

    /**
     * Stores registered effect data for reconnection scenarios.
     */
    protected val registeredEffectData: MutableMap<Identifier, EffectSyncPacket.EffectData> = mutableMapOf()

    /**
     * Stores registered block data for reconnection scenarios.
     */
    protected val registeredBlockData: MutableMap<Identifier, BlockSyncPacket.BlockData> = mutableMapOf()

    fun queueItemSnapshot(items: List<ItemSyncPacket.ItemData>) {
        pendingItemSnapshot = items
    }

    fun queueEffectSnapshot(effects: List<EffectSyncPacket.EffectData>) {
        pendingEffectSnapshot = effects
    }

    fun queueBlockSnapshot(blocks: List<BlockSyncPacket.BlockData>) {
        pendingBlockSnapshot = blocks
    }

    /**
     * Processes all pending item registrations.
     * Called from mixin before Fabric's registry sync check.
     */
    fun processPendingRegistrations() {
        pendingItemSnapshot?.let {
            pendingItemSnapshot = null
            applyItemSnapshot(it)
        }

        pendingEffectSnapshot?.let {
            pendingEffectSnapshot = null
            applyEffectSnapshot(it)
        }

        pendingBlockSnapshot?.let {
            pendingBlockSnapshot = null
            applyBlockSnapshot(it)
        }
    }

    fun applyItemSnapshot(items: List<ItemSyncPacket.ItemData>) {
        val incomingIds = items.asSequence().map { it.id }.toSet()
        val removedIds = registeredItemData.keys.filter { it !in incomingIds }
        removedIds.forEach(::unregisterItemOnClient)
        items.forEach(::registerOrUpdateItemOnClient)
        registeredItemData.keys.retainAll(incomingIds)
    }

    fun applyEffectSnapshot(effects: List<EffectSyncPacket.EffectData>) {
        val incomingIds = effects.asSequence().map { it.id }.toSet()
        val removedIds = registeredEffectData.keys.filter { it !in incomingIds }
        removedIds.forEach(::unregisterEffectOnClient)
        effects.forEach(::registerOrUpdateEffectOnClient)
        registeredEffectData.keys.retainAll(incomingIds)
    }

    fun applyBlockSnapshot(blocks: List<BlockSyncPacket.BlockData>) {
        val incomingIds = blocks.asSequence().map { it.id }.toSet()
        val removedIds = registeredBlockData.keys.filter { it !in incomingIds }
        removedIds.forEach(::unregisterBlockOnClient)
        blocks.forEach(::registerOrUpdateBlockOnClient)
        registeredBlockData.keys.retainAll(incomingIds)
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
    protected fun registerNewBlock(blockData: BlockSyncPacket.BlockData) {
        @Suppress("UNCHECKED_CAST")
        val blockRegistry = BuiltInRegistries.BLOCK as MappedRegistry<Block>

        // Inject unregisteredIntrusiveHolders if not present (same as item registration)
        val previousUnregistered = blockRegistry.unregisteredIntrusiveHolders
        val injectedUnregistered = previousUnregistered == null
        if (injectedUnregistered) {
            blockRegistry.unregisteredIntrusiveHolders = IdentityHashMap()
        }

        val savedFrozen = isRegistryFrozen(blockRegistry)

        try {
            setRegistryFrozen(blockRegistry, false)
            val properties = BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, blockData.id))
                .strength(blockData.destroyTime)
            if (blockData.requiresCorrectTool) {
                properties.requiresCorrectToolForDrops()
            }
            val block = Block(properties)
            Registry.register(BuiltInRegistries.BLOCK, blockData.id, block)
            DynamicRegistryHooks.onDynamicBlockRegistered(block)
        } finally {
            if (savedFrozen) setRegistryFrozen(blockRegistry, true)
            if (injectedUnregistered) blockRegistry.unregisteredIntrusiveHolders = previousUnregistered
        }
    }

    /**
     * Registers or updates an effect on the client.
     */
    protected fun registerOrUpdateEffectOnClient(effectData: EffectSyncPacket.EffectData) {
        registeredEffectData[effectData.id] = effectData

        val existingEffect = BuiltInRegistries.MOB_EFFECT.getOptional(effectData.id)
        if (existingEffect.isPresent) {
            return
        }

        registerNewEffect(effectData)
    }

    protected fun unregisterEffectOnClient(id: Identifier) {
        registeredEffectData.remove(id)
        RegistryMutationUtil.unregister(
            BuiltInRegistries.MOB_EFFECT as MappedRegistry<MobEffect>,
            ResourceKey.create(Registries.MOB_EFFECT, id)
        )
    }

    /**
     * Registers a new effect on the client.
     */
    protected fun registerNewEffect(effectData: EffectSyncPacket.EffectData) {
        @Suppress("UNCHECKED_CAST")
        val effectRegistry = BuiltInRegistries.MOB_EFFECT as MappedRegistry<MobEffect>
        val savedFrozen = isRegistryFrozen(effectRegistry)

        try {
            setRegistryFrozen(effectRegistry, false)
            val effect = object : MobEffect(effectData.category, effectData.color) {}
            Registry.register(BuiltInRegistries.MOB_EFFECT, effectData.id, effect)
        } finally {
            if (savedFrozen) setRegistryFrozen(effectRegistry, true)
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
    protected fun registerOrUpdateItemOnClient(itemData: ItemSyncPacket.ItemData) {
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

    protected fun unregisterItemOnClient(id: Identifier) {
        registeredItemData.remove(id)
        RegistryMutationUtil.unregister(
            BuiltInRegistries.ITEM as MappedRegistry<Item>,
            ResourceKey.create(Registries.ITEM, id)
        )
    }

    /**
     * Registers a new item on the client.
     *
     * @param itemData The item data to register
     */
    protected fun registerNewItem(itemData: ItemSyncPacket.ItemData) {
        @Suppress("UNCHECKED_CAST")
        val itemRegistry = BuiltInRegistries.ITEM as MappedRegistry<Item>

        // Inject unregisteredIntrusiveHolders if not present
        val previousUnregistered = itemRegistry.unregisteredIntrusiveHolders
        val injectedUnregistered = previousUnregistered == null
        if (injectedUnregistered) {
            itemRegistry.unregisteredIntrusiveHolders = IdentityHashMap()
        }

        val savedFrozen = isRegistryFrozen(itemRegistry)

        try {
            // Temporarily unfreeze registry
            setRegistryFrozen(itemRegistry, false)

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
            if (savedFrozen) setRegistryFrozen(itemRegistry, true)
            if (injectedUnregistered) itemRegistry.unregisteredIntrusiveHolders = previousUnregistered
        }
    }

    /**
     * Checks if there are pending effects to register.
     */
    fun hasPendingItems(): Boolean = pendingItemSnapshot != null

    /**
     * Checks if there are pending effects to register.
     */
    fun hasPendingEffects(): Boolean = pendingEffectSnapshot != null

    /**
     * Checks if there are pending blocks to register.
     */
    fun hasPendingBlocks(): Boolean = pendingBlockSnapshot != null

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
     * Clears pending snapshots and unregisters all client-side Katton content.
     * Called when disconnecting from server.
     */
    fun reset() {
        pendingItemSnapshot = null
        pendingEffectSnapshot = null
        pendingBlockSnapshot = null
        registeredItemData.keys.toList().forEach(::unregisterItemOnClient)
        registeredEffectData.keys.toList().forEach(::unregisterEffectOnClient)
        registeredBlockData.keys.toList().forEach(::unregisterBlockOnClient)
        registeredItemData.clear()
        registeredEffectData.clear()
        registeredBlockData.clear()
    }

    protected fun unregisterBlockOnClient(id: Identifier) {
        registeredBlockData.remove(id)
        RegistryMutationUtil.unregister(
            BuiltInRegistries.BLOCK as MappedRegistry<Block>,
            ResourceKey.create(Registries.BLOCK, id)
        )
    }
}
