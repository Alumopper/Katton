@file:Suppress("unused")

package top.katton.api.inject

import top.katton.engine.InjectionManager
import top.katton.util.Event
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Unsafe injection API for runtime method interception.
 *
 * This module provides low-level access to inject custom code into arbitrary
 * Java methods at runtime. This is a powerful but potentially dangerous feature
 * that should be used with caution.
 *
 * Key features:
 * - Before/after method injection
 * - Constructor injection
 * - Method replacement
 * - Method redirection
 *
 * **Warning**: Improper use of this API can cause crashes, data corruption,
 * or other unexpected behavior. Always test thoroughly and use appropriate
 * error handling.
 */

/**
 * Handle for a registered injection, used for rollback operations.
 *
 * @property id Injection registration id, used for rollback
 */
class InjectionHandle internal constructor(
    val id: String
)

/**
 * Invocation context passed to unsafe callbacks.
 *
 * Provides access to the method being invoked, the receiver instance,
 * arguments, and control over the invocation flow.
 *
 * @property delegate The underlying injection invocation delegate
 */
class InjectionInvocationContext internal constructor(
    private val delegate: InjectionManager.InjectionInvocation
) {
    /**
     * Method currently being invoked.
     */
    val method: Method get() = delegate.method

    /**
     * Method receiver instance, or `null` for static methods.
     */
    val instance: Any? get() = delegate.instance

    /**
     * Raw argument array.
     */
    val arguments: Array<Any?> get() = delegate.arguments

    /**
     * Bound script owner for this invocation.
     */
    val owner: String? get() = delegate.owner

    /**
     * Mutates argument at [index] for current invocation.
     *
     * @param index The argument index to modify
     * @param value The new value for the argument
     */
    fun setArgument(index: Int, value: Any?) {
        delegate.setArgument(index, value)
    }

    /**
     * Cancels current invocation. Return value becomes type default if not overridden.
     */
    fun cancel() {
        delegate.cancel()
    }

    /**
     * Cancels current invocation and overrides return value immediately.
     *
     * @param returnValue The value to return instead of executing the method
     */
    fun cancelWith(returnValue: Any?) {
        delegate.cancelWith(returnValue)
    }

    /**
     * Overrides return value in after phase.
     *
     * @param returnValue The value to return instead of the original result
     */
    fun setReturnValue(returnValue: Any?) {
        delegate.setReturnValue(returnValue)
    }
}

/**
 * Constructor invocation context passed to unsafe constructor callbacks.
 *
 * @property delegate The underlying constructor invocation delegate
 */
class ConstructorInvocationContext internal constructor(
    private val delegate: InjectionManager.ConstructorInvocation
) {
    /**
     * Constructor currently being invoked.
     */
    val constructor: Constructor<*> get() = delegate.constructor

    /**
     * Constructed instance (`this`) when available.
     */
    val instance: Any? get() = delegate.instance

    /**
     * Raw constructor argument array.
     */
    val arguments: Array<Any?> get() = delegate.arguments

    /**
     * Bound script owner for this invocation.
     */
    val owner: String? get() = delegate.owner
}

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
    handler: (InjectionInvocationContext) -> Unit
): InjectionHandle {
    val h = InjectionManager.injectBefore(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        methodName = methodName,
        parameterTypeNames = parameterTypeNames
    ) { invocation ->
        handler(InjectionInvocationContext(invocation))
    }
    return InjectionHandle(h.id)
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
    handler: (InjectionInvocationContext) -> Unit
): InjectionHandle {
    val h = InjectionManager.injectBefore(
        owner = effectiveOwner(owner),
        method = method
    ) { invocation ->
        handler(InjectionInvocationContext(invocation))
    }
    return InjectionHandle(h.id)
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
    handler: (InjectionInvocationContext, Any?, Throwable?) -> Unit
): InjectionHandle {
    val h = InjectionManager.injectAfter(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        methodName = methodName,
        parameterTypeNames = parameterTypeNames
    ) { invocation, result, throwable ->
        handler(InjectionInvocationContext(invocation), result, throwable)
    }
    return InjectionHandle(h.id)
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
    handler: (ConstructorInvocationContext) -> Unit
): InjectionHandle {
    val h = InjectionManager.injectConstructorBefore(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        parameterTypeNames = parameterTypeNames
    ) { invocation ->
        handler(ConstructorInvocationContext(invocation))
    }
    return InjectionHandle(h.id)
}

/**
 * Injects a callback before constructor execution (Constructor overload).
 */
fun injectConstructorBefore(
    constructor: Constructor<*>,
    owner: String? = null,
    handler: (ConstructorInvocationContext) -> Unit
): InjectionHandle {
    val h = InjectionManager.injectConstructorBefore(
        owner = effectiveOwner(owner),
        constructor = constructor
    ) { invocation ->
        handler(ConstructorInvocationContext(invocation))
    }
    return InjectionHandle(h.id)
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
    handler: (ConstructorInvocationContext) -> Unit
): InjectionHandle {
    val h = InjectionManager.injectConstructorAfter(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        parameterTypeNames = parameterTypeNames
    ) { invocation ->
        handler(ConstructorInvocationContext(invocation))
    }
    return InjectionHandle(h.id)
}

/**
 * Injects a callback after constructor execution (Constructor overload).
 */
