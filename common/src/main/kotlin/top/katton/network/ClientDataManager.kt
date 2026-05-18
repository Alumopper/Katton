package top.katton.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side storage for server-synced key-value data.
 *
 * Updated by [ClientDataSyncPacket] handler in platform networking code.
 * Read by client-side scripts via [top.katton.api.ClientDataApi].
 */
object ClientDataManager {

    private val data = ConcurrentHashMap<String, Any?>()

    fun get(key: String): Any? = data[key]

    fun getAll(): Map<String, Any?> = data.toMap()

    fun put(key: String, value: Any?) {
        if (value == null) {
            data.remove(key)
        } else {
            data[key] = value
        }
    }

    fun putAll(entries: List<ClientDataSyncPacket.DataEntry>) {
        for (entry in entries) {
            put(entry.key, entry.value)
        }
    }

    fun remove(key: String): Any? = data.remove(key)

    fun clear() = data.clear()
}
