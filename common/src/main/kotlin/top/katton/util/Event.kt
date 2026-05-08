@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package top.katton.util

import net.minecraft.util.TriState
import top.katton.pack.ScriptPackScope
import top.katton.util.Extension.returnIfNot

/**
 * Invoker strategy: receives the full [EventHandler] array so that metadata
 * (scope) is available at dispatch time without extra allocations.
 */
private typealias EventInvoker<Arg, R> = (Array<EventHandler<(Arg) -> R>>) -> (Arg) -> R

fun <B> unit(): EventInvoker<B, Unit> = { events ->
    { arg: B -> events.forEach { e -> e.handler(arg) } }
}

fun <B, R> firstNotNullOfOrNull(): EventInvoker<B, R?> = { events ->
    { arg: B -> events.firstNotNullOfOrNull { e -> e.handler(arg) } }
}

fun <B> all(): EventInvoker<B, Boolean> = { events ->
    { arg: B -> events.all { e -> e.handler(arg) } }
}

fun <B> any(): EventInvoker<B, Boolean> = { events ->
    { arg: B -> events.any { e -> e.handler(arg) } }
}

internal fun <B, R> returnIfNot(passValue: R, returnValue: R?): EventInvoker<B, R?> = { events ->
    { arg: B -> events.returnIfNot(passValue, returnValue) { e -> e.handler(arg) } }
}

internal fun <B, R> returnIfNot(passValue: R): EventInvoker<B, R> = { events ->
    { arg: B -> events.returnIfNot(passValue, passValue) { e -> e.handler(arg) } !!}
}

fun <B> triState(): EventInvoker<B, TriState> = { events ->
    { arg: B ->
        var status = TriState.DEFAULT
        for (e in events) {
            status = e.handler(arg)
            if (status != TriState.DEFAULT) break
        }
        status
    }
}

fun <T, R> create(invoker: EventInvoker<T, R>) = DelegateEvent(invoker)

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

/**
 * Unified handler metadata for event callbacks.
 *
 * @property handler The actual callback function.
 * @property scope The script pack scope (e.g. GLOBAL, WORLD) this handler was registered under.
 */
data class EventHandler<T>(
    val handler: T,
    val scope: ScriptPackScope? = null
)

interface Event<Arg, R> {
    fun clear()

    fun clearByScope(scope: ScriptPackScope)

    operator fun invoke(arg: Arg): Result<R>

    operator fun plusAssign(h: (Arg) -> R)

    companion object {
        val registry = ArrayList<Event<*, *>>()

        @JvmStatic
        fun clearHandlers(){
            for (event in registry) {
                event.clear()
            }
        }

        @JvmStatic
        fun clearHandlersByScope(scope: ScriptPackScope) {
            for (event in registry) {
                event.clearByScope(scope)
            }
        }
    }
}

class DelegateEvent<Arg, R>(val invoker: EventInvoker<Arg, R>): Event<Arg, R> {

    init {
        Event.registry.add(this)
    }

    override fun clear() {
        entries = emptyArray()
    }

    override fun clearByScope(scope: ScriptPackScope) {
        val es = entries
        if (es.isEmpty()) return
        entries = es.filter { it.scope != scope }.toTypedArray()
    }

    @Volatile
    var entries: Array<EventHandler<(Arg) -> R>> = emptyArray()

    override operator fun plusAssign(h: (Arg) -> R) {
        val old = entries
        val n = old.size
        val arr = java.util.Arrays.copyOf(old, n + 1)
        arr[n] = EventHandler(
            handler = h,
            scope = ScriptExecutionContext.currentScriptScope()
        )
        entries = arr
    }

    override operator fun invoke(arg: Arg): Result<R> {
        val es = entries
        if (es.isEmpty()) return Result.failure("No handler")
        return Result.success(invoker(es).invoke(arg))
    }

    @JvmName("invoke")
    fun invokeJ(arg: Arg): JResult<R> {
        return JResult.from(invoke(arg))
    }
}

class CancellableDelegateEvent<Arg: CancellableEventArg, R>(val invoker: EventInvoker<Arg, R>): Cancellable(), Event<Arg, R> {

    init {
        Event.registry.add(this)
    }

    override fun clear() {
        entries = emptyArray()
    }

    override fun clearByScope(scope: ScriptPackScope) {
        val es = entries
        if (es.isEmpty()) return
        entries = es.filter { it.scope != scope }.toTypedArray()
    }

    @Volatile
    private var entries: Array<EventHandler<(Arg) -> R>> = emptyArray()

    override operator fun plusAssign(h: (Arg) -> R) {
        val old = entries
        val n = old.size
        val arr = java.util.Arrays.copyOf(old, n + 1)
        arr[n] = EventHandler(
            handler = h,
            scope = ScriptExecutionContext.currentScriptScope()
        )
        entries = arr
    }

    override operator fun invoke(arg: Arg): Result<R> {
        reset()
        arg.event = this
        val es = entries
        if (es.isEmpty()) return Result.failure("No handler")
        return Result.success(invoker(es).invoke(arg))
    }

    @JvmName("invoke")
    fun invokeJ(arg: Arg): JResult<out R?> {
        return JResult.from(invoke(arg))
    }
}
