@file:Suppress("unused")

package top.katton.api

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import top.katton.platform.ClientApiHooks
import top.katton.util.Event
import java.util.concurrent.ConcurrentHashMap

/**
 * Screen-space render callback context.
 *
 * [graphics] is the runtime GUI graphics object from Minecraft.
 */
data class HudRenderContext(
    val graphics: Any,
    val tickDelta: Float
)

/**
 * World-space render callback context.
 *
 * [viewMatrix], [projectionMatrix] and [camera] are runtime objects from Minecraft render pipeline.
 */
data class WorldRenderContext(
    val viewMatrix: Any,
    val projectionMatrix: Any,
    val camera: Any?,
    val tickDelta: Float
)

/** Ordering bucket for HUD render callbacks. */
enum class HudRenderLayer {
    BACKGROUND,
    NORMAL,
    FOREGROUND
}

/** Ordering bucket for world render callbacks. */
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
private val clientBridge: ClientApiHooks.Bridge
    get() = ClientApiHooks.get()

private fun asComponent(message: Any): Component =
    message as? Component ?: Component.literal(message.toString())

/**
 * Register (or replace) a HUD renderer by [id].
 */
fun registerHudRenderer(id: String, render: (HudRenderContext) -> Unit) {
    registerHudRenderer(id, HudRenderLayer.NORMAL, 0, render)
}

/**
 * Register (or replace) a HUD renderer by [id], with [layer] and [priority].
 *
 * Lower priority values are rendered earlier.
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
 */
fun unregisterHudRenderer(id: String): Boolean = hudRenderers.remove(id) != null

/**
 * Register (or replace) a world-space renderer by [id].
 */
fun registerWorldRenderer(id: String, render: (WorldRenderContext) -> Unit) {
    registerWorldRenderer(id, WorldRenderLayer.NORMAL, 0, render)
}

/**
 * Register (or replace) a world renderer by [id], with [layer] and [priority].
 *
 * Lower priority values are rendered earlier.
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
 */
fun unregisterWorldRenderer(id: String): Boolean = worldRenderers.remove(id) != null

/** Clears all client render callbacks. */
fun clearClientRenderers() {
    hudRenderers.clear()
    worldRenderers.clear()
}

/** Internal dispatcher: called by platform render hooks. */
@JvmName("dispatchHudRender")
fun dispatchHudRender(graphics: Any, tickDelta: Float) {
    val ctx = HudRenderContext(graphics, tickDelta)
    val ordered = hudRenderers.values
        .sortedWith(compareBy<HudRendererEntry>({ it.layer.ordinal }, { it.priority }))
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

/** Internal dispatcher: called by platform render hooks. */
@JvmName("dispatchWorldRender")
fun dispatchWorldRender(viewMatrix: Any, projectionMatrix: Any, camera: Any?, tickDelta: Float) {
    val ctx = WorldRenderContext(viewMatrix, projectionMatrix, camera, tickDelta)
    val ordered = worldRenderers.values
        .sortedWith(compareBy<WorldRendererEntry>({ it.layer.ordinal }, { it.priority }))
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

/** Draws text on HUD using current [HudRenderContext]. */
fun drawHudText(
    ctx: HudRenderContext,
    message: Any,
    x: Int,
    y: Int,
    color: Int = 0xFFFFFF,
    shadow: Boolean = true
): Boolean {
    return clientBridge.hudDrawText(ctx.graphics, asComponent(message), x, y, color, shadow)
}

/** Draws a solid rectangle on HUD using current [HudRenderContext]. */
fun fillHudRect(
    ctx: HudRenderContext,
    x1: Int,
    y1: Int,
    x2: Int,
    y2: Int,
    color: Int
): Boolean {
    return clientBridge.hudFillRect(ctx.graphics, x1, y1, x2, y2, color)
}

/** Draws a texture region on HUD using current [HudRenderContext]. */
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
    return clientBridge.hudBlitTexture(
        ctx.graphics,
        id,
        x,
        y,
        width,
        height,
        u,
        v,
        textureWidth,
        textureHeight
    )
}

/**
 * Draw a 3D line using world coordinates and ARGB color.
 *
 * Uses real GPU mesh rendering via VertexConsumer+RenderType.
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
    return clientBridge.worldDrawMeshLine(
        ctx.viewMatrix, ctx.projectionMatrix, ctx.camera,
        x1, y1, z1, x2, y2, z2, argbColor, lineWidth
    )
}

/**
 * Draw a colored quad billboard at world position.
 *
 * Uses real GPU mesh rendering via VertexConsumer+RenderType.
 */
fun drawBillboard3D(
    ctx: WorldRenderContext,
    x: Double,
    y: Double,
    z: Double,
    argbColor: Int,
    size: Float = 1.0f
): Boolean {
    return clientBridge.worldDrawMeshQuad(
        ctx.viewMatrix, ctx.projectionMatrix, ctx.camera,
        x, y, z, argbColor, size
    )
}
