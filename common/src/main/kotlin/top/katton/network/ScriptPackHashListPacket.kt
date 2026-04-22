package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

data class ScriptPackHashListPacket(
    val entries: List<HashEntry>
) : CustomPacketPayload {

    data class HashEntry(
        val syncId: String,
        val scope: String,
        val hash: String,
        val name: String
    ) {
        fun write(buf: FriendlyByteBuf) {
            buf.writeUtf(syncId)
            buf.writeUtf(scope)
            buf.writeUtf(hash)
            buf.writeUtf(name)
        }

        companion object {
            fun read(buf: FriendlyByteBuf): HashEntry {
                return HashEntry(
                    syncId = buf.readUtf(),
                    scope = buf.readUtf(),
                    hash = buf.readUtf(),
                    name = buf.readUtf()
                )
            }
        }
    }

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(entries.size)
        entries.forEach { it.write(buf) }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ScriptPackHashListPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "script_pack_hashes"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ScriptPackHashListPacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf -> read(buf) })

        fun read(buf: FriendlyByteBuf): ScriptPackHashListPacket {
            val count = buf.readVarInt()
            val entries = ArrayList<HashEntry>(count)
            repeat(count) {
                entries.add(HashEntry.read(buf))
            }
            return ScriptPackHashListPacket(entries)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
