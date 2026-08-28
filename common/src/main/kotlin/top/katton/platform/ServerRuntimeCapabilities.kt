package top.katton.platform

/** Runtime capabilities that cannot be inferred from the shared Minecraft API. */
object ServerRuntimeCapabilities {
    @Volatile
    private var scriptPackDataReloadSupported: Boolean = true

    @JvmStatic
    fun configureScriptPackDataReload(supported: Boolean) {
        scriptPackDataReloadSupported = supported
    }

    fun supportsScriptPackDataReload(): Boolean = scriptPackDataReloadSupported

    @JvmStatic
    fun reset() {
        scriptPackDataReloadSupported = true
    }
}
