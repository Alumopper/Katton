package top.katton.network

import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException
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
                buf.writeUtf(entry.key, ClientPacketLimits.MAX_DATA_KEY_CHARS)
                when (val v = entry.value) {
                    null -> buf.writeByte(TYPE_NULL.toInt())
                    is String -> {
                        buf.writeByte(TYPE_STRING.toInt())
                        buf.writeUtf(v, ClientPacketLimits.MAX_DATA_STRING_CHARS)
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
                        throw EncoderException("Unsupported client-data value type ${v.javaClass.name}")
                    }
                }
            }

            /**
             * Decodes one standalone entry while retaining the original public API.
             * Packet decoding uses the overload below so all entries share one aggregate budget.
             */
            fun read(buf: FriendlyByteBuf): DataEntry = read(buf, DataStringDecodeBudget())

            internal fun read(buf: FriendlyByteBuf, stringBudget: DataStringDecodeBudget): DataEntry {
                val key = buf.readUtf(ClientPacketLimits.MAX_DATA_KEY_CHARS)
                val type = buf.readByte()
                val value: Any? = when (type) {
                    TYPE_NULL -> null
                    TYPE_STRING -> stringBudget.readString(buf)
                    TYPE_INT -> buf.readVarInt()
                    TYPE_DOUBLE -> buf.readDouble()
                    TYPE_BOOLEAN -> buf.readBoolean()
                    else -> throw DecoderException("Unknown client-data value type $type")
                }
                return DataEntry(key, value)
            }
        }
    }

    /** Limits aggregate allocation, not just each individual UTF value. */
    internal class DataStringDecodeBudget {
        private var remainingCharacters = ClientPacketLimits.MAX_TOTAL_DATA_STRING_CHARS

        fun readString(buf: FriendlyByteBuf): String {
            val value = buf.readUtf(minOf(ClientPacketLimits.MAX_DATA_STRING_CHARS, remainingCharacters))
            remainingCharacters -= value.length
            return value
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
            ClientPacketLimits.requireCount(packet.entries.size, ClientPacketLimits.MAX_DATA_ENTRIES, "client-data entries")
            val keys = HashSet<String>(packet.entries.size)
            var totalStringCharacters = 0L
            packet.entries.forEach { entry ->
                ClientPacketLimits.requireUniqueEncoding(entry.key, keys, "client-data key")
                if (entry.value is String) totalStringCharacters += entry.value.length
            }
            ClientPacketLimits.requireEncoding(
                totalStringCharacters <= ClientPacketLimits.MAX_TOTAL_DATA_STRING_CHARS
            ) { "Client-data packet exceeds the total string-character limit" }
            buf.writeVarInt(packet.entries.size)
            packet.entries.forEach { DataEntry.write(buf, it) }
        }

        fun read(buf: FriendlyByteBuf): ClientDataSyncPacket {
            val count = ClientPacketLimits.readCount(buf, ClientPacketLimits.MAX_DATA_ENTRIES, "client-data entries")
            val entries = ArrayList<DataEntry>(count)
            val keys = HashSet<String>(count)
            val stringBudget = DataStringDecodeBudget()
            repeat(count) {
                val entry = DataEntry.read(buf, stringBudget)
                ClientPacketLimits.requireUniqueDecoded(entry.key, keys, "client-data key")
                entries.add(entry)
            }
            return ClientDataSyncPacket(entries)
        }

    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