fun injectConstructorAfter(
    constructor: Constructor<*>,
    owner: String? = null,
    handler: (ConstructorInvocationContext) -> Unit
): InjectionHandle {
    val h = InjectionManager.injectConstructorAfter(
        owner = effectiveOwner(owner),
        constructor = constructor
    ) { invocation ->
        handler(ConstructorInvocationContext(invocation))
    }
    return InjectionHandle(h.id)
}

/**
 * Injects a callback after target method execution (Method overload).
 *
 * Prefer this overload when a reflected [Method] is already available.
 *
 * @param method The target Method to inject into
 * @param owner Script owner (nullable, auto-resolved from script context if null)
 * @param handler After callback with result and throwable
 * @return Injection handle, usable by [rollbackUnsafe]
 */
fun injectAfter(
    method: Method,
    owner: String? = null,
    handler: (InjectionInvocationContext, Any?, Throwable?) -> Unit
): InjectionHandle {
    val h = InjectionManager.injectAfter(
        owner = effectiveOwner(owner),
        method = method
    ) { invocation, result, throwable ->
        handler(InjectionInvocationContext(invocation), result, throwable)
    }
    return InjectionHandle(h.id)
}

/**
 * Replaces entire target method body (string-based overload).
 *
 * The handler return value becomes the method return value.
 * This completely bypasses the original method implementation.
 *
 * @param targetClassName Target class fully-qualified name
 * @param methodName Target method name
 * @param parameterTypeNames Parameter type names, e.g. `int`, `java.lang.String`
 * @param owner Script owner (nullable, auto-resolved from script context if null)
 * @param handler Replacement handler that returns the method result
 * @return Injection handle, usable by [rollbackUnsafe]
 */
fun replace(
    targetClassName: String,
    methodName: String,
    parameterTypeNames: List<String> = emptyList(),
    owner: String? = null,
    handler: (InjectionInvocationContext) -> Any?
): InjectionHandle {
    val h = InjectionManager.injectReplace(
        owner = effectiveOwner(owner),
        targetClassName = targetClassName,
        methodName = methodName,
        parameterTypeNames = parameterTypeNames
    ) { invocation ->
        handler(InjectionInvocationContext(invocation))
    }
    return InjectionHandle(h.id)
}

/**
 * Replaces entire target method body (Method overload).
 *
 * @param method The target Method to replace
 * @param owner Script owner (nullable, auto-resolved from script context if null)
 * @param handler Replacement handler that returns the method result
 * @return Injection handle, usable by [rollbackUnsafe]
 */
fun replace(
    method: Method,
    owner: String? = null,
    handler: (InjectionInvocationContext) -> Any?
): InjectionHandle {
    val h = InjectionManager.injectReplace(
        owner = effectiveOwner(owner),
        method = method
    ) { invocation ->
        handler(InjectionInvocationContext(invocation))
    }
    return InjectionHandle(h.id)
}

/**
 * Redirects a source method to another target method (string-based overload).
 *
 * All calls to the source method will be redirected to the target method instead.
 *
 * @param sourceClassName Source class fully-qualified name
 * @param sourceMethodName Source method name to redirect from
 * @param sourceParameterTypeNames Source method parameter type names
 * @param targetClassName Target class fully-qualified name
 * @param targetMethodName Target method name to redirect to
 * @param targetParameterTypeNames Target method parameter type names
 * @param owner Script owner (nullable, auto-resolved from script context if null)
 * @return Injection handle, usable by [rollbackUnsafe]
 */
fun redirect(
    sourceClassName: String,
    sourceMethodName: String,
    sourceParameterTypeNames: List<String> = emptyList(),
    targetClassName: String,
    targetMethodName: String,
    targetParameterTypeNames: List<String> = emptyList(),
    owner: String? = null
): InjectionHandle {
    val h = InjectionManager.injectRedirect(
        owner = effectiveOwner(owner),
        sourceClassName = sourceClassName,
        sourceMethodName = sourceMethodName,
        sourceParameterTypeNames = sourceParameterTypeNames,
        targetClassName = targetClassName,
        targetMethodName = targetMethodName,
        targetParameterTypeNames = targetParameterTypeNames
    )
    return InjectionHandle(h.id)
}

/**
 * Redirects a source method to another target method (Method overload).
 *
 * @param sourceMethod The source Method to redirect from
 * @param targetMethod The target Method to redirect to
 * @param owner Script owner (nullable, auto-resolved from script context if null)
 * @return Injection handle, usable by [rollbackUnsafe]
 */
fun redirect(
    sourceMethod: Method,
    targetMethod: Method,
    owner: String? = null
): InjectionHandle {
    val h = InjectionManager.injectRedirect(
        owner = effectiveOwner(owner),
        sourceMethod = sourceMethod,
        targetMethod = targetMethod
    )
    return InjectionHandle(h.id)
}

/**
 * Rolls back one unsafe injection by handle.
 *
 * @param handle The injection handle to roll back
 * @return true if the injection was found and removed, false otherwise
 */
fun rollbackUnsafe(handle: InjectionHandle): Boolean {
    return InjectionManager.rollback(handle.id)
}

/**
 * Rolls back all unsafe injections by owner.
 *
 * @param owner The script owner whose injections should be removed
 */
fun rollbackUnsafeByOwner(owner: String) {
    InjectionManager.rollbackByOwner(owner)
}

