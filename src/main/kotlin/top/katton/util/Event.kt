package top.katton.util

typealias FabricEvent<T> = net.fabricmc.fabric.api.event.Event<T>

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
        fun <T : Any> createReloadable(event: FabricEvent<T>, callback: (List<T>) -> T): Event<T> {
            val e = object : Event<T>() {
                override fun invoker(): T {
                    return callback(handlers)
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
                event.register(callback(invokerList))
                newList
            }

            list.add(e)
            return e
        }

        val fabricEventRegistry = mutableMapOf<FabricEvent<*>, MutableList<Event<*>>>()
    }
}