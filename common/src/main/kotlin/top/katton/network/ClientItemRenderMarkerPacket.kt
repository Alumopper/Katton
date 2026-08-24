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
        private const val MAX_MARKERS = 1_024
        private const val MAX_MARKER_IDS = 4_096
        private const val MAX_ANIMATION_COMMAND_MARKERS = 256
        private const val MAX_ANIMATION_COMMAND_IDS = 64
        private const val MAX_ANIMATION_SETS_PER_MARKER = 256
        private const val MAX_PLAYING_ANIMATIONS_PER_MARKER = 256
        private const val MAX_ANIMATIONS_PER_SET = 256
        private const val MAX_KEYFRAMES_PER_ANIMATION = 4_096
        private const val MAX_TOTAL_ANIMATION_SETS = 4_096
        private const val MAX_TOTAL_ANIMATIONS = 16_384
        private const val MAX_TOTAL_KEYFRAMES = 131_072
        private const val MAX_ANIMATION_ID_CHARS = 256

        /** Prevents small nested counts from multiplying into excessive allocations. */
        private class DecodeBudget {
            private var remainingSets = MAX_TOTAL_ANIMATION_SETS
            private var remainingAnimations = MAX_TOTAL_ANIMATIONS
            private var remainingKeyframes = MAX_TOTAL_KEYFRAMES

            fun takeSets(count: Int) {
                ClientPacketLimits.requireDecoded(count <= remainingSets) {
                    "Item-render packet exceeds the total animation-set limit"
                }
                remainingSets -= count
            }

            fun takeAnimations(count: Int) {
                ClientPacketLimits.requireDecoded(count <= remainingAnimations) {
                    "Item-render packet exceeds the total animation limit"
                }
                remainingAnimations -= count
            }

            fun takeKeyframes(count: Int) {
                ClientPacketLimits.requireDecoded(count <= remainingKeyframes) {
                    "Item-render packet exceeds the total keyframe limit"
                }
                remainingKeyframes -= count
            }
        }

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
            validateForEncoding(packet)
            buf.writeEnum(packet.action)
            buf.writeVarInt(packet.markers.size)
            packet.markers.forEach { writeMarker(buf, it) }
            buf.writeVarInt(packet.ids.size)
            packet.ids.forEach { buf.writeUUID(it) }
            buf.writeVarInt(packet.animationSetIds.size)
            packet.animationSetIds.forEach { buf.writeUtf(it, MAX_ANIMATION_ID_CHARS) }
        }

        fun read(buf: RegistryFriendlyByteBuf): ClientItemRenderMarkerPacket {
            val action = buf.readEnum(Action::class.java)
            val budget = DecodeBudget()
            val markerCount = ClientPacketLimits.readCount(buf, markerLimit(action), "item-render markers")
            val markers = ArrayList<ClientItemRenderMarker>(markerCount)
            val markerIds = HashSet<UUID>(markerCount)
            repeat(markerCount) {
                val marker = readMarker(buf, budget)
                ClientPacketLimits.requireUniqueDecoded(marker.id, markerIds, "item-render marker id")
                markers.add(marker)
            }
            val idCount = ClientPacketLimits.readCount(buf, markerIdLimit(action), "item-render marker ids")
            val ids = ArrayList<UUID>(idCount)
            val uniqueIds = HashSet<UUID>(idCount)
            repeat(idCount) {
                val id = buf.readUUID()
                ClientPacketLimits.requireUniqueDecoded(id, uniqueIds, "item-render marker id")
                ids.add(id)
            }
            val animationSetIdCount = ClientPacketLimits.readCount(
                buf,
                animationCommandIdLimit(action),
                "item-render animation command ids"
            )
            val animationSetIds = ArrayList<String>(animationSetIdCount)
            val uniqueAnimationSetIds = HashSet<String>(animationSetIdCount)
            repeat(animationSetIdCount) {
                val animationSetId = buf.readUtf(MAX_ANIMATION_ID_CHARS)
                ClientPacketLimits.requireUniqueDecoded(
                    animationSetId,
                    uniqueAnimationSetIds,
                    "item-render animation command id"
                )
                animationSetIds.add(animationSetId)
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
                buf.writeUtf(id, MAX_ANIMATION_ID_CHARS)
                writeAnimationSet(buf, animationSet)
            }
            buf.writeVarInt(marker.playingAnimationID.size)
            marker.playingAnimationID.forEach { buf.writeUtf(it, MAX_ANIMATION_ID_CHARS) }
        }

        private fun readMarker(buf: RegistryFriendlyByteBuf, budget: DecodeBudget): ClientItemRenderMarker {
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
            ClientPacketLimits.requireDecoded(
                pos.x.isFinite() && pos.y.isFinite() && pos.z.isFinite() &&
                    scale.isFinite() && yaw.isFinite() && pitch.isFinite() && roll.isFinite() && maxDistance.isFinite()
            ) { "Item-render marker contains a non-finite transform or distance" }
            val animationCount = ClientPacketLimits.readCount(
                buf,
                MAX_ANIMATION_SETS_PER_MARKER,
                "animation sets in item-render marker"
            )
            budget.takeSets(animationCount)
            val animations = LinkedHashMap<String, ClientItemRenderAnimationSet>(animationCount)
            repeat(animationCount) {
                val animationId = buf.readUtf(MAX_ANIMATION_ID_CHARS)
                ClientPacketLimits.requireDecoded(animationId !in animations) {
                    "Duplicate item-render animation id '$animationId'"
                }
                animations[animationId] = readAnimationSet(buf, budget)
            }
            val playingAnimationCount = ClientPacketLimits.readCount(
                buf,
                MAX_PLAYING_ANIMATIONS_PER_MARKER,
                "playing animations in item-render marker"
            )
            val playingAnimationID = ArrayList<String>(playingAnimationCount)
            val uniquePlayingIds = HashSet<String>(playingAnimationCount)
            repeat(playingAnimationCount) {
                val animationId = buf.readUtf(MAX_ANIMATION_ID_CHARS)
                ClientPacketLimits.requireUniqueDecoded(animationId, uniquePlayingIds, "playing animation id")
                playingAnimationID.add(animationId)
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

        private fun readAnimationSet(
            buf: RegistryFriendlyByteBuf,
            budget: DecodeBudget
        ): ClientItemRenderAnimationSet {
            val durationTicks = buf.readVarInt()
            val delayTicks = buf.readVarInt()
            val loop = buf.readBoolean()
            val animationCount = ClientPacketLimits.readCount(
                buf,
                MAX_ANIMATIONS_PER_SET,
                "animations in item-render animation set"
            )
            budget.takeAnimations(animationCount)
            val animations = ArrayList<ClientItemRenderAnimation>(animationCount)
            repeat(animationCount) {
                animations.add(readAnimation(buf, budget))
            }
            return ClientItemRenderAnimationSet(
                animations = animations,
                durationTicks = durationTicks,
                delayTicks = delayTicks,
                loop = loop
            )
        }

        private fun writeAnimation(buf: RegistryFriendlyByteBuf, animation: ClientItemRenderAnimation) {
            buf.writeEnum(animation.target)
            buf.writeEnum(animation.mode)
            buf.writeVarInt(animation.keyframes.count { it is ClientItemRenderAnimationKeyframe })
            animation.keyframes.forEach { keyframe ->
                if (keyframe is ClientItemRenderAnimationKeyframe) writeKeyframe(buf, keyframe)
            }
        }

        private fun readAnimation(buf: RegistryFriendlyByteBuf, budget: DecodeBudget): ClientItemRenderAnimation {
            val target = buf.readEnum(ClientItemRenderAnimationTarget::class.java)
            val mode = buf.readEnum(ClientItemRenderAnimationMode::class.java)
            val keyframeCount = ClientPacketLimits.readCount(
                buf,
                MAX_KEYFRAMES_PER_ANIMATION,
                "item-render animation keyframes"
            )
            budget.takeKeyframes(keyframeCount)
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
            ClientPacketLimits.requireDecoded(
                time.isFinite() && value.x.isFinite() && value.y.isFinite() && value.z.isFinite()
            ) { "Item-render animation keyframe contains a non-finite value" }
            return ClientItemRenderAnimationKeyframe(time, value, easing)
        }

        private fun markerLimit(action: Action): Int =
            if (action == Action.ADD_OR_UPDATE) MAX_MARKERS else 0

        private fun markerIdLimit(action: Action): Int = when (action) {
            Action.REMOVE -> MAX_MARKER_IDS
            Action.PLAY_ANIMATION, Action.STOP_ANIMATION -> MAX_ANIMATION_COMMAND_MARKERS
            Action.ADD_OR_UPDATE, Action.CLEAR -> 0
        }

        private fun animationCommandIdLimit(action: Action): Int = when (action) {
            Action.PLAY_ANIMATION, Action.STOP_ANIMATION -> MAX_ANIMATION_COMMAND_IDS
            Action.ADD_OR_UPDATE, Action.REMOVE, Action.CLEAR -> 0
        }

        private fun validateForEncoding(packet: ClientItemRenderMarkerPacket) {
            ClientPacketLimits.requireCount(packet.markers.size, markerLimit(packet.action), "item-render markers")
            ClientPacketLimits.requireCount(packet.ids.size, markerIdLimit(packet.action), "item-render marker ids")
            ClientPacketLimits.requireCount(
                packet.animationSetIds.size,
                animationCommandIdLimit(packet.action),
                "item-render animation command ids"
            )

            val markerIds = HashSet<UUID>(packet.markers.size)
            val commandMarkerIds = HashSet<UUID>(packet.ids.size)
            val commandAnimationIds = HashSet<String>(packet.animationSetIds.size)
            packet.markers.forEach { marker ->
                ClientPacketLimits.requireUniqueEncoding(marker.id, markerIds, "item-render marker id")
            }
            packet.ids.forEach { id ->
                ClientPacketLimits.requireUniqueEncoding(id, commandMarkerIds, "item-render marker id")
            }
            packet.animationSetIds.forEach { id ->
                ClientPacketLimits.requireUniqueEncoding(id, commandAnimationIds, "item-render animation command id")
                ClientPacketLimits.requireEncoding(id.length <= MAX_ANIMATION_ID_CHARS) {
                    "Item-render animation command id is too long"
                }
            }

            var totalSets = 0L
            var totalAnimations = 0L
            var totalKeyframes = 0L
            packet.markers.forEach { marker ->
                ClientPacketLimits.requireEncoding(
                    marker.pos.x.isFinite() && marker.pos.y.isFinite() && marker.pos.z.isFinite() &&
                        marker.scale.isFinite() && marker.yaw.isFinite() && marker.pitch.isFinite() &&
                        marker.roll.isFinite() && marker.maxDistance.isFinite()
                ) { "Item-render marker contains a non-finite transform or distance" }
                ClientPacketLimits.requireCount(
                    marker.animations.size,
                    MAX_ANIMATION_SETS_PER_MARKER,
                    "animation sets in item-render marker"
                )
                ClientPacketLimits.requireCount(
                    marker.playingAnimationID.size,
                    MAX_PLAYING_ANIMATIONS_PER_MARKER,
                    "playing animations in item-render marker"
                )
                val playingIds = HashSet<String>(marker.playingAnimationID.size)
                marker.playingAnimationID.forEach { id ->
                    ClientPacketLimits.requireUniqueEncoding(id, playingIds, "playing animation id")
                    ClientPacketLimits.requireEncoding(id.length <= MAX_ANIMATION_ID_CHARS) {
                        "Playing item-render animation id is too long"
                    }
                }
                totalSets += marker.animations.size
                marker.animations.forEach { (id, animationSet) ->
                    ClientPacketLimits.requireEncoding(id.length <= MAX_ANIMATION_ID_CHARS) {
                        "Item-render animation id is too long"
                    }
                    ClientPacketLimits.requireCount(
                        animationSet.animations.size,
                        MAX_ANIMATIONS_PER_SET,
                        "animations in item-render animation set"
                    )
                    totalAnimations += animationSet.animations.size
                    animationSet.animations.forEach { animation ->
                        var keyframeCount = 0
                        animation.keyframes.forEach { keyframe ->
                            if (keyframe is ClientItemRenderAnimationKeyframe) {
                                keyframeCount++
                                ClientPacketLimits.requireEncoding(
                                    keyframe.time.isFinite() && keyframe.value.x.isFinite() &&
                                        keyframe.value.y.isFinite() && keyframe.value.z.isFinite()
                                ) { "Item-render animation keyframe contains a non-finite value" }
                            }
                        }
                        ClientPacketLimits.requireCount(
                            keyframeCount,
                            MAX_KEYFRAMES_PER_ANIMATION,
                            "item-render animation keyframes"
                        )
                        totalKeyframes += keyframeCount
                    }
                }
            }
            ClientPacketLimits.requireEncoding(totalSets <= MAX_TOTAL_ANIMATION_SETS) {
                "Item-render packet exceeds the total animation-set limit"
            }
            ClientPacketLimits.requireEncoding(totalAnimations <= MAX_TOTAL_ANIMATIONS) {
                "Item-render packet exceeds the total animation limit"
            }
            ClientPacketLimits.requireEncoding(totalKeyframes <= MAX_TOTAL_KEYFRAMES) {
                "Item-render packet exceeds the total keyframe limit"
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
