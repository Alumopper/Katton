package top.katton.network

import com.mojang.serialization.DynamicOps
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import top.katton.Katton

/**
 * Packet for synchronizing Katton items from server to client.
 * Sent during configuration phase before Fabric's registry sync.
 * 
 * Uses DataComponentMap.CODEC to serialize the full component map as NBT,
 * which supports all vanilla and modded component types automatically.
 */
private val logger = LoggerFactory.getLogger("${Katton.MOD_ID}/ItemSyncPacket")

data class ItemSyncPacket(val items: List<ItemData>) : CustomPacketPayload {

    /**
     * Data for a single item to be synchronized.
     * Contains the item id and its full DataComponentMap serialized as NBT.
     */
    data class ItemData(
        val id: Identifier,
        val componentsNbt: CompoundTag
    ) {
        fun write(buf: FriendlyByteBuf) {
            buf.writeIdentifier(id)
            buf.writeNbt(componentsNbt)
        }

        companion object {
            fun read(buf: FriendlyByteBuf): ItemData {
                return ItemData(
                    id = buf.readIdentifier(),
                    componentsNbt = buf.readNbt() ?: CompoundTag()
                )
            }

            /**
             * Creates ItemData from an item's components using RegistryOps for proper serialization.
             */
            fun fromComponents(id: Identifier, components: DataComponentMap, ops: DynamicOps<Tag>): ItemData {
                val result = DataComponentMap.CODEC.encodeStart(ops, components)
                val nbt = result.resultOrPartial { error ->
                    logger.warn("Failed to encode components for {}: {}", id, error)
                }.orElse(CompoundTag()) as? CompoundTag ?: CompoundTag()
                return ItemData(id, nbt)
            }
        }

        /**
         * Decodes the NBT back into a DataComponentMap.
         * Uses NbtOps since we may not have full registry access on client during configuration.
         */
        fun decodeComponents(ops: DynamicOps<Tag>): DataComponentMap {
            val result = DataComponentMap.CODEC.parse(ops, componentsNbt)
            return result.resultOrPartial { error ->
                logger.warn("Failed to decode components for {}: {}", id, error)
            }.orElse(DataComponentMap.EMPTY)
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
