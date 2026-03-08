package top.katton.util

import top.katton.util.Event.Companion.currentScriptOwner

abstract class ContextEvent<C,T>(
    val context: C,
    protected val handlers: MutableMap<C,HandlerEntry<T>> = mutableMapOf()
) {
    data class HandlerEntry<T>(
        val owner: String?,
        val handler: T
    )

    fun register(handler: T) {
        handlers[context] = HandlerEntry(currentScriptOwner(), handler)
    }

    operator fun plusAssign(handler: T) {
        handlers[context] = HandlerEntry(currentScriptOwner(), handler)
    }

    operator fun minusAssign(handler: T) {
        handlers.entries.removeIf { it.value.handler == handler }
    }

    fun remove(handler: T) {
        handlers.entries.removeIf { it.value.handler == handler }
    }

    fun clear() {
        handlers.clear()
    }

    fun clearByOwner(owner: String) {
        handlers.entries.removeIf { it.value.owner == owner }
    }

    protected fun handlerSnapshot(): List<T> {
        return handlers.values.map { it.handler }
    }

    abstract fun invoker(): T
}