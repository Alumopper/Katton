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
    private var retainedStringCharacters = 0

    fun get(key: String): Any? = data[key]

    fun getAll(): Map<String, Any?> = data.toMap()

    @Synchronized
    fun put(key: String, value: Any?) {
        // Keep direct in-process callers under the same limits as network data;
        // otherwise a client script could bypass the packet codec's bounds.
        if (key.length > ClientPacketLimits.MAX_DATA_KEY_CHARS) return
        if (value is String && value.length > ClientPacketLimits.MAX_DATA_STRING_CHARS) return
        if (value == null) {
            remove(key)
            return
        }
        val previous = data[key]
        val previousCharacters = (previous as? String)?.length ?: 0
        val valueCharacters = (value as? String)?.length ?: 0
        val nextCharacters = retainedStringCharacters - previousCharacters + valueCharacters
        if (previous == null && data.size >= ClientPacketLimits.MAX_DATA_ENTRIES) return
        if (nextCharacters > ClientPacketLimits.MAX_TOTAL_DATA_STRING_CHARS) return
        data[key] = value
        retainedStringCharacters = nextCharacters
    }

    @Synchronized
    fun putAll(entries: List<ClientDataSyncPacket.DataEntry>) {
        // Keys are unique within a packet, so removals can safely release space
        // before additions regardless of their serialized order.
        entries.asSequence().filter { it.value == null }.forEach { put(it.key, null) }
        entries.asSequence().filter { it.value != null }.forEach { put(it.key, it.value) }
    }

    @Synchronized
    fun remove(key: String): Any? = data.remove(key).also { removed ->
        retainedStringCharacters -= (removed as? String)?.length ?: 0
    }

    @Synchronized
    fun clear() {
        data.clear()
        retainedStringCharacters = 0
    }
}
