import top.katton.api.HudRenderLayer
import top.katton.api.clientFps
import top.katton.api.clientPos
import top.katton.api.clientScreenName
import top.katton.api.clientTell
import top.katton.api.drawHudText
import top.katton.api.drawHudTexture
import top.katton.api.fillHudRect
import top.katton.api.once
import top.katton.api.registerHudRenderer
import top.katton.api.unregisterHudRenderer

fun hudRenderTestMain() {

    unregisterHudRenderer("katton:test:hud")

    registerHudRenderer("katton:test:hud", HudRenderLayer.FOREGROUND, 20) { ctx ->
        fillHudRect(ctx, 6, 6, 258, 64, 0xAA000000.toInt())
        fillHudRect(ctx, 8, 8, 256, 62, 0x66002244)

        drawHudText(ctx, "Katton HUD Render Test", 14, 14, 0xFFE8F1FF.toInt(), true)
        drawHudText(ctx, "FPS: ${clientFps() ?: -1}", 14, 28, 0xFF9BD5FF.toInt(), false)
        drawHudText(ctx, "Screen: ${clientScreenName() ?: "In-World"}", 14, 40, 0xFFB5FFC5.toInt(), false)

        val p = clientPos()
        if (p != null) {
            drawHudText(ctx, "Pos: %.2f, %.2f, %.2f".format(p.x, p.y, p.z), 14, 52, 0xFFFFD38A.toInt(), false)
        }

        // 9x9 heart icon area from vanilla icons atlas.
        drawHudTexture(
            ctx = ctx,
            texture = "minecraft:textures/gui/icons.png",
            x = 232,
            y = 16,
            width = 9,
            height = 9,
            u = 16f,
            v = 0f,
            textureWidth = 256,
            textureHeight = 256
        )
    }
    clientTell("[Katton] HUD render test script loaded")
}

val hudRenderTestEntryPoint = hudRenderTestMain()