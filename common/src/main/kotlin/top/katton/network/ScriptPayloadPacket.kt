package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/** A host-loaded payload type whose codec survives script pack reloads. */
class ScriptPayloadPacket(val channel: Identifier, val data: ByteArray) : CustomPacketPayload {
    init {
        require(channel.toString().length <= MAX_CHANNEL_CHARS) { "Script channel exceeds $MAX_CHANNEL_CHARS characters" }
        require(data.size <= MAX_DATA_BYTES) { "Script packet exceeds $MAX_DATA_BYTES bytes" }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val MAX_DATA_BYTES = 16384
        private const val MAX_CHANNEL_CHARS = 128

        @JvmField
        val TYPE = CustomPacketPayload.Type<ScriptPayloadPacket>(Identifier.fromNamespaceAndPath("katton", "script_payload"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ScriptPayloadPacket> = StreamCodec.of(
            { buf, packet ->
                buf.writeUtf(packet.channel.toString(), MAX_CHANNEL_CHARS)
                buf.writeVarInt(packet.data.size)
                buf.writeBytes(packet.data)
            },
            { buf ->
                val channel = Identifier.parse(buf.readUtf(MAX_CHANNEL_CHARS))
                val length = buf.readVarInt()
                require(length in 0..MAX_DATA_BYTES && length <= buf.readableBytes()) { "Invalid script packet length: $length" }
                val data = ByteArray(length)
                buf.readBytes(data)
                ScriptPayloadPacket(channel, data)
            }
        )
    }
}
