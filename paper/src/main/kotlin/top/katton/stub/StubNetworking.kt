package top.katton.pack

/**
 * Server pack cache manager stub for Paper (server-only platform).
 * On Paper, all script packs are local files — no server→client sync needed.
 */
@Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
object ServerPackCacheManager {
    @JvmStatic fun collectExecutablePacks(): List<ScriptPack> = emptyList()
    @JvmStatic fun reset() {}
}
