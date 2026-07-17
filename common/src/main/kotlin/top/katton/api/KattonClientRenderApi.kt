@file:Suppress("unused")

package top.katton.api

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import top.katton.compat.drawLine3DCompat
import top.katton.util.ScriptExecutionContext
import java.util.concurrent.ConcurrentHashMap

/**
 * %en
 * Client rendering API for custom HUD and world rendering.
 *
 * This module provides functionality for registering custom renderers that draw
 * on the HUD (screen-space) or in the world (3D space). Renderers can be organized
 * by layer and priority.
 *
 * %zh
 * 用于自定义 HUD 和世界渲染的客户端渲染 API。
 *
 * 本模块提供注册自定义渲染器的能力，可在 HUD（屏幕空间）或世界（3D 空间）中绘制内容。
 * 渲染器可以按图层和优先级组织。
 */

/**
 * %en
 * Screen-space render callback context.
 *
 * Contains the graphics context and timing information for HUD rendering.
 *
 * %zh
 * 屏幕空间渲染回调上下文。
 *
 * 包含 HUD 渲染所需的图形上下文和时间信息。
 * @property graphics
 * %en The runtime GUI graphics object from Minecraft (GuiGraphics)
 * %zh Minecraft 在运行时提供的 GUI 图形对象（GuiGraphics）。
 * @property tickDelta
 * %en The partial tick time for smooth animations
 * %zh 用于平滑动画的局部 tick 时间。
 */
data class HudRenderContext(
    val graphics: GuiGraphicsExtractor,
    val tickDelta: Float
)

/**
 * %en
 * World-space render callback context.
 *
 * Contains matrix and camera information for 3D world rendering.
 *
 * %zh
 * 世界空间渲染回调上下文。
 *
 * 包含 3D 世界渲染所需的矩阵和相机信息。
 * @property camera
 * %en The current camera instance (may be null)
 * %zh 当前相机实例，可能为 null。
 * @property tickDelta
 * %en The partial tick time for smooth animations
 * %zh 用于平滑动画的局部 tick 时间。
 */
data class WorldRenderContext(
    val camera: CameraRenderState?,
    val tickDelta: Float
)

/**
 * %en
 * Ordering bucket for HUD render callbacks.
 *
 * Renderers are processed in order: BACKGROUND -> NORMAL -> FOREGROUND
 *
 * %zh
 * HUD 渲染回调的排序分组。
 *
 * 渲染器按 BACKGROUND -> NORMAL -> FOREGROUND 的顺序处理。
 */
enum class HudRenderLayer {
    BACKGROUND,
    NORMAL,
    FOREGROUND
}

/**
 * %en
 * Ordering bucket for world render callbacks.
 *
 * Renderers are processed in order: EARLY -> NORMAL -> LATE
 *
 * %zh
 * 世界渲染回调的排序分组。
 *
 * 渲染器按 EARLY -> NORMAL -> LATE 的顺序处理。
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


private fun asComponent(text: Any?): Component = when (text) {
    is Component -> text
    else -> Component.literal(text.toString())
}

private fun asComponentNullable(text: Any?): Component? = when (text) {
    null -> null
    is Component -> text
    else -> Component.literal(text.toString())
}

/**
 * %en
 * Register (or replace) a HUD renderer by [id].
 *
 * The renderer will be called each frame during HUD rendering.
 * Uses NORMAL layer with priority 0.
 *
 * %zh
 * 通过 [id] 注册或替换一个 HUD 渲染器。
 *
 * 该渲染器会在每帧 HUD 渲染期间调用。
 * 默认使用 NORMAL 图层和优先级 0。
 * @param id
 * %en Unique identifier for this renderer
 * %zh 此渲染器的唯一标识符。
 * @param render
 * %en The render callback receiving HudRenderContext
 * %zh 接收 HudRenderContext 的渲染回调。
 */
fun registerHudRenderer(id: String, render: (HudRenderContext) -> Unit) {
    registerHudRenderer(id, HudRenderLayer.NORMAL, 0, render)
}

