@file:Suppress("unused")

package top.katton.api.inject

import top.katton.engine.InjectionManager
import top.katton.util.ScriptExecutionContext
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Unsafe injection API for runtime method interception.
 *
 * %zh
 * 运行时方法拦截的危险注入 API。
 *
 * 这个模块提供了在运行时向任意 Java 方法注入自定义代码的底层能力。
 * 它很强大，但也有风险，使用时要格外谨慎。
 *
 * 主要功能：
 * - 方法前置/后置注入
 * - 构造器注入
 * - 方法替换
 * - 方法重定向
 *
 * **警告**：不当使用这个 API 可能导致崩溃、数据损坏或其他异常行为。
 * 请务必充分测试，并配合合适的错误处理。
 */

/**
 * 注册注入的句柄，用于回滚操作。
 *
 * @property id 注入注册 ID，用于回滚。
 */
class InjectionHandle internal constructor(
    val id: String
)

/**
 * 传给不安全回调的调用上下文。
 *
 * 可访问正在调用的方法、接收实例、参数，并控制调用流程。
 *
 * @property delegate 底层的注入调用委托。
 */
class InjectionInvocationContext internal constructor(
    private val delegate: InjectionManager.InjectionInvocation
) {
    /**
     * 当前正在调用的方法。
     */
    val method: Method get() = delegate.method

    /**
     * 方法接收实例；对于静态方法则为 `null`。
     */
    val instance: Any? get() = delegate.instance

    /**
     * 原始参数数组。
     */
    val arguments: Array<Any?> get() = delegate.arguments

    /**
     * 这次调用绑定的脚本归属。
     */
    val owner: String? get() = delegate.owner

    /**
     * 修改当前调用中指定位置的参数。
     *
     * @param index 要修改的参数下标。
     * @param value 参数的新值。
     */
    fun setArgument(index: Int, value: Any?) {
        delegate.setArgument(index, value)
    }

    /**
     * 取消当前调用。若未覆写返回值，则使用该类型的默认值。
     */
    fun cancel() {
        delegate.cancel()
    }

    /**
     * 取消当前调用，并立即指定返回值。
     *
     * @param returnValue 用来替代方法执行结果的返回值。
     */
    fun cancelWith(returnValue: Any?) {
        delegate.cancelWith(returnValue)
    }

    /**
     * 在 after 阶段覆写返回值。
     *
     * @param returnValue 用来替代原始结果的返回值。
     */
    fun setReturnValue(returnValue: Any?) {
        delegate.setReturnValue(returnValue)
    }
}

/**
 * 传给不安全构造器回调的构造器调用上下文。
 *
 * @property delegate 底层的构造器调用委托。
 */
class ConstructorInvocationContext internal constructor(
    private val delegate: InjectionManager.ConstructorInvocation
) {
    /**
     * 当前正在调用的构造器。
     */
    val constructor: Constructor<*> get() = delegate.constructor

    /**
     * 已构造的实例（`this`），如果可用。
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
    return explicitOwner ?: ScriptExecutionContext.currentScriptOwner()
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

