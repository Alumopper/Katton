package top.katton.engine

import net.minecraft.server.MinecraftServer
import top.katton.pack.ScriptPlatform
import java.util.IdentityHashMap

/**
 * Identifies data-pack reloads started by Katton itself.
 *
 * Platform reload hooks consume the marker so the resource refresh still emits
 * lifecycle events, but does not recursively start another script reload.
 */
object InternalDatapackReloads {
    private val pending = IdentityHashMap<MinecraftServer, Int>()

    @JvmStatic
    @Synchronized
    fun begin(server: MinecraftServer): Boolean {
        if (ScriptDependencyManager.platform !in setOf(ScriptPlatform.FABRIC, ScriptPlatform.NEOFORGE)) {
            return false
        }
        pending[server] = (pending[server] ?: 0) + 1
        return true
    }

    @JvmStatic
    @Synchronized
    fun consume(server: MinecraftServer): Boolean {
        val count = pending[server] ?: return false
        if (count <= 1) pending.remove(server) else pending[server] = count - 1
        return true
    }

    @JvmStatic
    @Synchronized
    fun cancel(server: MinecraftServer) {
        consume(server)
    }

    @JvmStatic
    @Synchronized
    internal fun pendingCount(): Int = pending.values.sum()
}
