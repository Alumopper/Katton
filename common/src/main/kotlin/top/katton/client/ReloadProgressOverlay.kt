package top.katton.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

data class ReloadProgressSnapshot(
    val active: Boolean,
    val message: String,
    val progress: Float,
    val visibleUntilMillis: Long
)

object ReloadProgressState {
    private val state = AtomicReference(
        ReloadProgressSnapshot(
            active = false,
            message = "",
            progress = 0f,
            visibleUntilMillis = 0L
        )
    )

    @JvmStatic
    fun begin(message: String, progress: Float = 0f) {
        val now = System.currentTimeMillis()
        state.set(
            ReloadProgressSnapshot(
                active = true,
                message = message,
                progress = progress.coerceIn(0f, 1f),
                visibleUntilMillis = now + 5000L
            )
        )
    }

    @JvmStatic
    fun update(message: String, progress: Float) {
        val now = System.currentTimeMillis()
        state.set(
            ReloadProgressSnapshot(
                active = true,
                message = message,
                progress = progress.coerceIn(0f, 1f),
                visibleUntilMillis = now + 5000L
            )
        )
    }

    @JvmStatic
    fun finish(message: String = "Done") {
        val now = System.currentTimeMillis()
        state.set(
            ReloadProgressSnapshot(
                active = false,
                message = message,
                progress = 1f,
                visibleUntilMillis = now + 1500L
            )
        )
    }

    @JvmStatic
    fun hide() {
        state.set(
            ReloadProgressSnapshot(
                active = false,
                message = "",
                progress = 0f,
                visibleUntilMillis = 0L
            )
        )
    }

    @JvmStatic
    fun snapshot(): ReloadProgressSnapshot = state.get()
}

object ReloadProgressOverlay {
    private const val PANEL_WIDTH = 220
    private const val PANEL_HEIGHT = 28
    private const val BAR_HEIGHT = 6

    private fun shouldRender(snapshot: ReloadProgressSnapshot): Boolean {
        if (snapshot.active) return true
        return System.currentTimeMillis() <= snapshot.visibleUntilMillis
    }

    private fun renderCore(
        width: Int,
        drawFill: (Int, Int, Int, Int, Int) -> Unit,
        drawText: (Component, Int, Int, Int) -> Unit
    ) {
        val snapshot = ReloadProgressState.snapshot()
        if (!shouldRender(snapshot)) return

        val left = (width - PANEL_WIDTH) / 2
        val top = 8
        val right = left + PANEL_WIDTH
        val bottom = top + PANEL_HEIGHT

        drawFill(left, top, right, bottom, 0xAA000000.toInt())
        drawFill(left + 1, top + 1, right - 1, bottom - 1, 0x66000000)

        val barLeft = left + 8
        val barRight = right - 8
        val barTop = bottom - BAR_HEIGHT - 6
        val barBottom = barTop + BAR_HEIGHT
        drawFill(barLeft, barTop, barRight, barBottom, 0x66333333)

        val progressWidth = ((barRight - barLeft) * snapshot.progress.coerceIn(0f, 1f)).roundToInt()
        if (progressWidth > 0) {
            drawFill(barLeft, barTop, barLeft + progressWidth, barBottom, 0xFF5DBB63.toInt())
        }

        val percent = (snapshot.progress.coerceIn(0f, 1f) * 100f).roundToInt()
        drawText(Component.literal("${snapshot.message} (${percent}%)"), left + 8, top + 7, 0xFFFFFFFF.toInt())
    }

    @JvmStatic
    fun renderExtractor(graphics: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        renderCore(
            width = mc.window.guiScaledWidth,
            drawFill = { x1, y1, x2, y2, color -> graphics.fill(x1, y1, x2, y2, color) },
            drawText = { text, x, y, color -> graphics.text(mc.font, text, x, y, color, true) }
        )
    }
}
