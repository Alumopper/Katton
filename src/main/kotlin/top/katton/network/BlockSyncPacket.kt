package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

/**
 * Packet for synchronizing Katton blocks from server to client.
 * Sent during configuration phase before Fabric's registry sync check.
 */
data class BlockSyncPacket(val blocks: List<BlockData>) : CustomPacketPayload {

    /**
     * Data for a single block to be synchronized.
     */
    data class BlockData(
        val id: Identifier,
        val destroyTime: Float,
        val requiresCorrectTool: Boolean
    ) {
        fun write(buf: FriendlyByteBuf) {
            buf.writeIdentifier(id)
            buf.writeFloat(destroyTime)
            buf.writeBoolean(requiresCorrectTool)
        }

        companion object {
            fun read(buf: FriendlyByteBuf): BlockData {
                return BlockData(
                    id = buf.readIdentifier(),
                    destroyTime = buf.readFloat(),
                    requiresCorrectTool = buf.readBoolean()
                )
            }
        }
    }

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(blocks.size)
        for (block in blocks) {
            block.write(buf)
        }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<BlockSyncPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "block_sync"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BlockSyncPacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf -> read(buf) })

        fun read(buf: FriendlyByteBuf): BlockSyncPacket {
            val count = buf.readVarInt()
            val blocks = mutableListOf<BlockData>()
            for (i in 0 until count) {
                blocks.add(BlockData.read(buf))
            }
            return BlockSyncPacket(blocks)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

