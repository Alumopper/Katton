@file:Suppress("unused")

package top.katton.api

import net.minecraft.server.level.ServerPlayer
import top.katton.Katton
import top.katton.network.ClientDataManager
import top.katton.network.ClientDataSyncPacket
import top.katton.network.ServerNetworking

/**
 * %en
 * Server-side: sync key-value data to clients in real-time.
 *
 * Usage in server scripts:
 * ```kotlin
 * import top.katton.api.*
 *
 * syncClientData("boss_hp", 0.75)
 * syncClientData("boss_name", "Ender Dragon")
 * syncClientData(somePlayer, "private_msg", "hello")
 * ```
 *
 * Client-side: read synced data (usable in HUD renderers, client scripts).
 *
 * Usage in client scripts:
 * ```kotlin
 * import top.katton.api.*
 *
 * val hp = clientData.getNumber("boss_hp").toDouble()
 * val name = clientData.getString("boss_name")
 * ```
 *
 * Data is synced via play-phase [ClientDataSyncPacket] (Fabric: ServerPlayNetworking,
 * NeoForge: PacketDistributor). The client stores data in [ClientDataManager],
 * a thread-safe in-memory map. Data is not persisted - it resets on disconnect.
 *
 * %zh
 * 服务端可实时向客户端同步键值数据。
 *
 * 服务端脚本用法：
 * ```kotlin
 * import top.katton.api.*
 *
 * syncClientData("boss_hp", 0.75)
 * syncClientData("boss_name", "Ender Dragon")
 * syncClientData(somePlayer, "private_msg", "hello")
 * ```
 *
 * 客户端可以读取这些同步过来的数据，HUD 渲染器和客户端脚本都能用。
 *
 * 客户端脚本用法：
 * ```kotlin
 * import top.katton.api.*
 *
 * val hp = clientData.getNumber("boss_hp").toDouble()
 * val name = clientData.getString("boss_name")
 * ```
 *
 * 数据通过 play 阶段的 [ClientDataSyncPacket] 同步（Fabric: ServerPlayNetworking，NeoForge: PacketDistributor）。
 * 客户端会把数据存进 [ClientDataManager] 这个线程安全的内存 map；数据不会持久化，断开连接后会重置。
 */

// ---------------------------------------------------------------------------
// Server-side API
// ---------------------------------------------------------------------------

/**
 * %en
 * Sync a key-value pair to all connected players.
 * A value of `null` removes the key on the client side.
 *
 * %zh
 * 向所有已连接玩家同步一个键值对。
 * 值为 `null` 时会在客户端删除该键。
 */
fun syncClientData(key: String, value: Any?) {
    val server = Katton.server ?: return
    for (player in server.playerList.players) {
        sendSync(player, key, value)
    }
}

/**
 * %en
 * Sync a key-value pair to a specific player.
 * A value of `null` removes the key on the client side.
 *
 * %zh
 * 向指定玩家同步一个键值对。
 * 值为 `null` 时会在客户端删除该键。
 */
fun syncClientData(player: ServerPlayer, key: String, value: Any?) {
    sendSync(player, key, value)
}

/**
 * %en
 * Sync multiple key-value pairs to all connected players.
 * Entries with `null` values remove the key on the client side.
 *
 * %zh
 * 向所有已连接玩家同步多个键值对。
 * 值为 `null` 的条目会在客户端删除对应的键。
 */
fun syncClientData(entries: Map<String, Any?>) {
    val server = Katton.server ?: return
    for (player in server.playerList.players) {
        sendSyncBatch(player, entries)
    }
}

/**
 * %en
 * Sync multiple key-value pairs to a specific player.
 * Entries with `null` values remove the key on the client side.
 *
 * %zh
 * 向指定玩家同步多个键值对。
 * 值为 `null` 的条目会在客户端删除对应的键。
 */
fun syncClientData(player: ServerPlayer, entries: Map<String, Any?>) {
    sendSyncBatch(player, entries)
}

private fun sendSync(player: ServerPlayer, key: String, value: Any?) {
    val entry = ClientDataSyncPacket.DataEntry(key, value)
    val packet = ClientDataSyncPacket(listOf(entry))
    ServerNetworking.sendPlayPacket(player, packet)
}

private fun sendSyncBatch(player: ServerPlayer, entries: Map<String, Any?>) {
    val dataEntries = entries.map { (k, v) -> ClientDataSyncPacket.DataEntry(k, v) }
    val packet = ClientDataSyncPacket(dataEntries)
    ServerNetworking.sendPlayPacket(player, packet)
}

// ---------------------------------------------------------------------------
// Client-side API
// ---------------------------------------------------------------------------

/**
 * %en
 * Client-side accessor for server-synced data.
 *
 * %zh
 * 服务器同步数据的客户端访问入口。
 */
object KattonClientData {
    /**
     * %en
     * Get the raw value by key. Returns null if key not found.
     *
     * %zh
     * 按键获取原始值。如果找不到该键，返回 null。
     */
    operator fun get(key: String): Any? = ClientDataManager.get(key)

    /**
     * %en
     * Get a string value with an optional default.
     *
     * %zh
     * 获取字符串值，可指定默认值。
     */
    fun getString(key: String, default: String = ""): String {
        return when (val value = get(key)) {
            is String -> value
            else -> value?.toString() ?: default
        }
    }

    /**
     * %en
     * Get a numeric value with an optional default.
     *
     * %zh
     * 获取数值，可指定默认值。
     */
    fun getNumber(key: String, default: Number = 0): Number {
        return when (val value = get(key)) {
            is Number -> value
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    /**
     * %en
     * Get a boolean value with an optional default.
     *
     * %zh
     * 获取布尔值，可指定默认值。
     */
    fun getBool(key: String, default: Boolean = false): Boolean {
        return when (val value = get(key)) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> default
        }
    }

    /**
     * %en
     * Returns all synced data entries (read-only snapshot).
     *
     * %zh
     * 返回所有已同步的数据条目（只读快照）。
     */
    fun all(): Map<String, Any?> = ClientDataManager.getAll()
}

/**
 * %en
 * Shorthand accessor - use `clientData["key"]` in client scripts.
 *
 * %zh
 * 简写访问器 - 在客户端脚本中使用 `clientData["key"]`。
 */
val clientData: KattonClientData
    get() = KattonClientData
