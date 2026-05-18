package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

/**
 * Play-phase packet for syncing key-value data from server to client.
 *
 * Supports String, Int, Double, Boolean, and null values.
 * Multiple entries can be batched in a single packet for efficiency.
 */
data class ClientDataSyncPacket(
    val entries: List<DataEntry>
) : CustomPacketPayload {

    data class DataEntry(
        val key: String,
        val value: Any?
    ) {
        companion object {
            private const val TYPE_NULL: Byte = 0
            private const val TYPE_STRING: Byte = 1
            private const val TYPE_INT: Byte = 2
            private const val TYPE_DOUBLE: Byte = 3
            private const val TYPE_BOOLEAN: Byte = 4

            fun write(buf: FriendlyByteBuf, entry: DataEntry) {
                buf.writeUtf(entry.key)
                when (val v = entry.value) {
                    null -> buf.writeByte(TYPE_NULL.toInt())
                    is String -> {
                        buf.writeByte(TYPE_STRING.toInt())
                        buf.writeUtf(v)
                    }
                    is Int -> {
                        buf.writeByte(TYPE_INT.toInt())
                        buf.writeVarInt(v)
                    }
                    is Double -> {
                        buf.writeByte(TYPE_DOUBLE.toInt())
                        buf.writeDouble(v)
                    }
                    is Boolean -> {
                        buf.writeByte(TYPE_BOOLEAN.toInt())
                        buf.writeBoolean(v)
                    }
                    else -> {
                        buf.writeByte(TYPE_NULL.toInt())
                    }
                }
            }

            fun read(buf: FriendlyByteBuf): DataEntry {
                val key = buf.readUtf()
                val type = buf.readByte()
                val value: Any? = when (type) {
                    TYPE_NULL -> null
                    TYPE_STRING -> buf.readUtf()
                    TYPE_INT -> buf.readVarInt()
                    TYPE_DOUBLE -> buf.readDouble()
                    TYPE_BOOLEAN -> buf.readBoolean()
                    else -> null
                }
                return DataEntry(key, value)
            }
        }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ClientDataSyncPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "client_data_sync"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ClientDataSyncPacket> =
            StreamCodec.of({ buf, packet -> write(buf, packet) }, { buf -> read(buf) })

        fun write(buf: FriendlyByteBuf, packet: ClientDataSyncPacket) {
            buf.writeVarInt(packet.entries.size)
            packet.entries.forEach { DataEntry.write(buf, it) }
        }

        fun read(buf: FriendlyByteBuf): ClientDataSyncPacket {
            val count = buf.readVarInt()
            val entries = ArrayList<DataEntry>(count)
            repeat(count) {
                entries.add(DataEntry.read(buf))
            }
            return ClientDataSyncPacket(entries)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
