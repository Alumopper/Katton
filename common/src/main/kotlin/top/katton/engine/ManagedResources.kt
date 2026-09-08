package top.katton.engine

import top.katton.util.ScriptExecutionContext
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Reversible Katton contributions. Detach preserves the actual callback/value, never replays an entrypoint. */
object ManagedResources {
    class Record internal constructor(val owner: String, internal val attach: () -> Unit,
                                      internal val detach: () -> Unit, internal val dispose: () -> Unit, internal val resumed: () -> Unit,
                                      internal val persistent: Boolean)
    private val lock = ReentrantLock()
    private val drained = lock.newCondition()
    private val records = mutableListOf<Record>()
    private val paused = mutableSetOf<String>()
    private val calls = mutableMapOf<String, Int>()
    private val activating = ThreadLocal<String?>()
    fun <T> activation(ownerPrefix: String?, action: () -> T): T {
        val previous = activating.get()
        activating.set(ownerPrefix)
        return try { action() } finally { activating.set(previous) }
    }

    fun record(attach: () -> Unit, detach: () -> Unit, dispose: () -> Unit = {}, resumed: () -> Unit = {}, persistent: Boolean = false): Record? {
        val owner = ScriptExecutionContext.currentScriptOwner() ?: return null
        return Record(owner, attach, detach, dispose, resumed, persistent).also { record -> lock.withLock { records += record } }
    }
    internal fun retains(prefix: String): Boolean = lock.withLock {
        records.any { it.owner.startsWith(prefix) } || calls.any { it.key.startsWith(prefix) && it.value > 0 }
    }
    /** Used after the native owner has ended a persistent resource's lifetime. */
    internal fun release(record: Record) {
        lock.withLock { records.remove(record) }
        discard(listOf(record))
    }
    private class Contribution(val owner: String?, val value: Any?, val sequence: Long)
    private class Contributions(val base: Any?, val publish: (List<Any?>) -> Unit, val values: MutableList<Contribution> = mutableListOf())
    private val contributions = mutableMapOf<Any, Contributions>()
    private val sequence = java.util.concurrent.atomic.AtomicLong()

    /** Ordered contributions can be removed and restored without overwriting an unrelated owner's value. */
    @Suppress("UNCHECKED_CAST")
    fun <T> contribute(key: Any, base: T, value: T, exclusive: Boolean = false, publish: (List<T>) -> Unit) {
        val owner = ScriptExecutionContext.currentScriptOwner()
        val contribution = Contribution(owner, value, sequence.incrementAndGet())
        val stack = lock.withLock {
            val stack = contributions.getOrPut(key) { Contributions(base, { values -> publish(values as List<T>) }) }
            if (exclusive) require(stack.values.all { it.owner == owner }) { "Resource $key is already owned by another script instance" }
            stack.values += contribution
            try { stack.publish(listOf(stack.base) + stack.values.map { it.value }) }
            catch (failure: Throwable) {
                stack.values.remove(contribution)
                runCatching { stack.publish(listOf(stack.base) + stack.values.map { it.value }) }.onFailure(failure::addSuppressed)
                throw failure
            }
            stack
        }
        record(
            attach = { lock.withLock { stack.values += contribution; stack.values.sortBy { it.sequence }; stack.publish(listOf(stack.base) + stack.values.map { it.value }); contributions[key] = stack } },
            detach = { lock.withLock { stack.values.remove(contribution); stack.publish(listOf(stack.base) + stack.values.map { it.value }) } },
            dispose = { lock.withLock { if (stack.values.isEmpty()) contributions.remove(key, stack) } }
        )
    }

    fun <K, V> put(map: MutableMap<K, V>, key: K, value: V, exclusive: Boolean = false) {
        contribute(MapKey(map, key), map[key], value, exclusive) { values ->
            val latest = values.lastOrNull()
            if (latest == null) map.remove(key) else map[key] = latest
        }
    }
    private class MapKey(val map: Any, val key: Any?) {
        override fun hashCode() = System.identityHashCode(map) * 31 + (key?.hashCode() ?: 0)
        override fun equals(other: Any?) = other is MapKey && other.map === map && other.key == key
    }

    fun isPaused(owner: String?): Boolean = owner != null && lock.withLock { paused.any(owner::startsWith) }
    /** A scheduler can defer a callback that lost the race with quiescence without cancelling its task. */
    fun runCallback(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (_: top.katton.util.ManagedCallbackPausedException) { false }
    fun enter(owner: String?): Boolean = lock.withLock {
        if (owner == null) return true
        if (ScriptExecutionContext.currentScriptOwner() != owner && activating.get()?.let(owner::startsWith) != true && paused.any(owner::startsWith)) return false
        calls[owner] = calls.getOrDefault(owner, 0) + 1
        true
    }
    fun leave(owner: String?) = lock.withLock {
        if (owner != null) {
            val remaining = calls.getOrDefault(owner, 1) - 1
            if (remaining == 0) calls.remove(owner) else calls[owner] = remaining
            drained.signalAll()
        }
    }
    fun pause(prefixes: List<String>, timeoutMillis: Long): Boolean = lock.withLock {
        paused += prefixes
        var remaining = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (calls.any { (owner, count) -> count > 0 && prefixes.any(owner::startsWith) }) {
            if (remaining <= 0) { paused.removeAll(prefixes.toSet()); return false }
            try { remaining = drained.awaitNanos(remaining) }
            catch (interrupted: InterruptedException) {
                paused.removeAll(prefixes.toSet())
                Thread.currentThread().interrupt()
                throw interrupted
            }
        }
        true
    }
    fun resume(prefixes: List<String>) {
        val resumed = lock.withLock {
            paused.removeAll(prefixes.toSet())
            records.filter { record -> prefixes.any(record.owner::startsWith) }
        }
        resumed.forEach { it.resumed() }
    }
    fun detach(prefixes: List<String>, includePersistent: Boolean = false): List<Record> {
        val selected = lock.withLock { records.filter { record -> (includePersistent || !record.persistent) && prefixes.any(record.owner::startsWith) }.also { records.removeAll(it.toSet()) } }
        val detached = mutableListOf<Record>()
        try { selected.asReversed().forEach { it.detach(); detached += it } }
        catch (failure: Throwable) { detached.asReversed().forEach { it.attach() }; lock.withLock { records += selected }; throw failure }
        return selected
    }
    fun restore(saved: List<Record>) { saved.forEach { it.attach() }; lock.withLock { records += saved } }
    fun discard(saved: List<Record>) {
        saved.forEach { it.dispose() }
        saved.map { it.owner }.distinct().forEach { owner ->
            if (lock.withLock { records.none { it.owner == owner } }) ScriptExecutionContext.forgetOwner(owner)
        }
        PackRuntime.collectRetired()
    }
}