/**
 * %en
 * Register (or replace) a HUD renderer by [id], with [layer] and [priority].
 *
 * Lower priority values are rendered earlier within the same layer.
 * Layers are rendered in order: BACKGROUND -> NORMAL -> FOREGROUND.
 *
 * %zh
 * 通过 [id] 注册或替换一个 HUD 渲染器，并指定 [layer] 和 [priority]。
 *
 * 同一图层内，优先级数值越低越早渲染。
 * 图层按 BACKGROUND -> NORMAL -> FOREGROUND 的顺序渲染。
 * @param id
 * %en Unique identifier for this renderer
 * %zh 此渲染器的唯一标识符。
 * @param layer
 * %en The render layer determining draw order
 * %zh 决定绘制顺序的渲染图层。
 * @param priority
 * %en Priority within the layer (lower = earlier)
 * %zh 图层内优先级，数值越低越早渲染。
 * @param render
 * %en The render callback receiving HudRenderContext
 * %zh 接收 HudRenderContext 的渲染回调。
 */
fun registerHudRenderer(
    id: String,
    layer: HudRenderLayer,
    priority: Int = 0,
    render: (HudRenderContext) -> Unit
) {
    hudRenderers[id] = HudRendererEntry(ScriptExecutionContext.currentScriptOwner(), layer, priority, render)
}

/**
 * %en
 * Remove a HUD renderer by [id].
 *
 * %zh
 * 按 [id] 移除一个 HUD 渲染器。
 * @param id
 * %en The identifier of the renderer to remove
 * %zh 要移除的渲染器标识符。
 * @return
 * %en if the renderer was found and removed, false otherwise
 * %zh 如果找到并移除了渲染器则返回 true，否则返回 false。
 */
fun unregisterHudRenderer(id: String): Boolean = hudRenderers.remove(id) != null

/**
 * %en
 * Register (or replace) a world-space renderer by [id].
 *
 * The renderer will be called each frame during world rendering.
 * Uses NORMAL layer with priority 0.
 *
 * %zh
 * 通过 [id] 注册或替换一个世界空间渲染器。
 *
 * 该渲染器会在每帧世界渲染期间调用。
 * 默认使用 NORMAL 图层和优先级 0。
 * @param id
 * %en Unique identifier for this renderer
 * %zh 此渲染器的唯一标识符。
 * @param render
 * %en The render callback receiving WorldRenderContext
 * %zh 接收 WorldRenderContext 的渲染回调。
 */
fun registerWorldRenderer(id: String, render: (WorldRenderContext) -> Unit) {
    registerWorldRenderer(id, WorldRenderLayer.NORMAL, 0, render)
}

/**
 * %en
 * Register (or replace) a world renderer by [id], with [layer] and [priority].
 *
 * Lower priority values are rendered earlier within the same layer.
 * Layers are rendered in order: EARLY -> NORMAL -> LATE.
 *
 * %zh
 * 通过 [id] 注册或替换一个世界渲染器，并指定 [layer] 和 [priority]。
 *
 * 同一图层内，优先级数值越低越早渲染。
 * 图层按 EARLY -> NORMAL -> LATE 的顺序渲染。
 * @param id
 * %en Unique identifier for this renderer
 * %zh 此渲染器的唯一标识符。
 * @param layer
 * %en The render layer determining draw order
 * %zh 决定绘制顺序的渲染图层。
 * @param priority
 * %en Priority within the layer (lower = earlier)
 * %zh 图层内优先级，数值越低越早渲染。
 * @param render
 * %en The render callback receiving WorldRenderContext
 * %zh 接收 WorldRenderContext 的渲染回调。
 */
fun registerWorldRenderer(
    id: String,
    layer: WorldRenderLayer,
    priority: Int = 0,
    render: (WorldRenderContext) -> Unit
) {
    worldRenderers[id] = WorldRendererEntry(ScriptExecutionContext.currentScriptOwner(), layer, priority, render)
}

