@file:Suppress("unused")

package top.katton.api

import com.mojang.logging.LogUtils
import net.minecraft.server.MinecraftServer
import top.katton.Katton
import top.katton.util.ScriptExecutionContext
import java.util.concurrent.ConcurrentHashMap

internal val LOGGER = LogUtils.getLogger()

private val onceRegistry = ConcurrentHashMap<String, MutableSet<String>>()

private fun onceNamespace(namespace: String?): String {
    if (namespace != null) return namespace
    return ScriptExecutionContext.currentScriptOwner() ?: "global"
}

/**
 * %en
 * Execute [block] only once for a given [key] under the current script owner namespace.
 *
 * When script is reloaded, the markers won't be cleared, so the block won't execute again until the marker is reset with [resetOnce] or [clearOnce].
 *
 * When called inside a script execution, the default namespace is that script's owner id;
 * outside script execution, the default namespace is "global".
 *
 * %zh
 * 在当前脚本所有者命名空间下，对指定键只执行一次。
 *
 * 脚本重载后，这些标记不会自动清除，因此相同的代码段不会再次执行，直到你调用 [resetOnce] 或 [clearOnce] 重置标记。
 *
 * 在脚本执行期间调用时，默认命名空间是该脚本的所有者 id；在脚本执行之外调用时，默认命名空间是 "global"。
 *
 * @return
 * %en if [block] was executed this time, false if it was already executed before.
 * %zh 如果这次执行了 [block] 则返回 true；如果它之前已经执行过，则返回 false。
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
 * %en
 * Remove once-guard marker for [key] in the current script owner namespace (or [namespace]).
 *
 * %zh
 * 移除当前脚本所有者命名空间（或指定命名空间）下 [key] 对应的 once 防护标记。
 *
 * @return
 * %en if marker existed and was removed, false otherwise.
 * %zh 如果标记存在并已移除，则返回 true；否则返回 false。
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
 * %en
 * Clear all once-guard markers in the current script owner namespace (or [namespace]).
 *
 * %zh
 * 清除当前脚本所有者命名空间（或指定命名空间）下的所有 once 防护标记。
 */
fun clearOnce(namespace: String? = null) {
    val ns = onceNamespace(namespace)
    onceRegistry.remove(ns)
}

/**
 * %en
 * Current minecraft server instance. Maybe null during client-side execution.
 *
 * Use this property when you need optional access to the server.
 * For cases where the server must be available, use [requireServer] instead.
 *
 * %zh
 * 当前 Minecraft 服务器实例。在客户端执行期间可能为 null。
 *
 * 当你只需要可选地访问服务器时使用这个属性。
 * 如果操作必须依赖服务器，请改用 [requireServer]。
 *
 * @return
 * %en current MinecraftServer instance, or null if not available
 * %zh 当前 MinecraftServer 实例；如果不可用则返回 null。
 */
val server: MinecraftServer?
    get() = Katton.server

/**
 * %en
 * Requires the Minecraft server instance to be available.
 *
 * Use this function when the server must be present for the operation to succeed.
 * Throws an error if the server is not available (e.g., during client-side execution
 * or before the server has started).
 *
 * %zh
 * 要求 Minecraft 服务器实例可用。
 *
 * 当操作必须依赖服务器才能成功时使用这个函数。
 * 如果服务器不可用会直接抛出错误，例如在客户端执行期间，或者服务器尚未启动时。
 *
 * @return
 * %en current MinecraftServer instance
 * %zh 当前 MinecraftServer 实例。
 * @throws IllegalStateException
 * %en if the server is not available
 * %zh 当服务器不可用时抛出。
 */
fun requireServer(): MinecraftServer =
    server ?: error("MinecraftServer is not available (client-side or not started)")
