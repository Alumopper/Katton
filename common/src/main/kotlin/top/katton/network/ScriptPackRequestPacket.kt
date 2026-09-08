package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

data class ScriptPackRequestPacket(
    val requestedSyncIds: List<String>,
    val revision: Long = 0L
) : CustomPacketPayload {

    fun write(buf: FriendlyByteBuf) {
        ScriptPackPacketLimits.requireCount(requestedSyncIds.size, ScriptPackPacketLimits.MAX_PACKS, "requested script packs")
        val uniqueIds = HashSet<String>(requestedSyncIds.size)
        requestedSyncIds.forEach { id ->
            ScriptPackPacketLimits.requireUniqueForEncoding(id, uniqueIds, "requested script pack id")
        }
        buf.writeInt(0x4b500003)
        buf.writeVarLong(revision)
        buf.writeVarInt(requestedSyncIds.size)
        requestedSyncIds.forEach { buf.writeUtf(it, ScriptPackPacketLimits.MAX_SYNC_ID_CHARS) }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ScriptPackRequestPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "script_pack_request"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ScriptPackRequestPacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf -> read(buf) })

        fun read(buf: FriendlyByteBuf): ScriptPackRequestPacket {
            require(buf.readInt() == 0x4b500003) { "Incompatible Katton pack protocol; update client/server and rebuild the v3 cache" }
            val revision = buf.readVarLong()
            val count = ScriptPackPacketLimits.readCount(buf, ScriptPackPacketLimits.MAX_PACKS, "requested script packs")
            val ids = ArrayList<String>(count)
            val uniqueIds = HashSet<String>(count)
            repeat(count) {
                val id = buf.readUtf(ScriptPackPacketLimits.MAX_SYNC_ID_CHARS)
                ScriptPackPacketLimits.requireUnique(id, uniqueIds, "requested script pack id")
                ids.add(id)
            }
            return ScriptPackRequestPacket(ids, revision)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