/**
 * %en
 * Remove a world-space renderer by [id].
 *
 * %zh
 * 按 [id] 移除一个世界空间渲染器。
 * @param id
 * %en The identifier of the renderer to remove
 * %zh 要移除的渲染器标识符。
 * @return
 * %en if the renderer was found and removed, false otherwise
 * %zh 如果找到并移除了渲染器则返回 true，否则返回 false。
 */
fun unregisterWorldRenderer(id: String): Boolean = worldRenderers.remove(id) != null

/**
 * %en
 * Clears all client render callbacks (both HUD and world renderers).
 *
 * Useful for cleanup during script reload or when resetting state.
 *
 * %zh
 * 清除所有客户端渲染回调，包括 HUD 和世界渲染器。
 *
 * 可用于脚本重载期间的清理，或重置渲染状态。
 */
fun clearClientRenderers() {
    hudRenderers.clear()
    worldRenderers.clear()
}

/**
 * %en
 * Internal dispatcher: called by platform render hooks to invoke all HUD renderers.
 *
 * %zh
 * 内部分发器：由平台渲染钩子调用，用于执行所有 HUD 渲染器。
 * @param graphics
 * %en The GUI graphics context
 * %zh GUI 图形上下文。
 * @param tickDelta
 * %en The partial tick time
 * %zh 局部 tick 时间。
 */
@JvmName("dispatchHudRender")
fun dispatchHudRender(graphics: GuiGraphicsExtractor, tickDelta: Float) {
    val ctx = HudRenderContext(graphics, tickDelta)
    val ordered = hudRenderers.values
        .sortedWith(compareBy({ it.layer.ordinal }, { it.priority }))
    for (entry in ordered) {
        runCatching {
            ScriptExecutionContext.withOwner(entry.owner) {
                entry.render(ctx)
            }
        }.onFailure {
            LOGGER.warn("HUD renderer callback failed", it)
        }
    }
}

/**
 * %en
 * Internal dispatcher: called by platform render hooks to invoke all world renderers.
 *
 * %zh
 * 内部分发器：由平台渲染钩子调用，用于执行所有世界渲染器。
 * @param camera
 * %en The camera instance
 * %zh 相机实例。
 * @param tickDelta
 * %en The partial tick time
 * %zh 局部 tick 时间。
 */
@JvmName("dispatchWorldRender")
fun dispatchWorldRender(camera: CameraRenderState?, tickDelta: Float) {
    val ctx = WorldRenderContext(camera, tickDelta)
    val ordered = worldRenderers.values
        .sortedWith(compareBy({ it.layer.ordinal }, { it.priority }))
    for (entry in ordered) {
        runCatching {
            ScriptExecutionContext.withOwner(entry.owner) {
                entry.render(ctx)
            }
        }.onFailure {
            LOGGER.warn("World renderer callback failed", it)
        }
    }
}

/**
 * %en
 * Draws text on HUD using current [HudRenderContext].
 *
 * %zh
 * 使用当前 [HudRenderContext] 在 HUD 上绘制文本。
 * @param ctx
 * %en The HUD render context
 * %zh HUD 渲染上下文。
 * @param message
 * %en The text to draw (will be converted to Component if not already)
 * %zh 要绘制的文本；如果不是 Component，会自动转换。
 * @param x
 * %en The X position in screen coordinates
 * %zh 屏幕坐标中的 X 位置。
 * @param y
 * %en The Y position in screen coordinates
 * %zh 屏幕坐标中的 Y 位置。
 * @param color
 * %en The text color in ARGB format (default: white)
 * %zh ARGB 格式的文本颜色，默认为白色。
 * @param shadow
 * %en Whether to draw a drop shadow (default: true)
 * %zh 是否绘制阴影，默认 true。
 * @return
 * %en if drawing succeeded, false otherwise
 * %zh 如果绘制成功则返回 true，否则返回 false。
 */
fun drawHudText(
    ctx: HudRenderContext,
    message: Any?,
    x: Int,
    y: Int,
    color: Int = 0xFFFFFF,
    shadow: Boolean = true
) {
    ctx.graphics.text(mc.font, asComponent(message), x, y, color, shadow)
}

