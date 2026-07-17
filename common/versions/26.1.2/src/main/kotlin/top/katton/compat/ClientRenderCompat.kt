package top.katton.compat

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.Vec3

internal fun drawLine3DCompat(
    camPos: Vec3,
    x1: Double,
    y1: Double,
    z1: Double,
    x2: Double,
    y2: Double,
    z2: Double,
    argbColor: Int,
    lineWidth: Float
): Boolean {
    val a = (argbColor ushr 24) and 0xFF
    val r = (argbColor ushr 16) and 0xFF
    val g = (argbColor ushr 8) and 0xFF
    val b = argbColor and 0xFF

    val dx = (x2 - x1).toFloat()
    val dy = (y2 - y1).toFloat()
    val dz = (z2 - z1).toFloat()
    val len = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat().coerceAtLeast(1e-6f)
    val nx = dx / len
    val ny = dy / len
    val nz = dz / len

    val poseStack = PoseStack()
    poseStack.translate(-camPos.x, -camPos.y, -camPos.z)
    val pose = poseStack.last()

    val bufferSource: MultiBufferSource.BufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
    val vc = bufferSource.getBuffer(RenderTypes.linesTranslucent())
    val width = lineWidth.coerceAtLeast(1f)

    vc.addVertex(pose, x1.toFloat(), y1.toFloat(), z1.toFloat())
        .setColor(r, g, b, a)
        .setNormal(nx, ny, nz)
        .setLineWidth(width)
    vc.addVertex(pose, x2.toFloat(), y2.toFloat(), z2.toFloat())
        .setColor(r, g, b, a)
        .setNormal(nx, ny, nz)
        .setLineWidth(width)

    bufferSource.endBatch(RenderTypes.linesTranslucent())
    return true
}
