package top.katton.dev

import java.util.concurrent.atomic.AtomicLong

/** Bounded diagnostics shared by the compiler and the optional development bridge. */
object DevEvents {
    data class Entry(val cursor: Long, val severity: String, val message: String,
                     val packId: String? = null, val revision: String? = null,
                     val file: String? = null, val line: Int? = null, val column: Int? = null)
    private val sequence = AtomicLong()
    private val entries = ArrayDeque<Entry>()
    @Volatile var enabled = false
    fun emit(severity: String, message: String, packId: String? = null, revision: String? = null,
             file: String? = null, line: Int? = null, column: Int? = null) {
        if (!enabled) return
        synchronized(entries) {
            entries += Entry(sequence.incrementAndGet(), severity, message.take(32_768), packId, revision, file, line, column)
            while (entries.size > 512) entries.removeFirst()
        }
    }
    fun after(cursor: Long): List<Entry> = synchronized(entries) { entries.filter { it.cursor > cursor } }
    data class Batch(val cursor: Long, val entries: List<Entry>, val dropped: Boolean)
    fun read(after: Long): Batch = synchronized(entries) {
        val selected = entries.filter { it.cursor > after }.take(32)
        Batch(selected.lastOrNull()?.cursor ?: sequence.get(), selected,
            after > 0 && entries.firstOrNull()?.cursor?.let { it > after + 1 } == true)
    }
    fun cursor(): Long = sequence.get()
}