/**
 * %en
 * Draws a solid rectangle on HUD using current [HudRenderContext].
 *
 * %zh
 * 使用当前 [HudRenderContext] 在 HUD 上绘制一个实心矩形。
 * @param ctx
 * %en The HUD render context
 * %zh HUD 渲染上下文。
 * @param x1
 * %en The left edge X coordinate
 * %zh 左边缘 X 坐标。
 * @param y1
 * %en The top edge Y coordinate
 * %zh 上边缘 Y 坐标。
 * @param x2
 * %en The right edge X coordinate
 * %zh 右边缘 X 坐标。
 * @param y2
 * %en The bottom edge Y coordinate
 * %zh 下边缘 Y 坐标。
 * @param color
 * %en The fill color in ARGB format
 * %zh ARGB 格式的填充颜色。
 * @return
 * %en if drawing succeeded, false otherwise
 * %zh 如果绘制成功则返回 true，否则返回 false。
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
 * %en
 * Draws a texture region on HUD using current [HudRenderContext].
 *
 * %zh
 * 使用当前 [HudRenderContext] 在 HUD 上绘制一个纹理区域。
 * @param ctx
 * %en The HUD render context
 * %zh HUD 渲染上下文。
 * @param texture
 * %en The texture identifier string
 * %zh 纹理标识符字符串。
 * @param x
 * %en The X position on screen
 * %zh 屏幕上的 X 位置。
 * @param y
 * %en The Y position on screen
 * %zh 屏幕上的 Y 位置。
 * @param width
 * %en The width to draw
 * %zh 绘制宽度。
 * @param height
 * %en The height to draw
 * %zh 绘制高度。
 * @param u0
 * %en The U coordinate of the texture region (default: 0)
 * %zh 纹理区域的 U 起始坐标，默认 0。
 * @param u1
 * %en The U coordinate of the texture region (default: 1)
 * %zh 纹理区域的 U 结束坐标，默认 1。
 * @param v0
 * %en The V coordinate of the texture region (default: 0)
 * %zh 纹理区域的 V 起始坐标，默认 0。
 * @param v1
 * %en The V coordinate of the texture region (default: 1)
 * %zh 纹理区域的 V 结束坐标，默认 1。
 * @return
 * %en if drawing succeeded, false if texture ID was invalid
 * %zh 如果绘制成功则返回 true；如果纹理 ID 无效则返回 false。
 */
fun drawHudTexture(
    ctx: HudRenderContext,
    texture: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    u0: Float = 0f,
    u1: Float = 1f,
    v0: Float = 0f,
    v1: Float = 1f
): Boolean {
    val id = runCatching { Identifier.parse(texture) }.getOrNull() ?: return false
    ctx.graphics.blit(id, x, y, x+width, y+height, u0, u1, v0, v1)
    return true
}

