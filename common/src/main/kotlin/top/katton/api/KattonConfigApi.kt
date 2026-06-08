@file:Suppress("unused")

package top.katton.api

import top.katton.config.KattonConfigManager

/**
 * %en
 * Script-facing config API. Reads config values from the current script's pack manifest.
 *
 * Usage in scripts:
 * ```kotlin
 * import top.katton.api.*
 *
 * val rate = config.getNumber("spawn_rate").toDouble()
 * val prefix = config.getString("message_prefix", "[Default]")
 * val debug = config.getBool("debug_mode")
 * val raw = config["some_key"]
 * ```
 *
 * Each script pack has its own independent config values (from manifest.json `"config"` field).
 * The current pack is auto-detected from the script execution context.
 *
 * %zh
 * 面向脚本的配置 API，会从当前脚本包的 manifest 里读取配置值。
 *
 * 脚本中的用法：
 * ```kotlin
 * import top.katton.api.*
 *
 * val rate = config.getNumber("spawn_rate").toDouble()
 * val prefix = config.getString("message_prefix", "[Default]")
 * val debug = config.getBool("debug_mode")
 * val raw = config["some_key"]
 * ```
 *
 * 每个脚本包都有自己独立的配置值，来自 manifest.json 里的 `"config"` 字段。
 * 当前脚本包会根据脚本执行上下文自动识别。
 */
object KattonConfig {

    private fun resolvePack(): String? = KattonConfigManager.resolveCurrentPack()

    /**
     * %en
     * Get the raw config value by key. Returns null if key not found or pack not resolved.
     *
     * %zh
     * 按键获取原始配置值。如果键不存在，或者当前脚本包无法解析，则返回 null。
     */
    operator fun get(key: String): Any? {
        val pack = resolvePack() ?: return null
        return KattonConfigManager.get(pack, key)
    }

    /**
     * %en
     * Get a string config value with an optional default.
     *
     * %zh
     * 获取字符串配置值，可指定默认值。
     */
    fun getString(key: String, default: String = ""): String {
        val value = get(key)
        return when (value) {
            is String -> value
            else -> value?.toString() ?: default
        }
    }

    /**
     * %en
     * Get a numeric config value with an optional default.
     *
     * %zh
     * 获取数值配置，可指定默认值。
     */
    fun getNumber(key: String, default: Number = 0): Number {
        val value = get(key)
        return when (value) {
            is Number -> value
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    /**
     * %en
     * Get a boolean config value with an optional default.
     *
     * %zh
     * 获取布尔配置值，可指定默认值。
     */
    fun getBool(key: String, default: Boolean = false): Boolean {
        val value = get(key)
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> default
        }
    }

    /**
     * %en
     * Returns all config entries for the current pack.
     *
     * %zh
     * 返回当前脚本包的全部配置项。
     */
    fun all(): Map<String, Any> {
        val pack = resolvePack() ?: return emptyMap()
        return KattonConfigManager.all(pack)
    }
}

/**
 * %en
 * Shorthand accessor - use `config["key"]` in scripts.
 *
 * %zh
 * 简写访问器 - 在脚本中使用 `config["key"]`。
 */
val config: KattonConfig
    get() = KattonConfig
