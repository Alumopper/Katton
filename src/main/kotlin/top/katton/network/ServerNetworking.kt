package top.katton.network

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.server.network.ServerGamePacketListenerImpl
import top.katton.registry.KattonRegistry

/**
 * Server-side networking handler for Katton.
 * Handles sending item sync packets to connecting clients.
 */
object ServerNetworking {
    
    /**
     * Initializes server networking.
     * Payload type registration is handled by the common initialization.
     */
    fun initialize() {
        // Payload type is registered in common code (both sides need to register)
    }
    
    /**
     * Sends item sync packet to a connecting player.
     * Called from mixin before Fabric's registry sync check.
     *
     * @param handler The player's network handler
     */
    fun sendItemSyncPacket(handler: ServerConfigurationPacketListenerImpl) {
        val items = collectKattonItems()
        if (items.isEmpty()) {
            return
        }
        
        val packet = ItemSyncPacket(items)
        ServerConfigurationNetworking.send(handler, packet)
    }
    
    /**
     * Collects all Katton-managed items for synchronization.
     * 
     * @return List of item data to sync
     */
    private fun collectKattonItems(): List<ItemSyncPacket.ItemData> {
        val items = mutableListOf<ItemSyncPacket.ItemData>()
        
        for ((id, entry) in KattonRegistry.ITEMS) {
            val item = entry.item
            items.add(ItemSyncPacket.ItemData(
                id = id,
                maxStackSize = item.defaultMaxStackSize,
                maxDamage = 0, // Damage is handled via components, not needed for basic sync
                translationKey = item.descriptionId,
                itemName = item.components().get(DataComponents.ITEM_NAME),
                itemModel = item.components().get(DataComponents.ITEM_MODEL)
            ))
        }
        
        return items
    }
}
