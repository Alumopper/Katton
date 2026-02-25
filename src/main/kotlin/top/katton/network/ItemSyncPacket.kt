package top.katton.network

import net.minecraft.core.component.DataComponents
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

/**
 * Packet for synchronizing Katton items from server to client.
 * Sent during configuration phase before Fabric's registry sync.
 */
data class ItemSyncPacket(val items: List<ItemData>) : CustomPacketPayload {

    /**
     * Data for a single item to be synchronized.
     */
    data class ItemData(
        val id: Identifier,
        val maxStackSize: Int,
        val maxDamage: Int,
        val translationKey: String,
        val itemName: Component?,
        val itemModel: Identifier?
    ) {
        fun write(buf: FriendlyByteBuf) {
            buf.writeIdentifier(id)
            buf.writeVarInt(maxStackSize)
            buf.writeVarInt(maxDamage)
            buf.writeUtf(translationKey)
            // Write optional itemName using ComponentSerialization
            buf.writeBoolean(itemName != null)
            itemName?.let { ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, it) }
            // Write optional itemModel
            buf.writeBoolean(itemModel != null)
            itemModel?.let { buf.writeIdentifier(it) }
        }

        companion object {
            fun read(buf: FriendlyByteBuf): ItemData {
                return ItemData(
                    id = buf.readIdentifier(),
                    maxStackSize = buf.readVarInt(),
                    maxDamage = buf.readVarInt(),
                    translationKey = buf.readUtf(),
                    itemName = if (buf.readBoolean()) ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf) else null,
                    itemModel = if (buf.readBoolean()) buf.readIdentifier() else null
                )
            }
        }
    }

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(items.size)
        for (item in items) {
            item.write(buf)
        }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ItemSyncPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "item_sync"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ItemSyncPacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf -> read(buf) })
        
        fun read(buf: FriendlyByteBuf): ItemSyncPacket {
            val count = buf.readVarInt()
            val items = mutableListOf<ItemData>()
            for (i in 0 until count) {
                items.add(ItemData.read(buf))
            }
            return ItemSyncPacket(items)
        }
    }
    
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
