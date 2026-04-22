package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

data class ScriptPackRequestPacket(
    val requestedSyncIds: List<String>
) : CustomPacketPayload {

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(requestedSyncIds.size)
        requestedSyncIds.forEach(buf::writeUtf)
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ScriptPackRequestPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "script_pack_request"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ScriptPackRequestPacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf -> read(buf) })

        fun read(buf: FriendlyByteBuf): ScriptPackRequestPacket {
            val count = buf.readVarInt()
            val ids = ArrayList<String>(count)
            repeat(count) {
                ids.add(buf.readUtf())
            }
            return ScriptPackRequestPacket(ids)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
