@file:Suppress("unused")

package top.katton.api

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.joml.Vector3f
import top.katton.util.Event
import java.util.concurrent.ConcurrentHashMap

/**
 * Client rendering API for custom HUD and world rendering.
 *
 * This module provides functionality for registering custom renderers that draw
 * on the HUD (screen-space) or in the world (3D space). Renderers can be organized
 * by layer and priority for proper ordering.
 *
 * Key features:
 * - HUD rendering with text, rectangles, and textures
 * - World-space rendering with lines and billboards
 * - Layer-based ordering (BACKGROUND, NORMAL, FOREGROUND for HUD; EARLY, NORMAL, LATE for world)
 * - Priority-based ordering within layers
 */

/**
 * Screen-space render callback context.
 *
 * Contains the graphics context and timing information for HUD rendering.
 *
 * @property graphics The runtime GUI graphics object from Minecraft (GuiGraphics)
 * @property tickDelta The partial tick time for smooth animations
 */
data class HudRenderContext(
    val graphics: GuiGraphics,
    val tickDelta: Float
)

/**
 * World-space render callback context.
 *
 * Contains matrix and camera information for 3D world rendering.
 *
 * @property viewMatrix The current view transformation matrix
 * @property projectionMatrix The current projection matrix
 * @property camera The current camera instance (may be null)
 * @property tickDelta The partial tick time for smooth animations
 */
data class WorldRenderContext(
    val viewMatrix: Any,
    val projectionMatrix: Any,
    val camera: Camera?,
    val tickDelta: Float
)

/**
 * Ordering bucket for HUD render callbacks.
 *
 * Renderers are processed in order: BACKGROUND -> NORMAL -> FOREGROUND
 */
enum class HudRenderLayer {
    BACKGROUND,
    NORMAL,
    FOREGROUND
}

/**
 * Ordering bucket for world render callbacks.
 *
 * Renderers are processed in order: EARLY -> NORMAL -> LATE
 */
enum class WorldRenderLayer {
    EARLY,
    NORMAL,
    LATE
}

private data class HudRendererEntry(
    val owner: String?,
    val layer: HudRenderLayer,
    val priority: Int,
    val render: (HudRenderContext) -> Unit
)

private data class WorldRendererEntry(
    val owner: String?,
    val layer: WorldRenderLayer,
    val priority: Int,
    val render: (WorldRenderContext) -> Unit
)

private val hudRenderers = ConcurrentHashMap<String, HudRendererEntry>()
private val worldRenderers = ConcurrentHashMap<String, WorldRendererEntry>()
private val mc = Minecraft.getInstance()
private val player get() = mc.player
private val level get() = mc.level
private val gui get() = mc.gui

private fun asComponent(message: Any): Component =
    message as? Component ?: Component.literal(message.toString())

/**
 * Register (or replace) a HUD renderer by [id].
 *
 * The renderer will be called each frame during HUD rendering.
 * Uses NORMAL layer with priority 0.
 *
 * @param id Unique identifier for this renderer
 * @param render The render callback receiving HudRenderContext
 */
fun registerHudRenderer(id: String, render: (HudRenderContext) -> Unit) {
    registerHudRenderer(id, HudRenderLayer.NORMAL, 0, render)
}

/**
 * Register (or replace) a HUD renderer by [id], with [layer] and [priority].
 *
 * Lower priority values are rendered earlier within the same layer.
 * Layers are rendered in order: BACKGROUND -> NORMAL -> FOREGROUND
 *
 * @param id Unique identifier for this renderer
 * @param layer The render layer determining draw order
 * @param priority Priority within the layer (lower = earlier)
 * @param render The render callback receiving HudRenderContext
 */
fun registerHudRenderer(
    id: String,
    layer: HudRenderLayer,
    priority: Int = 0,
    render: (HudRenderContext) -> Unit
) {
    hudRenderers[id] = HudRendererEntry(Event.currentScriptOwner(), layer, priority, render)
}

/**
 * Remove a HUD renderer by [id].
 *
 * @param id The identifier of the renderer to remove
 * @return true if the renderer was found and removed, false otherwise
 */
fun unregisterHudRenderer(id: String): Boolean = hudRenderers.remove(id) != null

/**
 * Register (or replace) a world-space renderer by [id].
 *
 * The renderer will be called each frame during world rendering.
 * Uses NORMAL layer with priority 0.
 *
 * @param id Unique identifier for this renderer
 * @param render The render callback receiving WorldRenderContext
 */
fun registerWorldRenderer(id: String, render: (WorldRenderContext) -> Unit) {
    registerWorldRenderer(id, WorldRenderLayer.NORMAL, 0, render)
}

/**
 * Register (or replace) a world renderer by [id], with [layer] and [priority].
 *
 * Lower priority values are rendered earlier within the same layer.
 * Layers are rendered in order: EARLY -> NORMAL -> LATE
 *
 * @param id Unique identifier for this renderer
 * @param layer The render layer determining draw order
 * @param priority Priority within the layer (lower = earlier)
 * @param render The render callback receiving WorldRenderContext
 */
fun registerWorldRenderer(
    id: String,
    layer: WorldRenderLayer,
    priority: Int = 0,
    render: (WorldRenderContext) -> Unit
) {
    worldRenderers[id] = WorldRendererEntry(Event.currentScriptOwner(), layer, priority, render)
}

/**
 * Remove a world-space renderer by [id].
 *
 * @param id The identifier of the renderer to remove
 * @return true if the renderer was found and removed, false otherwise
 */
fun unregisterWorldRenderer(id: String): Boolean = worldRenderers.remove(id) != null

/**
 * Clears all client render callbacks (both HUD and world renderers).
 *
 * Useful for cleanup during script reload or when resetting state.
 */
