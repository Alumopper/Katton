import top.katton.api.WorldRenderLayer
import top.katton.api.clientPos
import top.katton.api.clientTell
import top.katton.api.drawBillboard3D
import top.katton.api.drawLine3D
import top.katton.api.once
import top.katton.api.registerWorldRenderer
import top.katton.api.unregisterWorldRenderer

once("katton:client_world_render_test") {
    unregisterWorldRenderer("katton:test:world")

    registerWorldRenderer("katton:test:world", WorldRenderLayer.NORMAL, 0) { ctx ->
        val pos = clientPos() ?: return@registerWorldRenderer

        val x = pos.x
        val y = pos.y + 1.6
        val z = pos.z

        // X axis — red
        drawLine3D(
            ctx,
            x - 1.0, y, z,
            x + 1.0, y, z,
            argbColor = 0xFFFF5050.toInt(),
            lineWidth = 2.0f
        )

        // Y axis — green
        drawLine3D(
            ctx,
            x, y - 1.0, z,
            x, y + 1.0, z,
            argbColor = 0xFF50FF9E.toInt(),
            lineWidth = 2.0f
        )

        // Z axis — blue
        drawLine3D(
            ctx,
            x, y, z - 1.0,
            x, y, z + 1.0,
            argbColor = 0xFF5BB4FF.toInt(),
            lineWidth = 2.0f
        )

        // Camera-facing quad above head
        drawBillboard3D(
            ctx,
            x = x,
            y = y + 0.6,
            z = z,
            argbColor = 0xAAFFFF66.toInt(),
            size = 0.5f
        )
    }

    clientTell("[Katton] World render test script loaded")
}
