package top.katton.network

import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.RegistryOps
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.level.block.Block
import top.katton.Katton
import top.katton.registry.KattonRegistry

fun interface ServerConfigurationNetworkingSender {
   operator fun invoke(handler: ServerConfigurationPacketListenerImpl, payload: CustomPacketPayload)
}

/**
 * Server-side networking handler for Katton.
 * Handles sending item sync packets to connecting clients.
 */
object ServerNetworking {

    /**
     * Sends item sync packet to a connecting player.
     * Called from mixin before Fabric's registry sync check.
     *
     * @param handler The player's network handler
     */
    fun sendItemSyncPacket(handler: ServerConfigurationPacketListenerImpl, sender: ServerConfigurationNetworkingSender) {
        val items = collectKattonItems()
        if (items.isEmpty()) {
            return
        }

        val packet = ItemSyncPacket(items)
        sender(handler, packet)
    }

    /**
     * Sends effect sync packet to a connecting player.
     */
    fun sendEffectSyncPacket(handler: ServerConfigurationPacketListenerImpl, sender: ServerConfigurationNetworkingSender) {
        val effects = collectKattonEffects()
        if (effects.isEmpty()) {
            return
        }

        val packet = EffectSyncPacket(effects)
        sender(handler, packet)
    }

    /**
     * Sends block sync packet to a connecting player.
     */
    fun sendBlockSyncPacket(handler: ServerConfigurationPacketListenerImpl, sender: ServerConfigurationNetworkingSender) {
        val blocks = collectKattonBlocks()
        if (blocks.isEmpty()) {
            return
        }

        val packet = BlockSyncPacket(blocks)
        sender(handler, packet)
    }
    
    /**
     * Collects all Katton-managed items for synchronization.
     * Serializes the full DataComponentMap for each item using RegistryOps.
     * 
     * @return List of item data to sync
     */
    private fun collectKattonItems(): List<ItemSyncPacket.ItemData> {
        val server = Katton.server ?: return emptyList()
        val ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())
        val items = mutableListOf<ItemSyncPacket.ItemData>()
        
        for ((id, entry) in KattonRegistry.ITEMS) {
            val item = entry.item
            val components = item.components()
            items.add(ItemSyncPacket.ItemData.fromComponents(id, components, ops))
        }
        
        return items
    }

    /**
     * Collects all Katton-managed effects for synchronization.
     */
    private fun collectKattonEffects(): List<EffectSyncPacket.EffectData> {
        val effects = mutableListOf<EffectSyncPacket.EffectData>()

        for ((id, entry) in KattonRegistry.EFFECTS) {
            val effect: MobEffect = entry.effect
            effects.add(
                EffectSyncPacket.EffectData(
                    id = id,
                    category = effect.category,
                    color = effect.color
                )
            )
        }

        return effects
    }

    /**
     * Collects all Katton-managed blocks for synchronization.
     */
    private fun collectKattonBlocks(): List<BlockSyncPacket.BlockData> {
        val blocks = mutableListOf<BlockSyncPacket.BlockData>()

        for ((id, entry) in KattonRegistry.BLOCKS) {
            val block: Block = entry.block
            val state = block.defaultBlockState()
            blocks.add(
                BlockSyncPacket.BlockData(
                    id = id,
                    destroyTime = block.defaultDestroyTime(),
                    requiresCorrectTool = state.requiresCorrectToolForDrops()
                )
            )
        }

        return blocks
    }
}
