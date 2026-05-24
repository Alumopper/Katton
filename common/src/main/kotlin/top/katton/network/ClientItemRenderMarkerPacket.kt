package top.katton.network

import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import top.katton.Katton
import top.katton.api.ClientItemRenderAnimation
import top.katton.api.ClientItemRenderAnimationKeyframe
import top.katton.api.ClientItemRenderAnimationMode
import top.katton.api.ClientItemRenderAnimationSet
import top.katton.api.ClientItemRenderAnimationTarget
import top.katton.api.ClientItemRenderEasing
import top.katton.api.ClientItemRenderKeyframe
import top.katton.api.ClientItemRenderMarker
import java.util.UUID

data class ClientItemRenderMarkerPacket(
    val action: Action,
    val markers: List<ClientItemRenderMarker>,
    val ids: List<UUID>,
    val animationSetIds: List<String> = emptyList()
) : CustomPacketPayload {
    enum class Action {
        ADD_OR_UPDATE,
        REMOVE,
        CLEAR,
        PLAY_ANIMATION,
        STOP_ANIMATION
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

        fun playAnimation(id: UUID, animationSetId: String): ClientItemRenderMarkerPacket =
            ClientItemRenderMarkerPacket(Action.PLAY_ANIMATION, emptyList(), listOf(id), listOf(animationSetId))

        fun stopAnimation(id: UUID, animationSetId: String): ClientItemRenderMarkerPacket =
            ClientItemRenderMarkerPacket(Action.STOP_ANIMATION, emptyList(), listOf(id), listOf(animationSetId))

        fun write(buf: RegistryFriendlyByteBuf, packet: ClientItemRenderMarkerPacket) {
            buf.writeEnum(packet.action)
            buf.writeVarInt(packet.markers.size)
            packet.markers.forEach { writeMarker(buf, it) }
            buf.writeVarInt(packet.ids.size)
            packet.ids.forEach { buf.writeUUID(it) }
            buf.writeVarInt(packet.animationSetIds.size)
            packet.animationSetIds.forEach(buf::writeUtf)
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
            val animationSetIdCount = buf.readVarInt()
            val animationSetIds = ArrayList<String>(animationSetIdCount)
            repeat(animationSetIdCount) {
                animationSetIds.add(buf.readUtf())
            }
            return ClientItemRenderMarkerPacket(action, markers, ids, animationSetIds)
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
            buf.writeVarInt(marker.animations.size)
            marker.animations.forEach { (id, animationSet) ->
                buf.writeUtf(id)
                writeAnimationSet(buf, animationSet)
            }
            buf.writeVarInt(marker.playingAnimationID.size)
            marker.playingAnimationID.forEach(buf::writeUtf)
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
            val animationCount = buf.readVarInt()
            val animations = LinkedHashMap<String, ClientItemRenderAnimationSet>(animationCount)
            repeat(animationCount) {
                animations[buf.readUtf()] = readAnimationSet(buf)
            }
            val playingAnimationCount = buf.readVarInt()
            val playingAnimationID = ArrayList<String>(playingAnimationCount)
            repeat(playingAnimationCount) {
                playingAnimationID.add(buf.readUtf())
            }
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
                lifetimeTicks = lifetimeTicks,
                animations = animations,
                playingAnimationID = playingAnimationID
            )
        }

        private fun writeAnimationSet(buf: RegistryFriendlyByteBuf, animationSet: ClientItemRenderAnimationSet) {
            buf.writeVarInt(animationSet.durationTicks)
            buf.writeVarInt(animationSet.delayTicks)
            buf.writeBoolean(animationSet.loop)
            buf.writeVarInt(animationSet.animations.size)
            animationSet.animations.forEach { writeAnimation(buf, it) }
        }

        private fun readAnimationSet(buf: RegistryFriendlyByteBuf): ClientItemRenderAnimationSet {
            val durationTicks = buf.readVarInt()
            val delayTicks = buf.readVarInt()
            val loop = buf.readBoolean()
            val animationCount = buf.readVarInt()
            val animations = ArrayList<ClientItemRenderAnimation>(animationCount)
            repeat(animationCount) {
                animations.add(readAnimation(buf))
            }
            return ClientItemRenderAnimationSet(
                animations = animations,
                durationTicks = durationTicks,
                delayTicks = delayTicks,
                loop = loop
            )
        }

        private fun writeAnimation(buf: RegistryFriendlyByteBuf, animation: ClientItemRenderAnimation) {
            val transformKeyframes = animation.keyframes.filterIsInstance<ClientItemRenderAnimationKeyframe>()
            buf.writeEnum(animation.target)
            buf.writeEnum(animation.mode)
            buf.writeVarInt(transformKeyframes.size)
            transformKeyframes.forEach { writeKeyframe(buf, it) }
        }

        private fun readAnimation(buf: RegistryFriendlyByteBuf): ClientItemRenderAnimation {
            val target = buf.readEnum(ClientItemRenderAnimationTarget::class.java)
            val mode = buf.readEnum(ClientItemRenderAnimationMode::class.java)
            val keyframeCount = buf.readVarInt()
            val keyframes = ArrayList<ClientItemRenderKeyframe>(keyframeCount)
            repeat(keyframeCount) {
                keyframes.add(readKeyframe(buf))
            }
            return ClientItemRenderAnimation(
                target = target,
                mode = mode,
                keyframes = keyframes
            )
        }

        private fun writeKeyframe(buf: RegistryFriendlyByteBuf, keyframe: ClientItemRenderAnimationKeyframe) {
            buf.writeFloat(keyframe.time)
            Vec3.STREAM_CODEC.encode(buf, keyframe.value)
            buf.writeEnum(keyframe.easing)
        }

        private fun readKeyframe(buf: RegistryFriendlyByteBuf): ClientItemRenderAnimationKeyframe {
            val time = buf.readFloat()
            val value = Vec3.STREAM_CODEC.decode(buf)
            val easing = buf.readEnum(ClientItemRenderEasing::class.java)
            return ClientItemRenderAnimationKeyframe(time, value, easing)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
