@file:Suppress("unused")

package top.katton.api.unsafe

import top.katton.engine.UnsafeInjectionManager
import top.katton.util.Event
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Experimental `unsafe` dynamic injection API.
 *
 * Design goals:
 * - Allow scripts to attach before/after hooks to arbitrary methods at runtime.
 * - Support rollback by handle and by owner, compatible with hot reload.
 * - Keep script-side usage concise.
 *
 * Key behaviors:
 * - This API does not add extra safety restrictions (no whitelist / permission gate).
 * - Runtime hook failures are caught and logged by the engine without breaking the target call chain.
 * - Injection dispatch is keyed by [Method].
 */
class UnsafeHandle internal constructor(
    /** Injection registration id, used for rollback. */
    val id: String
)

/**
 * Invocation context passed to unsafe callbacks.
 */
class UnsafeInvocationContext internal constructor(
    private val delegate: UnsafeInjectionManager.UnsafeInvocation
) {
    /** Method currently being invoked. */
    val method: Method get() = delegate.method

    /** Method receiver instance, or `null` for static methods. */
    val instance: Any? get() = delegate.instance

    /** Raw argument array. */
    val arguments: Array<Any?> get() = delegate.arguments

    /** Bound script owner for this invocation. */
    val owner: String? get() = delegate.owner
}

/**
 * Constructor invocation context passed to unsafe constructor callbacks.
 */
class UnsafeConstructorInvocationContext internal constructor(
    private val delegate: UnsafeInjectionManager.UnsafeConstructorInvocation
) {
    /** Constructor currently being invoked. */
    val constructor: Constructor<*> get() = delegate.constructor

    /** Constructed instance (`this`) when available. */
    val instance: Any? get() = delegate.instance

    /** Raw constructor argument array. */
    val arguments: Array<Any?> get() = delegate.arguments

    /** Bound script owner for this invocation. */
    val owner: String? get() = delegate.owner
}

/**
 * Compatibility helper: returns JVM property `katton.unsafe.enable`.
 *
 * Note: current unsafe implementation does not strictly require this flag.
 */
fun unsafeEnabled(): Boolean = UnsafeInjectionManager.isEnabled()

/**
 * Resolves effective owner:
 * - Prefer explicit owner if provided.
 * - Otherwise use current script execution owner.
 */
private fun effectiveOwner(explicitOwner: String?): String? {
    return explicitOwner ?: Event.currentScriptOwner()
}

/**
 * Injects a callback before target method execution (string-based overload).
 *
 * @param targetClassName target class fully-qualified name
 * @param methodName target method name
 * @param parameterTypeNames parameter type names, e.g. `int`, `java.lang.String`
 * @param owner script owner (nullable, auto-resolved from script context if null)
 * @param handler before callback
 * @return injection handle, usable by [rollbackUnsafe]
 */
fun injectBefore(
    targetClassName: String,
    methodName: String,
    parameterTypeNames: List<String> = emptyList(),
    owner: String? = null,
    handler: (UnsafeInvocationContext) -> Unit
): UnsafeHandle {
    val h = UnsafeInjectionManager.injectBefore(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        methodName = methodName,
        parameterTypeNames = parameterTypeNames
    ) { invocation ->
        handler(UnsafeInvocationContext(invocation))
    }
    return UnsafeHandle(h.id)
}

/**
 * Injects a callback before target method execution (Method overload).
 *
 * Prefer this overload when a reflected [Method] is already available,
 * to avoid string-signature assembly errors.
 */
fun injectBefore(
    method: Method,
    owner: String? = null,
    handler: (UnsafeInvocationContext) -> Unit
): UnsafeHandle {
    val h = UnsafeInjectionManager.injectBefore(
        owner = effectiveOwner(owner),
        method = method
    ) { invocation ->
        handler(UnsafeInvocationContext(invocation))
    }
    return UnsafeHandle(h.id)
}

/**
 * Injects a callback after target method execution (string-based overload).
 *
 * @param targetClassName target class fully-qualified name
 * @param methodName target method name
 * @param parameterTypeNames parameter type names, e.g. `int`, `java.lang.String`
 * @param owner script owner (nullable, auto-resolved from script context if null)
 * @param handler after callback with `result` and `throwable`
 * @return injection handle, usable by [rollbackUnsafe]
 */
