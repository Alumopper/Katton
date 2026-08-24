@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package top.katton.util

import net.minecraft.util.TriState
import org.slf4j.LoggerFactory
import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope
import top.katton.util.Extension.returnIfNot
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Invoker strategy: receives the full [EventHandler] array so that metadata
 * (scope) is available at dispatch time without extra allocations.
 */
private typealias EventInvoker<Arg, R> = (Array<EventHandler<Arg, R>>) -> (Arg) -> R

private val LOGGER = LoggerFactory.getLogger("top.katton.util.Event")
private val NO_HANDLER_RESULT: Result<Nothing> = Result.failure("No handler")

/** Immutable publication unit; rebuilding the dispatcher only on mutation avoids a lambda allocation per event. */
private class EventDispatchState<Arg, R>(
    val entries: Array<EventHandler<Arg, R>>,
    val dispatch: ((Arg) -> R)?
)

private fun <Arg, R> buildDispatchState(
    entries: Array<EventHandler<Arg, R>>,
    invoker: EventInvoker<Arg, R>
): EventDispatchState<Arg, R> {
    if (entries.isEmpty()) return EventDispatchState(entries, null)
    val dispatch = runCatching { invoker(entries) }
        // Custom event strategies used to fail during invocation, not registration.
        .getOrElse { failure -> { _: Arg -> throw failure } }
    return EventDispatchState(entries, dispatch)
}

@Suppress("UNCHECKED_CAST")
private fun <R> noHandlerResult(): Result<R> = NO_HANDLER_RESULT as Result<R>

fun <B> unit(): EventInvoker<B, Unit> = { events ->
    { arg: B -> events.forEach { e -> e(arg) } }
}

fun <B, R> firstNotNullOfOrNull(): EventInvoker<B, R?> = { events ->
    { arg: B -> events.firstNotNullOfOrNull { e -> e(arg) } }
}

fun <B> all(): EventInvoker<B, Boolean> = { events ->
    { arg: B -> events.all { e -> e(arg) } }
}

fun <B> any(): EventInvoker<B, Boolean> = { events ->
    { arg: B -> events.any { e -> e(arg) } }
}

internal fun <B, R> returnIfNot(passValue: R, returnValue: R?): EventInvoker<B, R?> = { events ->
    { arg: B -> events.returnIfNot(passValue, returnValue) { e -> e(arg) } }
}

internal fun <B, R> returnIfNot(passValue: R): EventInvoker<B, R> = { events ->
    { arg: B -> events.returnIfNot(passValue, passValue) { e -> e(arg) } !!}
}

