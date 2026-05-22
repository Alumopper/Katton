package top.katton.network

import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import top.katton.Katton
import top.katton.api.ClientItemRenderMarker
import java.util.UUID

data class ClientItemRenderMarkerPacket(
    val action: Action,
    val markers: List<ClientItemRenderMarker>,
    val ids: List<UUID>
) : CustomPacketPayload {
    enum class Action {
        ADD_OR_UPDATE,
        REMOVE,
        CLEAR
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ClientItemRenderMarkerPacket> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "client_item_render_marker"))

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClientItemRenderMarkerPacket> =
            StreamCodec.of({ buf, packet -> write(buf, packet) }, { buf -> read(buf) })

        fun addOrUpdate(marker: ClientItemRenderMarker): ClientItemRenderMarkerPacket =
            ClientItemRenderMarkerPacket(Action.ADD_OR_UPDATE, listOf(marker), emptyList())

        fun remove(id: UUID): ClientItemRenderMarkerPacket =
            ClientItemRenderMarkerPacket(Action.REMOVE, emptyList(), listOf(id))

        fun clear(): ClientItemRenderMarkerPacket =
            ClientItemRenderMarkerPacket(Action.CLEAR, emptyList(), emptyList())

        fun write(buf: RegistryFriendlyByteBuf, packet: ClientItemRenderMarkerPacket) {
            buf.writeEnum(packet.action)
            buf.writeVarInt(packet.markers.size)
            packet.markers.forEach { writeMarker(buf, it) }
            buf.writeVarInt(packet.ids.size)
            packet.ids.forEach { buf.writeUUID(it) }
        }

        fun read(buf: RegistryFriendlyByteBuf): ClientItemRenderMarkerPacket {
            val action = buf.readEnum(Action::class.java)
            val markerCount = buf.readVarInt()
            val markers = ArrayList<ClientItemRenderMarker>(markerCount)
            repeat(markerCount) {
                markers.add(readMarker(buf))
            }
            val idCount = buf.readVarInt()
            val ids = ArrayList<UUID>(idCount)
            repeat(idCount) {
                ids.add(buf.readUUID())
            }
            return ClientItemRenderMarkerPacket(action, markers, ids)
        }

        private fun writeMarker(buf: RegistryFriendlyByteBuf, marker: ClientItemRenderMarker) {
            buf.writeUUID(marker.id)
            buf.writeResourceKey(marker.level)
            Vec3.STREAM_CODEC.encode(buf, marker.pos)
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, marker.stack)
            buf.writeEnum(marker.displayContext)
            buf.writeFloat(marker.scale)
            buf.writeFloat(marker.yaw)
            buf.writeFloat(marker.pitch)
            buf.writeFloat(marker.roll)
            buf.writeBoolean(marker.fullBright)
            buf.writeDouble(marker.maxDistance)
            buf.writeVarInt(marker.lifetimeTicks)
        }

        private fun readMarker(buf: RegistryFriendlyByteBuf): ClientItemRenderMarker {
            val id = buf.readUUID()
            val level = buf.readResourceKey(Registries.DIMENSION)
            val pos = Vec3.STREAM_CODEC.decode(buf)
            val stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
            val displayContext = buf.readEnum(ItemDisplayContext::class.java)
            val scale = buf.readFloat()
            val yaw = buf.readFloat()
            val pitch = buf.readFloat()
            val roll = buf.readFloat()
            val fullBright = buf.readBoolean()
            val maxDistance = buf.readDouble()
            val lifetimeTicks = buf.readVarInt()
            return ClientItemRenderMarker(
                id = id,
                level = level,
                pos = pos,
                stack = stack,
                displayContext = displayContext,
                scale = scale,
                yaw = yaw,
                pitch = pitch,
                roll = roll,
                fullBright = fullBright,
                maxDistance = maxDistance,
                lifetimeTicks = lifetimeTicks
            )
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
