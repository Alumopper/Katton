@file:Suppress("unused")
package top.katton.api

object KattonClientRenderApi {
    @JvmStatic fun clearClientRenderers() {}
    @JvmStatic fun dispatchHudRender() {}
    @JvmStatic fun dispatchWorldRender() {}
    @JvmStatic fun drawLine3D(from: Any, to: Any, color: Any, width: Float) {}
    @JvmStatic fun drawHudText(text: String, x: Int, y: Int, color: Int) {}
}