fun <B> triState(): EventInvoker<B, TriState> = { events ->
    { arg: B ->
        var status = TriState.DEFAULT
        for (e in events) {
            status = e(arg)
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
    private class CancellationState(var cancelled: Boolean = false)

    // Paper/Folia can dispatch the same Katton event object concurrently on
    // different region threads. A small per-thread stack also handles nested
    // dispatch of the same event without the inner reset erasing outer state.
    private val invocationStates = ThreadLocal.withInitial { ArrayDeque<CancellationState>() }
    private val lastCompletedState = ThreadLocal.withInitial { false }

    fun cancel() {
        val current = invocationStates.get().lastOrNull()
        if (current != null) current.cancelled = true else lastCompletedState.set(true)
    }

    fun isCanceled(): Boolean {
        return invocationStates.get().lastOrNull()?.cancelled ?: lastCompletedState.get()
    }

    protected fun beginInvocation() {
        invocationStates.get().addLast(CancellationState())
    }

    protected fun finishInvocation() {
        val states = invocationStates.get()
        val completed = states.removeLastOrNull()?.cancelled ?: false
        lastCompletedState.set(completed)
        if (states.isEmpty()) invocationStates.remove()
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
 * @property owner The script class that registered this handler.
 * @property environment The client or server entrypoint environment that registered this handler.
 */
data class EventHandler<Arg, R>(
    val handler: (Arg) -> R,
    val scope: ScriptPackScope? = null,
    val owner: String? = null,
    val environment: ScriptEnvironment? = null
) {
    operator fun invoke(arg: Arg): R =
        ScriptExecutionContext.withEnvironment(environment) {
            ScriptExecutionContext.withScope(scope) {
                ScriptExecutionContext.withOwner(owner) {
                    handler(arg)
                }
            }
        }
}

interface Event<Arg, R> {
    fun clear()

    fun clearByScope(scope: ScriptPackScope)

    fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment)

    fun clearByOwnerPrefix(ownerPrefix: String)

    fun hasHandlers(): Boolean

    operator fun invoke(arg: Arg): Result<R>

    operator fun plusAssign(h: (Arg) -> R)

    companion object {
        // Script code can create custom DelegateEvent instances. Holding those
        // strongly here would pin every obsolete hot-reload classloader. Platform
        // singleton events remain strongly owned by their bridge objects.
        private val registry = CopyOnWriteArrayList<WeakReference<Event<*, *>>>()

        internal fun register(event: Event<*, *>) {
            registry.add(WeakReference(event))
        }

        private inline fun forEachLive(action: (Event<*, *>) -> Unit) {
            var containsClearedReference = false
            registry.forEach { reference ->
                val event = reference.get()
                if (event == null) containsClearedReference = true else action(event)
            }
            if (containsClearedReference) {
                // CopyOnWriteArrayList copies once for removeIf; removing each
                // dead reference individually becomes quadratic after many reloads.
                registry.removeIf { reference -> reference.get() == null }
            }
        }

        @JvmStatic
        fun clearHandlers(){
            forEachLive { it.clear() }
        }

        @JvmStatic
        fun clearHandlersByScope(scope: ScriptPackScope) {
            forEachLive { it.clearByScope(scope) }
        }

        @JvmStatic
        fun clearHandlersByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
            forEachLive { it.clearByScopeAndEnvironment(scope, environment) }
        }


        @JvmStatic
        fun clearHandlersByOwnerPrefix(ownerPrefix: String) {
            forEachLive { it.clearByOwnerPrefix(ownerPrefix) }
        }
    }
}

class DelegateEvent<Arg, R>(val invoker: EventInvoker<Arg, R>): Event<Arg, R> {

    init {
        Event.register(this)
    }

    @Synchronized
    override fun clear() {
        entries = emptyArray()
    }

    @Synchronized
    override fun clearByScope(scope: ScriptPackScope) {
        val es = entries
        if (es.isEmpty()) return
        entries = es.filter { it.scope != scope }.toTypedArray()
    }

    @Synchronized
    override fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
        val es = entries
        if (es.isEmpty()) return
        entries = es.filter { it.scope != scope || it.environment != environment }.toTypedArray()
    }

    @Synchronized
    override fun clearByOwnerPrefix(ownerPrefix: String) {
        val es = entries
        if (es.isEmpty()) return
        entries = es.filter { it.owner?.startsWith(ownerPrefix) != true }.toTypedArray()
    }

    override fun hasHandlers(): Boolean = dispatchState.entries.isNotEmpty()

    @Volatile
    private var dispatchState = buildDispatchState(emptyArray(), invoker)

    var entries: Array<EventHandler<Arg, R>>
        get() = dispatchState.entries
        set(value) {
            dispatchState = buildDispatchState(value, invoker)
        }

    @Synchronized
    override operator fun plusAssign(h: (Arg) -> R) {
        val old = entries
        val n = old.size
        val arr = java.util.Arrays.copyOf(old, n + 1)
        arr[n] = EventHandler(
            handler = h,
            scope = ScriptExecutionContext.currentScriptScope(),
            owner = ScriptExecutionContext.currentScriptOwner(),
            environment = ScriptExecutionContext.currentScriptEnvironment()
        )
        entries = arr
    }

    override operator fun invoke(arg: Arg): Result<R> {
        val dispatch = dispatchState.dispatch ?: return noHandlerResult()
        return try {
            Result.success(dispatch(arg))
        } catch (t: Throwable) {
            LOGGER.warn("Script event handler failed for {}", arg?.javaClass?.name ?: "null", t)
            Result.failure("Script event handler failed: ${t.message ?: t.javaClass.name}")
        }
    }
}

class CancellableDelegateEvent<Arg: CancellableEventArg, R>(val invoker: EventInvoker<Arg, R>): Cancellable(), Event<Arg, R> {

    init {
        Event.register(this)
    }

    @Synchronized
    override fun clear() {
        entries = emptyArray()
    }

    @Synchronized
    override fun clearByScope(scope: ScriptPackScope) {
        val es = entries
        if (es.isEmpty()) return
        entries = es.filter { it.scope != scope }.toTypedArray()
    }

    @Synchronized
    override fun clearByScopeAndEnvironment(scope: ScriptPackScope, environment: ScriptEnvironment) {
        val es = entries
        if (es.isEmpty()) return
        entries = es.filter { it.scope != scope || it.environment != environment }.toTypedArray()
    }

    @Synchronized
    override fun clearByOwnerPrefix(ownerPrefix: String) {
        val es = entries
        if (es.isEmpty()) return
        entries = es.filter { it.owner?.startsWith(ownerPrefix) != true }.toTypedArray()
    }

    override fun hasHandlers(): Boolean = dispatchState.entries.isNotEmpty()

    @Volatile
    private var dispatchState = buildDispatchState(emptyArray(), invoker)

    private var entries: Array<EventHandler<Arg, R>>
        get() = dispatchState.entries
        set(value) {
            dispatchState = buildDispatchState(value, invoker)
        }

    @Synchronized
    override operator fun plusAssign(h: (Arg) -> R) {
        val old = entries
        val n = old.size
        val arr = java.util.Arrays.copyOf(old, n + 1)
        arr[n] = EventHandler(
            handler = h,
            scope = ScriptExecutionContext.currentScriptScope(),
            owner = ScriptExecutionContext.currentScriptOwner(),
            environment = ScriptExecutionContext.currentScriptEnvironment()
        )
        entries = arr
    }

    override operator fun invoke(arg: Arg): Result<R> {
        beginInvocation()
        arg.event = this
        val dispatch = dispatchState.dispatch
        return try {
            if (dispatch == null) {
                noHandlerResult()
            } else {
                Result.success(dispatch(arg))
            }
        } catch (t: Throwable) {
            LOGGER.warn("Script cancellable event handler failed for {}", arg.javaClass.name, t)
            Result.failure("Script event handler failed: ${t.message ?: t.javaClass.name}")
        } finally {
            finishInvocation()
        }
    }
}
