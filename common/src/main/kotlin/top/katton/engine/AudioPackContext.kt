package top.katton.engine

import top.katton.pack.ScriptPack

/** Candidate resources are visible during entrypoints, before the transaction publishes the pack. */
internal object AudioPackContext {
    private val current = ThreadLocal<ScriptPack?>()
    fun currentPack(): ScriptPack? = current.get()
    fun <T> withPack(pack: ScriptPack, action: () -> T): T {
        val old = current.get()
        current.set(pack)
        return try { action() } finally { if (old == null) current.remove() else current.set(old) }
    }
}
