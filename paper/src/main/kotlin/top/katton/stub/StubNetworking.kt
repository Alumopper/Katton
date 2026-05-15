package top.katton.pack

/**
 * Server pack cache manager stub for Paper (server-only platform).
 * On Paper, all script packs are local files — no server→client sync needed.
 */
object ServerPackCacheManager {
    @JvmStatic fun collectExecutablePacks(): List<ScriptPack> = emptyList()
    @JvmStatic fun reset() {}
}
