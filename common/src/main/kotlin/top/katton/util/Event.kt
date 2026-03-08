@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package top.katton.util

import net.minecraft.util.TriState
import top.katton.util.Extension.returnIfNot

fun <B> unit(): (Array<(B) -> Unit>) -> (B) -> Unit = { events ->
    { arg: B -> events.forEach { e -> e(arg) } }
}

fun <B, R> firstNotNullOfOrNull(): (Array<(B) -> R?>) -> (B) -> R? = { events ->
    { arg: B -> events.firstNotNullOfOrNull { e -> e(arg) } }
}

fun <B> all(): (Array<(B) -> Boolean>) -> (B) -> Boolean = { events ->
    { arg: B -> events.all { e -> e(arg) } }
}

fun <B> any(): (Array<(B) -> Boolean>) -> (B) -> Boolean = { events ->
    { arg: B -> events.any { e -> e(arg) } }
}

internal fun <B, R> returnIfNot(passValue: R, returnValue: R?): (Array<(B) -> R>) -> (B) -> R? = { events ->
    { arg: B -> events.returnIfNot(passValue, returnValue) { e -> e(arg) } }
}

internal fun <B, R> returnIfNot(passValue: R): (Array<(B) -> R>) -> (B) -> R = { events ->
    { arg: B -> events.returnIfNot(passValue, passValue) { e -> e(arg) } !!}
}


fun <B> triState(): (Array<(B) -> TriState>) -> (B) -> TriState = { events ->
    { arg: B ->
        var status = TriState.DEFAULT
        for (e in events) {
            status = e(arg)
            if (status != TriState.DEFAULT) break
        }
        status
    }
}

fun <T, R> create(invoker: (Array<(T) -> R>) -> (T) -> R) = DelegateEvent(invoker)

fun <T> createUnit() = DelegateEvent<T, Unit>(unit())

fun <T: CancellableEventArg> createCancellableUnit() = CancellableDelegateEvent<T, Unit>(unit())

fun <T, R> createReturnIfNot(unexpectValue: R, returnValue: R?) = DelegateEvent<T, R?>(returnIfNot(unexpectValue, returnValue))

fun <T, R> createReturnIfNot(unexpectValue: R) = DelegateEvent<T, R>(returnIfNot(unexpectValue))

fun <T> createTriState() = DelegateEvent<T, TriState>(triState())

fun <T, R> createFirstNotNullOfOrNull() = DelegateEvent<T, R?>(firstNotNullOfOrNull())

fun <T> createAny() = DelegateEvent<T, Boolean>(any())

fun <T> createAll() = DelegateEvent<T, Boolean>(all())

abstract class Cancellable {
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun isCanceled(): Boolean {
        return cancelled
    }

    protected fun reset() {
        cancelled = false
    }

}

@Suppress("unused")
abstract class CancellableEventArg {
    lateinit var event: Cancellable

    fun cancel() {
        event.cancel()
    }

    fun isCancelled(): Boolean {
        return event.isCanceled()
    }
}

interface Event<Arg, R> {
    fun clear()

    operator fun invoke(arg: Arg): Result<R>

    operator fun plusAssign(h: (Arg) -> R)

    companion object {
        private val currentScriptOwner = ThreadLocal<String?>()

        fun currentScriptOwner(): String? = currentScriptOwner.get()

        val registry = ArrayList<Event<*, *>>()

        @JvmStatic
        fun clearHandlers(){
            for (event in registry) {
                event.clear()
            }
        }


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
    }
}

class DelegateEvent<Arg, R : Any?>(val invoker: (Array<(Arg) -> R>) -> (Arg) -> R): Event<Arg, R> {

    init {
        Event.registry.add(this)
    }

    override fun clear() {
        handlers = emptyArray()
    }

    @Volatile
    var handlers: Array<(Arg) -> R> = emptyArray()

    override operator fun plusAssign(h: (Arg) -> R) {
        val old = handlers
        val n = old.size
        val arr = java.util.Arrays.copyOf(old, n + 1)
        arr[n] = h
        handlers = arr
    }

    override operator fun invoke(arg: Arg): Result<R> {
        val hs = handlers
        if(hs.isEmpty()) return Result.failure("No handler")

        return Result.success(invoker(handlers).invoke(arg))
    }

    @JvmName("invoke")
    fun invokeJ(arg: Arg): JResult<R> {
        return JResult.from(invoke(arg))
    }
}

class CancellableDelegateEvent<Arg: CancellableEventArg, R>(val invoker: (Array<(Arg) -> R>) -> (Arg) -> R): Cancellable(), Event<Arg, R> {

    init {
        Event.registry.add(this)
    }

    override fun clear() {
        handlers = emptyArray()
    }


    @Volatile
    private var handlers: Array<(Arg) -> R> = emptyArray()

    override operator fun plusAssign(h: (Arg) -> R) {
        val old = handlers
        val n = old.size
        val arr = java.util.Arrays.copyOf(old, n + 1)
        arr[n] = h
        handlers = arr
    }

    override operator fun invoke(arg: Arg): Result<R> {
        reset()
        arg.event = this
        val hs = handlers
        if(hs.isEmpty()) return Result.failure("No handler")

        return Result.success(invoker(handlers).invoke(arg))
    }

    @JvmName("invoke")
    fun invokeJ(arg: Arg): JResult<out R?> {
        return JResult.from(invoke(arg))
    }
}