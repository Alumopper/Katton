package top.katton.compat

import net.minecraft.client.renderer.StagedVertexBuffer
import net.minecraft.client.renderer.rendertype.RenderType
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

    val renderType = RenderTypes.linesTranslucent()
    val staged = StagedVertexBuffer({ "Katton line renderer" }, RenderType.SMALL_BUFFER_SIZE)
    try {
        val draw = staged.appendDraw(renderType.format(), renderType.primitiveTopology())
        val vc = staged.getVertexBuilder(draw)
        val width = lineWidth.coerceAtLeast(1f)

        vc.addVertex((x1 - camPos.x).toFloat(), (y1 - camPos.y).toFloat(), (z1 - camPos.z).toFloat())
            .setColor(r, g, b, a)
            .setNormal(nx, ny, nz)
            .setLineWidth(width)
        vc.addVertex((x2 - camPos.x).toFloat(), (y2 - camPos.y).toFloat(), (z2 - camPos.z).toFloat())
            .setColor(r, g, b, a)
            .setNormal(nx, ny, nz)
            .setLineWidth(width)

        staged.upload()
        val executeInfo = staged.getExecuteInfo(draw) ?: return false
        renderType.prepare().drawFromBuffer(executeInfo)
        staged.endDraw()
        staged.endFrame()
        return true
    } finally {
        staged.close()
    }
}
