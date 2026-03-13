package top.katton.platform

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import org.joml.Vector3f

object FabricClientApiBridge : ClientApiHooks.Bridge {
    private val mc: Minecraft
        get() = Minecraft.getInstance()

    private val player get() = mc.player
    private val level get() = mc.level
    private val gui get() = mc.gui

    override fun isClientRuntime(): Boolean = true

    override fun rawClient(): Any = mc

    override fun rawPlayer(): Any? = player

    override fun rawLevel(): Any? = level

    override fun runOnClient(action: Runnable): Boolean {
        mc.execute(action)
        return true
    }

    override fun isPaused(): Boolean = mc.isPaused

    override fun isInWorld(): Boolean = level != null

    override fun playerX(): Double? = player?.x

    override fun playerY(): Double? = player?.y

    override fun playerZ(): Double? = player?.z

    override fun playerYaw(): Float? = player?.yRot

    override fun playerPitch(): Float? = player?.xRot

    override fun dimensionId(): String? = level?.dimension()?.registry()?.toString()

    override fun gameTime(): Long? = level?.gameTime

    override fun tell(message: Component): Boolean {
        val p = player ?: return false
        p.displayClientMessage(message, false)
        return true
    }

    override fun actionBar(message: Component): Boolean {
        val p = player ?: return false
        p.displayClientMessage(message, true)
        return true
    }

    override fun overlay(message: Component, tinted: Boolean): Boolean {
        gui.setOverlayMessage(message, tinted)
        return true
    }

    override fun clearOverlay(): Boolean {
        gui.setOverlayMessage(Component.empty(), false)
        return true
    }

    override fun nowPlaying(message: Component): Boolean {
        gui.setNowPlaying(message)
        return true
    }

    override fun playSound(soundId: Identifier, volume: Float, pitch: Float): Boolean {
        val p = player ?: return false
        p.playSound(SoundEvent.createVariableRangeEvent(soundId), volume, pitch)
        return true
    }

    override fun title(message: Component): Boolean {
        gui.setTitle(message)
        return true
    }

    override fun subtitle(message: Component): Boolean {
        gui.setSubtitle(message)
        return true
    }

    override fun titleTimes(fadeInTicks: Int, stayTicks: Int, fadeOutTicks: Int): Boolean {
        gui.setTimes(fadeInTicks, stayTicks, fadeOutTicks)
        return true
    }

    override fun clearTitle(): Boolean {
        gui.clearTitles()
        return true
    }

    override fun toast(title: Component, description: Component): Boolean {
        mc.toastManager.addToast(SystemToast(SystemToast.SystemToastId(), title, description))
        return true
    }

    override fun fps(): Int = Minecraft.getInstance().fps

    override fun windowFocused(): Boolean = mc.isWindowActive

    override fun screenName(): String? = mc.screen?.javaClass?.simpleName

    override fun inMenu(): Boolean = mc.screen != null

    override fun chatOpen(): Boolean = mc.screen is ChatScreen

    override fun hudDrawText(graphics: Any?, text: Component, x: Int, y: Int, color: Int, shadow: Boolean): Boolean {
        val g = graphics as? GuiGraphics ?: return false
        g.drawString(mc.font, text, x, y, color, shadow)
        return true
    }

    override fun hudFillRect(graphics: Any?, x1: Int, y1: Int, x2: Int, y2: Int, color: Int): Boolean {
        val g = graphics as? GuiGraphics ?: return false
        g.fill(x1, y1, x2, y2, color)
        return true
    }

    override fun hudBlitTexture(
        graphics: Any?,
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
        textureWidth: Int,
        textureHeight: Int
    ): Boolean {
        val g = graphics as? GuiGraphics ?: return false
        g.blit(texture, x, y, width, height, u, v, textureWidth.toFloat(), textureHeight.toFloat())
        return true
    }

