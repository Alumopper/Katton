package top.katton.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import top.katton.api.ClientItemRenderMarker
import top.katton.network.ClientItemRenderMarkerPacket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ClientItemRenderMarkerManager {
    private const val FULL_BRIGHT_LIGHT = 0x00F000F0
    private val markers = ConcurrentHashMap<UUID, Entry>()
    private val mc = Minecraft.getInstance()

    private data class Entry(
        val marker: ClientItemRenderMarker,
        var remainingTicks: Int?,
        val renderState: ItemStackRenderState = ItemStackRenderState()
    )

    @JvmStatic
    fun handlePacket(packet: ClientItemRenderMarkerPacket) {
        when (packet.action) {
            ClientItemRenderMarkerPacket.Action.ADD_OR_UPDATE -> {
                packet.markers.forEach { addOrUpdate(it) }
            }
            ClientItemRenderMarkerPacket.Action.REMOVE -> {
                packet.ids.forEach { markers.remove(it) }
            }
            ClientItemRenderMarkerPacket.Action.CLEAR -> clear()
        }
    }

    @JvmStatic
    fun addOrUpdate(marker: ClientItemRenderMarker) {
        if (marker.stack.isEmpty()) {
            markers.remove(marker.id)
            return
        }
        val ttl = marker.lifetimeTicks.takeIf { it > 0 }
        markers[marker.id] = Entry(marker.copy(stack = marker.stack.copy()), ttl)
    }

    @JvmStatic
    fun remove(id: UUID) {
        markers.remove(id)
    }

    @JvmStatic
    fun clear() {
        markers.clear()
    }

    @JvmStatic
    fun tick() {
        val iterator = markers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val remaining = entry.remainingTicks ?: continue
            if (remaining <= 1) {
                iterator.remove()
            } else {
                entry.remainingTicks = remaining - 1
            }
        }
    }

    @JvmStatic
    fun render(camera: CameraRenderState?, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector, _tickDelta: Float) {
        if (markers.isEmpty()) return
        val level = mc.level ?: return
        val cameraState = camera ?: return
        val camPos = cameraState.pos ?: return
        val currentDimension = level.dimension()

        for (entry in markers.values) {
            val marker = entry.marker
            if (marker.level != currentDimension || marker.scale <= 0.0f || marker.stack.isEmpty()) {
                continue
            }

            val distanceLimit = marker.maxDistance
            if (distanceLimit > 0.0 && marker.pos.distanceToSqr(camPos) > distanceLimit * distanceLimit) {
                continue
            }

            val cullSize = marker.scale.toDouble().coerceAtLeast(0.25)
            val bounds = AABB.ofSize(marker.pos, cullSize, cullSize, cullSize)
            if (cameraState.cullFrustum != null && !cameraState.cullFrustum.isVisible(bounds)) {
                continue
            }

            val light = if (marker.fullBright) {
                FULL_BRIGHT_LIGHT
            } else {
                LevelRenderer.getLightCoords(level, BlockPos.containing(marker.pos))
            }

            poseStack.pushPose()
            try {
                poseStack.translate(marker.pos.x - camPos.x, marker.pos.y - camPos.y, marker.pos.z - camPos.z)
                poseStack.mulPose(Axis.YP.rotationDegrees(marker.yaw))
                poseStack.mulPose(Axis.XP.rotationDegrees(marker.pitch))
                poseStack.mulPose(Axis.ZP.rotationDegrees(marker.roll))
                poseStack.scale(marker.scale, marker.scale, marker.scale)

                entry.renderState.clear()
                mc.itemModelResolver.updateForTopItem(
                    entry.renderState,
                    marker.stack,
                    marker.displayContext,
                    level,
                    null,
                    marker.id.hashCode()
                )
                entry.renderState.submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, 0)
            } finally {
                poseStack.popPose()
            }
        }
    }
}
