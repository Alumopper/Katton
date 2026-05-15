@file:Suppress("unused")
package top.katton.api

object KattonClientApi {
    @JvmStatic fun client(): Any? = null
    @JvmStatic fun clientPlayer(): Any? = null
    @JvmStatic fun clientLevel(): Any? = null
    @JvmStatic fun clientTell(msg: String) {}
    @JvmStatic fun clientTell(msg: Any) {}
    @JvmStatic fun clientActionBar(msg: String) {}
    @JvmStatic fun clientActionBar(msg: Any) {}
    @JvmStatic fun clientTitle(title: String, subtitle: String) {}
    @JvmStatic fun playClientSound(sound: Any, volume: Float, pitch: Float) {}
    @JvmStatic fun playClientSound(sound: Any) {}
    @JvmStatic fun clientToast(title: String, description: String) {}
    @JvmStatic fun getClientPlayerByName(name: String): Any? = null
}