    override fun worldDrawLine(
        x1: Double,
        y1: Double,
        z1: Double,
        x2: Double,
        y2: Double,
        z2: Double,
        argbColor: Int,
        size: Float,
        segments: Int
    ): Boolean {
        val l = level ?: return false
        val seg = segments.coerceAtLeast(1)
        val particle = DustParticleOptions(argbColor, size.coerceAtLeast(0.01f))

        for (i in 0..seg) {
            val t = i.toDouble() / seg.toDouble()
            val x = x1 + (x2 - x1) * t
            val y = y1 + (y2 - y1) * t
            val z = z1 + (z2 - z1) * t
            l.addParticle(particle, x, y, z, 0.0, 0.0, 0.0)
        }
        return true
    }

    override fun worldDrawBillboard(
        x: Double,
        y: Double,
        z: Double,
        argbColor: Int,
        size: Float
    ): Boolean {
        val l = level ?: return false
        val particle = DustParticleOptions(argbColor, size.coerceAtLeast(0.01f))
        l.addParticle(particle, x, y, z, 0.0, 0.0, 0.0)
        return true
    }

    override fun worldDrawMeshLine(
        viewMatrix: Any,
        projMatrix: Any,
        camera: Any,
        x1: Double,
        y1: Double,
        z1: Double,
        x2: Double,
        y2: Double,
        z2: Double,
        argbColor: Int,
        lineWidth: Float
    ): Boolean {
        val cam = camera as? Camera ?: return false
        val camPos = cam.position()

        val a = ((argbColor ushr 24) and 0xFF)
        val r = ((argbColor ushr 16) and 0xFF)
        val g = ((argbColor ushr 8) and 0xFF)
        val b = (argbColor and 0xFF)

        // Direction vector used as per-vertex normal for line rendering.
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

        val bufferSource: MultiBufferSource.BufferSource = mc.renderBuffers().bufferSource()
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

    override fun worldDrawMeshQuad(
        viewMatrix: Any,
        projMatrix: Any,
        camera: Any,
        x: Double,
        y: Double,
        z: Double,
        argbColor: Int,
        size: Float
    ): Boolean {
        val cam = camera as? Camera ?: return false
        val camPos = cam.position()

        val a = ((argbColor ushr 24) and 0xFF)
        val r = ((argbColor ushr 16) and 0xFF)
        val g = ((argbColor ushr 8) and 0xFF)
        val b = (argbColor and 0xFF)
        val h = size / 2f

        // Use camera basis vectors directly for a stable billboard orientation.
        val right = Vector3f(cam.leftVector()).mul(-1f)
        val up = Vector3f(cam.upVector())

        val poseStack = PoseStack()
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z)
        val pose = poseStack.last()

        // Camera-relative center
        val cx = (x - camPos.x).toFloat()
        val cy = (y - camPos.y).toFloat()
        val cz = (z - camPos.z).toFloat()

        // Four corners: center ± right*h ± up*h
        val v0x = cx - right.x * h - up.x * h
        val v0y = cy - right.y * h - up.y * h
        val v0z = cz - right.z * h - up.z * h
        val v1x = cx + right.x * h - up.x * h
        val v1y = cy + right.y * h - up.y * h
        val v1z = cz + right.z * h - up.z * h
        val v2x = cx + right.x * h + up.x * h
        val v2y = cy + right.y * h + up.y * h
        val v2z = cz + right.z * h + up.z * h
        val v3x = cx - right.x * h + up.x * h
        val v3y = cy - right.y * h + up.y * h
        val v3z = cz - right.z * h + up.z * h

        val bufferSource: MultiBufferSource.BufferSource = mc.renderBuffers().bufferSource()
        val vc = bufferSource.getBuffer(RenderTypes.debugQuads())
        vc.addVertex(pose, v0x, v0y, v0z).setColor(r, g, b, a)
        vc.addVertex(pose, v1x, v1y, v1z).setColor(r, g, b, a)
        vc.addVertex(pose, v2x, v2y, v2z).setColor(r, g, b, a)
        vc.addVertex(pose, v3x, v3y, v3z).setColor(r, g, b, a)
        bufferSource.endBatch(RenderTypes.debugQuads())
        return true
    }
}