fun injectAfter(
    targetClassName: String,
    methodName: String,
    parameterTypeNames: List<String> = emptyList(),
    owner: String? = null,
    handler: (UnsafeInvocationContext, Any?, Throwable?) -> Unit
): UnsafeHandle {
    val h = UnsafeInjectionManager.injectAfter(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        methodName = methodName,
        parameterTypeNames = parameterTypeNames
    ) { invocation, result, throwable ->
        handler(UnsafeInvocationContext(invocation), result, throwable)
    }
    return UnsafeHandle(h.id)
}

/**
 * Injects a callback before constructor execution (string-based overload).
 *
 * @param targetClassName target class fully-qualified name
 * @param parameterTypeNames constructor parameter type names, e.g. `int`, `java.lang.String`
 * @param owner script owner (nullable, auto-resolved from script context if null)
 * @param handler constructor-before callback
 * @return injection handle, usable by [rollbackUnsafe]
 */
fun injectConstructorBefore(
    targetClassName: String,
    parameterTypeNames: List<String> = emptyList(),
    owner: String? = null,
    handler: (UnsafeConstructorInvocationContext) -> Unit
): UnsafeHandle {
    val h = UnsafeInjectionManager.injectConstructorBefore(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        parameterTypeNames = parameterTypeNames
    ) { invocation ->
        handler(UnsafeConstructorInvocationContext(invocation))
    }
    return UnsafeHandle(h.id)
}

/**
 * Injects a callback before constructor execution (Constructor overload).
 */
fun injectConstructorBefore(
    constructor: Constructor<*>,
    owner: String? = null,
    handler: (UnsafeConstructorInvocationContext) -> Unit
): UnsafeHandle {
    val h = UnsafeInjectionManager.injectConstructorBefore(
        owner = effectiveOwner(owner),
        constructor = constructor
    ) { invocation ->
        handler(UnsafeConstructorInvocationContext(invocation))
    }
    return UnsafeHandle(h.id)
}

/**
 * Injects a callback after constructor execution (string-based overload).
 *
 * @param targetClassName target class fully-qualified name
 * @param parameterTypeNames constructor parameter type names, e.g. `int`, `java.lang.String`
 * @param owner script owner (nullable, auto-resolved from script context if null)
 * @param handler constructor-after callback
 * @return injection handle, usable by [rollbackUnsafe]
 */
fun injectConstructorAfter(
    targetClassName: String,
    parameterTypeNames: List<String> = emptyList(),
    owner: String? = null,
    handler: (UnsafeConstructorInvocationContext) -> Unit
): UnsafeHandle {
    val h = UnsafeInjectionManager.injectConstructorAfter(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        parameterTypeNames = parameterTypeNames
    ) { invocation ->
        handler(UnsafeConstructorInvocationContext(invocation))
    }
    return UnsafeHandle(h.id)
}

/**
 * Injects a callback after constructor execution (Constructor overload).
 */
fun injectConstructorAfter(
    constructor: Constructor<*>,
    owner: String? = null,
    handler: (UnsafeConstructorInvocationContext) -> Unit
): UnsafeHandle {
    val h = UnsafeInjectionManager.injectConstructorAfter(
        owner = effectiveOwner(owner),
        constructor = constructor
    ) { invocation ->
        handler(UnsafeConstructorInvocationContext(invocation))
    }
    return UnsafeHandle(h.id)
}

/**
 * Injects a callback after target method execution (Method overload).
 *
 * Prefer this overload when a reflected [Method] is already available.
 */
fun injectAfter(
    method: Method,
    owner: String? = null,
    handler: (UnsafeInvocationContext, Any?, Throwable?) -> Unit
): UnsafeHandle {
    val h = UnsafeInjectionManager.injectAfter(
        owner = effectiveOwner(owner),
        method = method
    ) { invocation, result, throwable ->
        handler(UnsafeInvocationContext(invocation), result, throwable)
    }
    return UnsafeHandle(h.id)
}

/**
 * Rolls back one unsafe injection by handle.
 */
fun rollbackUnsafe(handle: UnsafeHandle): Boolean {
    return UnsafeInjectionManager.rollback(handle.id)
}

/**
 * Rolls back all unsafe injections by owner.
 */
fun rollbackUnsafeByOwner(owner: String): Unit {
    UnsafeInjectionManager.rollbackByOwner(owner)
}

