package top.katton.client.audio

import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded disk cache. Completed entries are evicted once no handle references them. */
internal object AudioCache {
    private const val MAX_BYTES = 1024L * 1024 * 1024
    private val directory by lazy { Files.createTempDirectory("katton-audio-") }
    private val executor = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64),
        { task -> Thread(task, "Katton audio decoder").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private class Entry(val future: CompletableFuture<PcmFile> = CompletableFuture(), var refs: Int = 1, var bytes: Long = 0)
    private val entries = linkedMapOf<String, Entry>()
    private var total = 0L
    class Lease internal constructor(val future: CompletableFuture<PcmFile>, private val release: () -> Unit) : AutoCloseable {
        private val closed = java.util.concurrent.atomic.AtomicBoolean()
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }
    @Synchronized fun acquire(bytes: ByteArray, mono: Boolean): Lease {
        val key = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } + mono
        val existing = entries.remove(key)
        if (existing != null && !existing.future.isCompletedExceptionally) {
            existing.refs++
            entries[key] = existing
            return lease(key, existing)
        }
        val entry = Entry()
        entries[key] = entry
        try { executor.execute {
            var output: java.nio.file.Path? = null
            try {
                output = Files.createTempFile(directory, "pcm-", ".bin")
                val path = output
                val decoded = AudioDecoders.decode(bytes, path, mono) { count -> reserve(entry, count) }
                entry.future.complete(decoded)
            } catch (failure: Throwable) {
                output?.let { runCatching { Files.deleteIfExists(it) } }
                synchronized(this) { total -= entry.bytes; entries.remove(key, entry) }
                entry.future.completeExceptionally(failure)
            }
        } } catch (failure: RuntimeException) {
            entries.remove(key, entry)
            entry.future.completeExceptionally(failure)
        }
        // A handle that gives up while the decode is still running unregisters the entry; the
        // decoder observes the zero reference count at its next block and cleans up itself.
        return lease(key, entry)
    }
    private fun lease(key: String, entry: Entry) = Lease(entry.future) {
        synchronized(this) {
            entry.refs--
            if (entry.refs == 0 && !entry.future.isDone) entries.remove(key, entry)
        }
    }
    @Synchronized private fun reserve(entry: Entry, count: Int) {
        check(entry.refs > 0) { "Audio preparation cancelled" }
        if (total + count > MAX_BYTES) {
            val evicted = mutableListOf<PcmFile>()
            val iterator = entries.iterator()
            while (total + count > MAX_BYTES && iterator.hasNext()) {
                val candidate = iterator.next().value
                // refs == 0 for a completed entry means no handle can reach it any more.
                if (candidate !== entry && candidate.refs == 0 && candidate.future.isDone &&
                    !candidate.future.isCompletedExceptionally) {
                    evicted += candidate.future.join()
                    total -= candidate.bytes
                    iterator.remove()
                }
            }
            // Deleting cannot fail while the cache lock is held.
            evicted.forEach { runCatching { Files.deleteIfExists(it.path) } }
        }
        require(total + count <= MAX_BYTES) { "Audio PCM cache exceeds 1 GiB" }
        entry.bytes += count
        total += count
    }
    @Synchronized fun clearIdle() {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.refs == 0 && entry.future.isDone && !entry.future.isCompletedExceptionally) {
                Files.deleteIfExists(entry.future.join().path)
                total -= entry.bytes
                iterator.remove()
            }
        }
    }
    /** Drops every unreferenced entry; the decoder executor stays alive for the next world. */
    fun destroy() {
        executor.queue.clear()
        synchronized(this) {
            entries.entries.removeIf { (_, entry) ->
                if (entry.refs > 0) return@removeIf false
                if (entry.future.isDone && !entry.future.isCompletedExceptionally) {
                    runCatching { Files.deleteIfExists(entry.future.join().path) }
                    total -= entry.bytes
                }
                true
            }
        }
    }
}
