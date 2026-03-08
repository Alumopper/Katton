@file:Suppress("unused")

package top.katton.api

import com.mojang.logging.LogUtils
import net.minecraft.server.MinecraftServer
import top.katton.Katton
import top.katton.util.Event
import java.util.concurrent.ConcurrentHashMap


internal val LOGGER = LogUtils.getLogger()

private val onceRegistry = ConcurrentHashMap<String, MutableSet<String>>()


private fun onceNamespace(namespace: String?): String {
    if (namespace != null) return namespace
    return Event.currentScriptOwner() ?: "global"
}


/**
 * Execute [block] only once for a given [key] under the current script owner namespace.
 *
 * When called inside a script execution, the default namespace is that script's owner id;
 * outside script execution, the default namespace is "global".
 *
 * @return true if [block] was executed this time, false if it was already executed before.
 */
fun once(key: String, namespace: String? = null, block: () -> Unit): Boolean {
    val ns = onceNamespace(namespace)
    val set = onceRegistry.computeIfAbsent(ns) { ConcurrentHashMap.newKeySet() }
    val isFirstTime = set.add(key)
    if (isFirstTime) {
        block()
    }
    return isFirstTime
}


/**
 * Remove once-guard marker for [key] in the current script owner namespace (or [namespace]).
 *
 * @return true if marker existed and was removed, false otherwise.
 */
fun resetOnce(key: String, namespace: String? = null): Boolean {
    val ns = onceNamespace(namespace)
    val set = onceRegistry[ns] ?: return false
    val removed = set.remove(key)
    if (set.isEmpty()) {
        onceRegistry.remove(ns, set)
    }
    return removed
}


/**
 * Clear all once-guard markers in the current script owner namespace (or [namespace]).
 */
fun clearOnce(namespace: String? = null) {
    val ns = onceNamespace(namespace)
    onceRegistry.remove(ns)
}


/**
 * Current minecraft server instance. Maybe null during client-side execution.
 */
val server: MinecraftServer?
    get() = Katton.server


fun requireServer(): MinecraftServer =
    server ?: error("MinecraftServer is not available (client-side or not started)")

