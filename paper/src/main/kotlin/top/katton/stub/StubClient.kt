@file:Suppress("unused")
package top.katton.api

object KattonClientApi {
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun client(): Any? = null
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun clientPlayer(): Any? = null
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun clientLevel(): Any? = null
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun clientTell(msg: String) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun clientTell(msg: Any) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun clientActionBar(msg: String) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun clientActionBar(msg: Any) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun clientTitle(title: String, subtitle: String) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun playClientSound(sound: Any, volume: Float, pitch: Float) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun playClientSound(sound: Any) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun clientToast(title: String, description: String) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun getClientPlayerByName(name: String): Any? = null
}