fun clearClientRenderers() {
    hudRenderers.clear()
    worldRenderers.clear()
}

/**
 * Internal dispatcher: called by platform render hooks to invoke all HUD renderers.
 *
 * @param graphics The GUI graphics context
 * @param tickDelta The partial tick time
 */
@JvmName("dispatchHudRender")
fun dispatchHudRender(graphics: GuiGraphics, tickDelta: Float) {
    val ctx = HudRenderContext(graphics, tickDelta)
    val ordered = hudRenderers.values
        .sortedWith(compareBy({ it.layer.ordinal }, { it.priority }))
    for (entry in ordered) {
        runCatching {
            Event.withScriptOwner(entry.owner) {
                entry.render(ctx)
            }
        }.onFailure {
            LOGGER.warn("HUD renderer callback failed", it)
        }
    }
}

/**
 * Internal dispatcher: called by platform render hooks to invoke all world renderers.
 *
 * @param viewMatrix The view transformation matrix
 * @param projectionMatrix The projection matrix
 * @param camera The camera instance
 * @param tickDelta The partial tick time
 */
@JvmName("dispatchWorldRender")
fun dispatchWorldRender(viewMatrix: Any, projectionMatrix: Any, camera: Camera?, tickDelta: Float) {
    val ctx = WorldRenderContext(viewMatrix, projectionMatrix, camera, tickDelta)
    val ordered = worldRenderers.values
        .sortedWith(compareBy({ it.layer.ordinal }, { it.priority }))
    for (entry in ordered) {
        runCatching {
            Event.withScriptOwner(entry.owner) {
                entry.render(ctx)
            }
        }.onFailure {
            LOGGER.warn("World renderer callback failed", it)
        }
    }
}

/**
 * Draws text on HUD using current [HudRenderContext].
 *
 * @param ctx The HUD render context
 * @param message The text to draw (will be converted to Component if not already)
 * @param x The X position in screen coordinates
 * @param y The Y position in screen coordinates
 * @param color The text color in ARGB format (default: white)
 * @param shadow Whether to draw a drop shadow (default: true)
 * @return true if drawing succeeded, false otherwise
 */
fun drawHudText(
    ctx: HudRenderContext,
    message: Component,
    x: Int,
    y: Int,
    color: Int = 0xFFFFFF,
    shadow: Boolean = true
) {
    ctx.graphics.drawString(mc.font, message, x, y, color, shadow)
}

/**
 * Draws a solid rectangle on HUD using current [HudRenderContext].
 *
 * @param ctx The HUD render context
 * @param x1 The left edge X coordinate
 * @param y1 The top edge Y coordinate
 * @param x2 The right edge X coordinate
 * @param y2 The bottom edge Y coordinate
 * @param color The fill color in ARGB format
 * @return true if drawing succeeded, false otherwise
 */
fun fillHudRect(
    ctx: HudRenderContext,
    x1: Int,
    y1: Int,
    x2: Int,
    y2: Int,
    color: Int
) {
    ctx.graphics.fill(x1, y1, x2, y2, color)
}

/**
 * Draws a texture region on HUD using current [HudRenderContext].
 *
 * @param ctx The HUD render context
 * @param texture The texture identifier string
 * @param x The X position on screen
 * @param y The Y position on screen
 * @param width The width to draw
 * @param height The height to draw
 * @param u The U coordinate in the texture (default: 0)
 * @param v The V coordinate in the texture (default: 0)
 * @param textureWidth The total texture width (default: same as width)
 * @param textureHeight The total texture height (default: same as height)
 * @return true if drawing succeeded, false if texture ID was invalid
 */
fun drawHudTexture(
    ctx: HudRenderContext,
    texture: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    u: Float = 0f,
    v: Float = 0f,
    textureWidth: Int = width,
    textureHeight: Int = height
): Boolean {
    val id = runCatching { Identifier.parse(texture) }.getOrNull() ?: return false
    ctx.graphics.blit(id, x, y, width, height, u, v, textureWidth.toFloat(), textureHeight.toFloat())
    return true
}

/**
 * Draw a 3D line using world coordinates and ARGB color.
 *
 * Uses real GPU mesh rendering via VertexConsumer+RenderType.
 *
 * @param ctx The world render context
 * @param x1 Start X coordinate in world space
 * @param y1 Start Y coordinate in world space
 * @param z1 Start Z coordinate in world space
 * @param x2 End X coordinate in world space
 * @param y2 End Y coordinate in world space
 * @param z2 End Z coordinate in world space
 * @param argbColor The line color in ARGB format
 * @param lineWidth The width of the line (default: 1.0)
 * @return true if drawing succeeded, false otherwise
 */
fun drawLine3D(
    ctx: WorldRenderContext,
    x1: Double,
    y1: Double,
    z1: Double,
    x2: Double,
    y2: Double,
    z2: Double,
    argbColor: Int,
    lineWidth: Float = 1.0f
): Boolean {
    val cam = ctx.camera ?: return false
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

/**
 * Draw a colored quad billboard at world position.
 *
 * Uses real GPU mesh rendering via VertexConsumer+RenderType.
 * Billboards always face the camera.
 *
 * @param ctx The world render context
 * @param x The X coordinate in world space
 * @param y The Y coordinate in world space
 * @param z The Z coordinate in world space
 * @param argbColor The color in ARGB format
 * @param size The size of the billboard (default: 1.0)
 * @return true if drawing succeeded, false otherwise
 */
fun drawBillboard3D(
    ctx: WorldRenderContext,
    x: Double,
    y: Double,
    z: Double,
    argbColor: Int,
    size: Float = 1.0f
): Boolean {
    val cam = ctx.camera ?: return false
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
