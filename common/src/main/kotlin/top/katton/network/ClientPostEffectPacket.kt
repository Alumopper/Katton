package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import top.katton.Katton

data class ClientPostEffectPacket(
    val action: Action,
    val effectId: String? = null
) : CustomPacketPayload {
    enum class Action {
        SET,
        CLEAR,
        TOGGLE
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ClientPostEffectPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "client_post_effect"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ClientPostEffectPacket> =
            StreamCodec.of({ buf, packet -> write(buf, packet) }, { buf -> read(buf) })

        fun set(id: Identifier): ClientPostEffectPacket =
            ClientPostEffectPacket(Action.SET, effectId = id.toString())

        fun clear(): ClientPostEffectPacket =
            ClientPostEffectPacket(Action.CLEAR)

        fun toggle(): ClientPostEffectPacket =
            ClientPostEffectPacket(Action.TOGGLE)

        fun write(buf: FriendlyByteBuf, packet: ClientPostEffectPacket) {
            validate(packet, decoding = false)
            buf.writeEnum(packet.action)
            buf.writeNullable(packet.effectId) { b, value ->
                b.writeUtf(value, ClientPacketLimits.MAX_RESOURCE_ID_CHARS)
            }
        }

        fun read(buf: FriendlyByteBuf): ClientPostEffectPacket {
            val action = buf.readEnum(Action::class.java)
            val effectId = buf.readNullable { it.readUtf(ClientPacketLimits.MAX_RESOURCE_ID_CHARS) }
            return ClientPostEffectPacket(action, effectId).also { validate(it, decoding = true) }
        }

        private fun validate(packet: ClientPostEffectPacket, decoding: Boolean) {
            val valid = when (packet.action) {
                Action.SET -> packet.effectId?.let(Identifier::tryParse) != null
                Action.CLEAR, Action.TOGGLE -> packet.effectId == null
            }
            if (decoding) {
                ClientPacketLimits.requireDecoded(valid) { "Invalid post-effect payload for ${packet.action}" }
            } else {
                ClientPacketLimits.requireEncoding(valid) { "Invalid post-effect payload for ${packet.action}" }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
