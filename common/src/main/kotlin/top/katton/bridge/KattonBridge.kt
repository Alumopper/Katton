package top.katton.bridge

import net.minecraft.world.entity.AnimationState
import java.util.concurrent.ConcurrentHashMap

/**
 * Cross-ClassLoader bridge for sharing data between server and client
 * script compilations.
 *
 * Because Katton compiles server scripts and client scripts in separate
 * [org.jetbrains.kotlin.scripting.compiler.plugin.impl.CompiledScriptClassLoader]
 * instances, static fields in script classes are ClassLoader-isolated.
 *
 * This bridge lives in Katton's mod module (loaded by the system ClassLoader),
 * so ALL script ClassLoaders see the same singleton.
 *
 * Usage from entity tick() (server or client script):
 * ```
 * KattonBridge.put("anim:$id:idle", idleAnimationState)
 * ```
 *
 * Usage from renderer extractRenderState() (client script):
 * ```
 * val idleState = KattonBridge.get("anim:$id:idle") as? AnimationState ?: return
 * ```
 */
object KattonBridge {
    private val store = ConcurrentHashMap<String, Any>()

    operator fun get(key: String): Any? = store[key]
    operator fun set(key: String, value: Any) { store[key] = value }
    fun remove(key: String): Any? = store.remove(key)
    fun removeByPrefix(prefix: String) {
        store.keys.removeAll { it.startsWith(prefix) }
    }
}
