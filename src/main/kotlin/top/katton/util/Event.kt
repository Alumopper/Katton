package top.katton.util

typealias FabricEvent<T> = net.fabricmc.fabric.api.event.Event<T>

abstract class Event<T> {
    private data class HandlerEntry<T>(
        val owner: String?,
        val handler: T
    )

    protected val handlers = mutableListOf<HandlerEntry<T>>()

    fun register(handler: T) {
        handlers.add(HandlerEntry(currentScriptOwner.get(), handler))
    }

    operator fun plusAssign(handler: T) {
        handlers.add(HandlerEntry(currentScriptOwner.get(), handler))
    }

    operator fun minusAssign(handler: T) {
        handlers.removeIf { it.handler == handler }
    }

    fun remove(handler: T) {
        handlers.removeIf { it.handler == handler }
    }

    fun clear() {
        handlers.clear()
    }

    fun clearByOwner(owner: String) {
        handlers.removeIf { it.owner == owner }
    }

    protected fun handlerSnapshot(): List<T> {
        return handlers.map { it.handler }
    }

    abstract fun invoker(): T

    companion object {
        private val currentScriptOwner = ThreadLocal<String?>()

        fun <R> withScriptOwner(owner: String?, action: () -> R): R {
            if (owner == null) return action()
            val previous = currentScriptOwner.get()
            currentScriptOwner.set(owner)
            return try {
                action()
            } finally {
                if (previous == null) {
                    currentScriptOwner.remove()
                } else {
                    currentScriptOwner.set(previous)
                }
            }
        }

        fun clearHandlersByOwner(owner: String) {
            fabricEventRegistry.values.forEach { eventList ->
                eventList.forEach { event ->
                    event.clearByOwner(owner)
                }
            }
        }

        fun currentScriptOwner(): String? = currentScriptOwner.get()

        fun <T : Any, U: Any> createReloadable(event: FabricEvent<U>, adapter: (T) -> U, callback: (List<T>) -> T): Event<T> {
            val e = object : Event<T>() {
                override fun invoker(): T {
                    return callback(handlerSnapshot())
                }
            }

            // 获取或创建对应 Fabric 事件的列表
            val list = fabricEventRegistry.getOrPut(event) {
                val newList = mutableListOf<Event<*>>()

                // 优化：直接捕获 newList 引用，避免在事件触发热路径中进行 Map 查找
                // 这里创建一个虚拟列表，实时映射 newList 中所有 Katton Event 的 invoker
                val invokerList = object : java.util.AbstractList<T>() {
                    override val size: Int get() = newList.size
                    override fun get(index: Int): T {
                        @Suppress("UNCHECKED_CAST")
                        return (newList[index] as Event<T>).invoker()
                    }
                }

                // 注册到 Fabric 事件，当 Fabric 事件触发时，依次调用所有子事件的 invoker
                event.register(adapter(callback(invokerList)))
                newList
            }

            list.add(e)
            return e
        }

        val fabricEventRegistry = mutableMapOf<FabricEvent<*>, MutableList<Event<*>>>()
    }
}