package top.katton.network

import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.level.block.Block
import top.katton.Katton
import top.katton.registry.KattonRegistry

fun interface ServerConfigurationNetworkingSender {
   operator fun invoke(handler: ServerConfigurationPacketListenerImpl, payload: CustomPacketPayload)
}

fun interface ServerPlayNetworkingSender {
    operator fun invoke(player: ServerPlayer, payload: CustomPacketPayload)
}

/**
 * Server-side networking handler for Katton.
 * Handles sending item sync packets to connecting clients.
 */
object ServerNetworking {

    @Volatile
    private var playSender: ServerPlayNetworkingSender? = null

    fun setPlaySender(sender: ServerPlayNetworkingSender?) {
        playSender = sender
    }

    /**
     * Sends item sync packet to a connecting player.
     * Called from mixin before Fabric's registry sync check.
     *
     * @param handler The player's network handler
     */
    fun sendItemSyncPacket(handler: ServerConfigurationPacketListenerImpl, sender: ServerConfigurationNetworkingSender) {
        val packet = createItemSyncPacket()
        if (packet.items.isEmpty()) {
            return
        }
        sender(handler, packet)
    }

    /**
     * Sends effect sync packet to a connecting player.
     */
    fun sendEffectSyncPacket(handler: ServerConfigurationPacketListenerImpl, sender: ServerConfigurationNetworkingSender) {
        val packet = createEffectSyncPacket()
        if (packet.effects.isEmpty()) {
            return
        }
        sender(handler, packet)
    }

    /**
     * Sends block sync packet to a connecting player.
     */
    fun sendBlockSyncPacket(handler: ServerConfigurationPacketListenerImpl, sender: ServerConfigurationNetworkingSender) {
        val packet = createBlockSyncPacket()
        if (packet.blocks.isEmpty()) {
            return
        }
        sender(handler, packet)
    }

    fun syncOnlinePlayers(server: MinecraftServer) {
        val sender = playSender ?: return
        val itemPacket = createItemSyncPacket()
        val effectPacket = createEffectSyncPacket()
        val blockPacket = createBlockSyncPacket()

        for (player in server.playerList.players) {
            sender(player, itemPacket)
            sender(player, effectPacket)
            sender(player, blockPacket)
        }
    }

    private fun createItemSyncPacket(): ItemSyncPacket {
        return ItemSyncPacket(collectKattonItems())
    }

    /**
     * Creates effect sync packet for the current registry snapshot.
     */
    private fun createEffectSyncPacket(): EffectSyncPacket {
        return EffectSyncPacket(collectKattonEffects())
    }

    /**
     * Creates block sync packet for the current registry snapshot.
     */
    private fun createBlockSyncPacket(): BlockSyncPacket {
        return BlockSyncPacket(collectKattonBlocks())
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
