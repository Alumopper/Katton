package top.katton.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffectCategory
import top.katton.Katton

/**
 * Packet for synchronizing Katton mob effects from server to client.
 * Sent during configuration phase before Fabric's registry sync check.
 */
data class EffectSyncPacket(val effects: List<EffectData>) : CustomPacketPayload {

    /**
     * Data for a single effect to be synchronized.
     */
    data class EffectData(
        val id: Identifier,
        val category: MobEffectCategory,
        val color: Int
    ) {
        fun write(buf: FriendlyByteBuf) {
            buf.writeIdentifier(id)
            buf.writeUtf(category.name)
            buf.writeInt(color)
        }

        companion object {
            fun read(buf: FriendlyByteBuf): EffectData {
                val id = buf.readIdentifier()
                val category = runCatching { MobEffectCategory.valueOf(buf.readUtf()) }
                    .getOrDefault(MobEffectCategory.NEUTRAL)
                val color = buf.readInt()
                return EffectData(id, category, color)
            }
        }
    }

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(effects.size)
        for (effect in effects) {
            effect.write(buf)
        }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<EffectSyncPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "effect_sync"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, EffectSyncPacket> =
            StreamCodec.of({ buf, packet -> packet.write(buf) }, { buf -> read(buf) })

        fun read(buf: FriendlyByteBuf): EffectSyncPacket {
            val count = buf.readVarInt()
            val effects = mutableListOf<EffectData>()
            for (i in 0 until count) {
                effects.add(EffectData.read(buf))
            }
            return EffectSyncPacket(effects)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

