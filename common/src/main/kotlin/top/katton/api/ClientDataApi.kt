@file:Suppress("unused")

package top.katton.api

import net.minecraft.server.level.ServerPlayer
import top.katton.Katton
import top.katton.network.ClientDataManager
import top.katton.network.ClientDataSyncPacket
import top.katton.network.ServerNetworking

/**
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
 * a thread-safe in-memory map. Data is not persisted — it resets on disconnect.
 */

// ── Server-side API ──────────────────────────────────────────────

/**
 * Sync a key-value pair to all connected players.
 * A value of `null` removes the key on the client side.
 */
fun syncClientData(key: String, value: Any?) {
    val server = Katton.server ?: return
    for (player in server.playerList.players) {
        sendSync(player, key, value)
    }
}

/**
 * Sync a key-value pair to a specific player.
 * A value of `null` removes the key on the client side.
 */
fun syncClientData(player: ServerPlayer, key: String, value: Any?) {
    sendSync(player, key, value)
}

/**
 * Sync multiple key-value pairs to all connected players.
 * Entries with `null` values remove the key on the client side.
 */
fun syncClientData(entries: Map<String, Any?>) {
    val server = Katton.server ?: return
    for (player in server.playerList.players) {
        sendSyncBatch(player, entries)
    }
}

/**
 * Sync multiple key-value pairs to a specific player.
 * Entries with `null` values remove the key on the client side.
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

// ── Client-side API ──────────────────────────────────────────────

/** Client-side accessor for server-synced data. */
object KattonClientData {
    /** Get the raw value by key. Returns null if key not found. */
    operator fun get(key: String): Any? = ClientDataManager.get(key)

    /** Get a string value with an optional default. */
    fun getString(key: String, default: String = ""): String {
        val value = get(key)
        return when (value) {
            is String -> value
            else -> value?.toString() ?: default
        }
    }

    /** Get a numeric value with an optional default. */
    fun getNumber(key: String, default: Number = 0): Number {
        val value = get(key)
        return when (value) {
            is Number -> value
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    /** Get a boolean value with an optional default. */
    fun getBool(key: String, default: Boolean = false): Boolean {
        val value = get(key)
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> default
        }
    }

    /** Returns all synced data entries (read-only snapshot). */
    fun all(): Map<String, Any?> = ClientDataManager.getAll()
}

/** Shorthand accessor — use `clientData["key"]` in client scripts. */
val clientData: KattonClientData
    get() = KattonClientData