/**
 * %en
 * Draw a 3D line using world coordinates and ARGB color.
 *
 * Uses real GPU mesh rendering via VertexConsumer+RenderType.
 *
 * %zh
 * 使用世界坐标和 ARGB 颜色绘制一条 3D 线段。
 *
 * 通过 VertexConsumer 和 RenderType 进行实际 GPU 网格渲染。
 * @param ctx
 * %en The world render context
 * %zh 世界渲染上下文。
 * @param x1
 * %en Start X coordinate in world space
 * %zh 世界空间中的起点 X 坐标。
 * @param y1
 * %en Start Y coordinate in world space
 * %zh 世界空间中的起点 Y 坐标。
 * @param z1
 * %en Start Z coordinate in world space
 * %zh 世界空间中的起点 Z 坐标。
 * @param x2
 * %en End X coordinate in world space
 * %zh 世界空间中的终点 X 坐标。
 * @param y2
 * %en End Y coordinate in world space
 * %zh 世界空间中的终点 Y 坐标。
 * @param z2
 * %en End Z coordinate in world space
 * %zh 世界空间中的终点 Z 坐标。
 * @param argbColor
 * %en The line color in ARGB format
 * %zh ARGB 格式的线条颜色。
 * @param lineWidth
 * %en The width of the line (default: 1.0)
 * %zh 线条宽度，默认 1.0。
 * @return
 * %en if drawing succeeded, false otherwise
 * %zh 如果绘制成功则返回 true，否则返回 false。
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
    val camPos = cam.pos

    return drawLine3DCompat(camPos, x1, y1, z1, x2, y2, z2, argbColor, lineWidth)
}
//
// /**
//  * %en
//  * Draw a colored quad billboard at world position.
//  *
//  * Uses real GPU mesh rendering via VertexConsumer+RenderType.
//  * Billboards always face the camera.
//  *
//  * %zh
//  * 在世界位置绘制一个带颜色的四边形公告板。
//  *
//  * 通过 VertexConsumer 和 RenderType 进行实际 GPU 网格渲染。
//  * 公告板始终朝向相机。
//  * @param ctx
//  * %en The world render context
//  * %zh 世界渲染上下文。
//  * @param x
//  * %en The X coordinate in world space
//  * %zh 世界空间中的 X 坐标。
//  * @param y
//  * %en The Y coordinate in world space
//  * %zh 世界空间中的 Y 坐标。
//  * @param z
//  * %en The Z coordinate in world space
//  * %zh 世界空间中的 Z 坐标。
//  * @param argbColor
//  * %en The color in ARGB format
//  * %zh ARGB 格式的颜色。
//  * @param size
//  * %en The size of the billboard (default: 1.0)
//  * %zh 公告板大小，默认 1.0。
//  * @return
//  * %en if drawing succeeded, false otherwise
//  * %zh 如果绘制成功则返回 true，否则返回 false。
//  */
//fun drawBillboard3D(
//    ctx: WorldRenderContext,
//    x: Double,
//    y: Double,
//    z: Double,
//    argbColor: Int,
//    size: Float = 1.0f
//): Boolean {
//    val cam = ctx.camera ?: return false
//    val camPos = cam.pos
//
//    val a = ((argbColor ushr 24) and 0xFF)
//    val r = ((argbColor ushr 16) and 0xFF)
//    val g = ((argbColor ushr 8) and 0xFF)
//    val b = (argbColor and 0xFF)
//    val h = size / 2f
//
//    // Use camera basis vectors directly for a stable billboard orientation.
//    val right = Vector3f(cam.leftVector()).mul(-1f)
//    val up = Vector3f(cam.upVector())
//
//    val poseStack = PoseStack()
//    poseStack.translate(-camPos.x, -camPos.y, -camPos.z)
//    val pose = poseStack.last()
//
//    // Camera-relative center
//    val cx = (x - camPos.x).toFloat()
//    val cy = (y - camPos.y).toFloat()
//    val cz = (z - camPos.z).toFloat()
//
//    // Four corners: center +/- right*h +/- up*h
//    val v0x = cx - right.x * h - up.x * h
//    val v0y = cy - right.y * h - up.y * h
//    val v0z = cz - right.z * h - up.z * h
//    val v1x = cx + right.x * h - up.x * h
//    val v1y = cy + right.y * h - up.y * h
//    val v1z = cz + right.z * h - up.z * h
//    val v2x = cx + right.x * h + up.x * h
//    val v2y = cy + right.y * h + up.y * h
//    val v2z = cz + right.z * h + up.z * h
//    val v3x = cx - right.x * h + up.x * h
//    val v3y = cy - right.y * h + up.y * h
//    val v3z = cz - right.z * h + up.z * h
//
//    val bufferSource: MultiBufferSource.BufferSource = mc.renderBuffers().bufferSource()
//    val vc = bufferSource.getBuffer(RenderTypes.debugQuads())
//    vc.addVertex(pose, v0x, v0y, v0z).setColor(r, g, b, a)
//    vc.addVertex(pose, v1x, v1y, v1z).setColor(r, g, b, a)
//    vc.addVertex(pose, v2x, v2y, v2z).setColor(r, g, b, a)
//    vc.addVertex(pose, v3x, v3y, v3z).setColor(r, g, b, a)
//    bufferSource.endBatch(RenderTypes.debugQuads())
//    return true
//}
