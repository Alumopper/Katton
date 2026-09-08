package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

data class ScriptPackSyncAckPacket(
    val revision: Long,
    val success: Boolean,
    val message: String = ""
) : CustomPacketPayload {
    private fun write(buf: FriendlyByteBuf) {
        buf.writeInt(0x4b500003)
        buf.writeVarLong(revision)
        buf.writeBoolean(success)
        buf.writeUtf(message, 1024)
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ScriptPackSyncAckPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "script_pack_sync_ack"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ScriptPackSyncAckPacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf ->
                require(buf.readInt() == 0x4b500003) { "Incompatible Katton pack protocol; update client/server" }
                ScriptPackSyncAckPacket(buf.readVarLong(), buf.readBoolean(), buf.readUtf(1024))
            })
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
