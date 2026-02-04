package top.katton.util

import org.lwjgl.system.Callback

abstract class Event<T> {
    protected val handlers = mutableListOf<T>()

    fun register(handler: T) {
        handlers.add(handler)
    }

    operator fun plusAssign(handler: T) {
        handlers.add(handler)
    }

    operator fun minusAssign(handler: T) {
        handlers.remove(handler)
    }

    fun remove(handler: T) {
        handlers.remove(handler)
    }

    fun clear() {
        handlers.clear()
    }

    abstract fun invoker(): T

    companion object {
        fun <T> create(callback: (List<T>) -> T): Event<T> {
            return object : Event<T>() {
                override fun invoker(): T {
                    return callback(handlers)
                }
            }
        }
    }
}