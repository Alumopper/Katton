@file:Suppress("unused")

package top.katton.api

import top.katton.config.KattonConfigManager

/**
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
 */
object KattonConfig {

    private fun resolvePack(): String? = KattonConfigManager.resolveCurrentPack()

    /** Get the raw config value by key. Returns null if key not found or pack not resolved. */
    operator fun get(key: String): Any? {
        val pack = resolvePack() ?: return null
        return KattonConfigManager.get(pack, key)
    }

    /** Get a string config value with an optional default. */
    fun getString(key: String, default: String = ""): String {
        val value = get(key)
        return when (value) {
            is String -> value
            else -> value?.toString() ?: default
        }
    }

    /** Get a numeric config value with an optional default. */
    fun getNumber(key: String, default: Number = 0): Number {
        val value = get(key)
        return when (value) {
            is Number -> value
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    /** Get a boolean config value with an optional default. */
    fun getBool(key: String, default: Boolean = false): Boolean {
        val value = get(key)
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> default
        }
    }

    /** Returns all config entries for the current pack. */
    fun all(): Map<String, Any> {
        val pack = resolvePack() ?: return emptyMap()
        return KattonConfigManager.all(pack)
    }
}

/** Shorthand accessor — use `config["key"]` in scripts. */
val config: KattonConfig
    get() = KattonConfig
