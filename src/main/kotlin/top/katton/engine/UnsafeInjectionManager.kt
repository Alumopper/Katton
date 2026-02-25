package top.katton.engine

import com.mojang.logging.LogUtils
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.asm.Advice
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.bytecode.assign.Assigner
import net.bytebuddy.matcher.ElementMatchers
import top.katton.util.Event
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runtime method injection manager for `unsafe` APIs.
 *
 * Design (based on ByteBuddy Advice):
 * 1. When a method is injected for the first time, redefine its declaring class once.
 * 2. Weave a universal Advice bridge at method enter/exit.
 * 3. Route runtime calls to the manager's before/after handler registries.
 *
 * Notes:
 * - This is experimental and intentionally has no extra sandbox.
 * - The goal is dynamic hooks with rollback support across script reloads.
 *
 */
internal object UnsafeInjectionManager {
    private val logger = LogUtils.getLogger()

    /**
     * Snapshot of a target method invocation.
     *
     * @property method reflected method object being invoked
     * @property instance receiver instance; null for static methods
     * @property arguments raw argument array
     * @property owner script owner used for reload lifecycle management
     */
    data class UnsafeInvocation(
        val method: Method,
        val instance: Any?,
        val arguments: Array<Any?>,
        val owner: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UnsafeInvocation

            if (method != other.method) return false
            if (instance != other.instance) return false
            if (!arguments.contentEquals(other.arguments)) return false
            if (owner != other.owner) return false

            return true
        }

        override fun hashCode(): Int {
            var result = method.hashCode()
            result = 31 * result + (instance?.hashCode() ?: 0)
            result = 31 * result + arguments.contentHashCode()
            result = 31 * result + (owner?.hashCode() ?: 0)
            return result
        }

        private var enterControl: EnterControl? = null
        private var exitControl: ExitControl? = null

        internal fun bindEnter(control: EnterControl): UnsafeInvocation {
            this.enterControl = control
            return this
        }

        internal fun bindExit(control: ExitControl): UnsafeInvocation {
            this.exitControl = control
            return this
        }

        /** Mutates target argument at [index] for current invocation. */
        fun setArgument(index: Int, value: Any?) {
            arguments[index] = value
        }

        /** Cancels current method execution. */
        fun cancel() {
            val control = enterControl ?: return
            control.skip = true
        }

        /** Cancels current method execution and sets replacement return value. */
        fun cancelWith(returnValue: Any?) {
            val control = enterControl ?: return
            control.skip = true
            control.overrideReturn = true
            control.returnValue = returnValue
        }

        /** Overrides method return value in after phase. */
        fun setReturnValue(returnValue: Any?) {
            val control = exitControl ?: return
            control.overrideReturn = true
            control.returnValue = returnValue
        }
    }

    internal data class EnterControl(
        var skip: Boolean = false,
        var overrideReturn: Boolean = false,
        var returnValue: Any? = null
    )

    internal data class ExitControl(
        var overrideReturn: Boolean = false,
        var returnValue: Any? = null
    )

    internal data class EnterState(
        val returnValue: Any?
    )

    private data class BeforeEntry(
        val id: String,
        val owner: String?,
        val handler: (UnsafeInvocation) -> Unit
    )

    private data class AfterEntry(
        val id: String,
        val owner: String?,
        val handler: (UnsafeInvocation, Any?, Throwable?) -> Unit
    )

    private data class ReplaceEntry(
        val id: String,
        val owner: String?,
        val handler: (UnsafeInvocation) -> Any?
    )

    private data class RedirectEntry(
        val id: String,
        val owner: String?,
        val target: Method
    )

    data class UnsafeConstructorInvocation(
        val constructor: Constructor<*>,
        val instance: Any?,
        val arguments: Array<Any?>,
        val owner: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UnsafeConstructorInvocation

            if (constructor != other.constructor) return false
            if (instance != other.instance) return false
            if (!arguments.contentEquals(other.arguments)) return false
            if (owner != other.owner) return false

            return true
        }

        override fun hashCode(): Int {
            var result = constructor.hashCode()
            result = 31 * result + (instance?.hashCode() ?: 0)
            result = 31 * result + arguments.contentHashCode()
            result = 31 * result + (owner?.hashCode() ?: 0)
            return result
        }
    }

    private data class ConstructorBeforeEntry(
        val id: String,
        val owner: String?,
        val handler: (UnsafeConstructorInvocation) -> Unit
    )

    private data class ConstructorAfterEntry(
        val id: String,
        val owner: String?,
        val handler: (UnsafeConstructorInvocation) -> Unit
    )

    private data class HandleMeta(
        val id: String,
        val owner: String?,
        val targetKey: String,
        val phase: Phase
    )

    /** Injection phase: before or after target method execution. */
    enum class Phase {
        BEFORE,
        AFTER,
        REPLACE,
        REDIRECT,
        CONSTRUCTOR_BEFORE,
        CONSTRUCTOR_AFTER
    }

    /**
     * Injection handle returned to callers for precise rollback.
     */
    data class InjectionHandle(
        val id: String,
        val owner: String?,
        val targetClass: String,
        val targetMethod: String,
        val phase: Phase
    )

    private val instrumentedTargets = ConcurrentHashMap.newKeySet<String>()
    private val beforeHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<BeforeEntry>>()
    private val afterHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<AfterEntry>>()
    private val replaceHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<ReplaceEntry>>()
    private val redirectHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<RedirectEntry>>()
    private val constructorBeforeHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<ConstructorBeforeEntry>>()
    private val constructorAfterHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<ConstructorAfterEntry>>()
    private val handles = ConcurrentHashMap<String, HandleMeta>()

    @JvmStatic
    /**
     * Compatibility helper that reads the `katton.unsafe.enable` system property.
     *
     * The current implementation does not require this flag to be true.
     */
    fun isEnabled(): Boolean = java.lang.Boolean.getBoolean("katton.unsafe.enable")

    private fun targetKey(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.declaringClass.name}#${method.name}($params)"
    }

    private fun constructorKey(constructor: Constructor<*>): String {
        val params = constructor.parameterTypes.joinToString(",") { it.name }
        return "${constructor.declaringClass.name}#<init>($params)"
    }

    private fun findMethod(targetClass: Class<*>, methodName: String, parameterTypes: List<Class<*>>): Method {
        var current: Class<*>? = targetClass
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, *parameterTypes.toTypedArray()).also { it.isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        error("Method not found: ${targetClass.name}#$methodName(${parameterTypes.joinToString(",") { it.name }})")
    }

    private fun findConstructor(targetClass: Class<*>, parameterTypes: List<Class<*>>): Constructor<*> {
        return targetClass.getDeclaredConstructor(*parameterTypes.toTypedArray()).also { it.isAccessible = true }
    }

    private fun resolveType(name: String): Class<*> {
        return when (name) {
            "boolean" -> Boolean::class.javaPrimitiveType!!
            "byte" -> Byte::class.javaPrimitiveType!!
            "char" -> Char::class.javaPrimitiveType!!
            "short" -> Short::class.javaPrimitiveType!!
            "int" -> Int::class.javaPrimitiveType!!
            "long" -> Long::class.javaPrimitiveType!!
            "float" -> Float::class.javaPrimitiveType!!
            "double" -> Double::class.javaPrimitiveType!!
            "void" -> Void.TYPE
            else -> Class.forName(name)
        }
    }

    private fun defaultReturnValue(type: Class<*>): Any? {
        if (!type.isPrimitive || type == Void.TYPE) return null
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }

    @JvmStatic
    fun createEnterControl(): EnterControl = EnterControl()

    @JvmStatic
    fun createExitControl(): ExitControl = ExitControl()

    @JvmStatic
    fun defaultReturnValueFor(type: Class<*>): Any? = defaultReturnValue(type)

    private fun ensureInstrumented(targetClass: Class<*>, method: Method) {
        val key = targetKey(method)
        if (!instrumentedTargets.add(key)) return

        // Install agent lazily (reuses existing installation if present).
        ByteBuddyAgent.install()

        // Redefine target class and weave a universal Advice bridge.
        // Runtime dispatch is narrowed by Method key, so matching any() is fine here.
        ByteBuddy()
            .redefine(targetClass)
            .visit(
                Advice.to(UnsafeMethodAdvice::class.java).on(
                    ElementMatchers.not(ElementMatchers.isConstructor())
                        .and(ElementMatchers.not(ElementMatchers.isTypeInitializer()))
                )
            )
            .make()
            .load(targetClass.classLoader, ClassReloadingStrategy.fromInstalledAgent())

        logger.warn("[Katton Unsafe] Instrumented method: {}", key)
    }

    private fun ensureConstructorInstrumented(targetClass: Class<*>, constructor: Constructor<*>) {
        val key = constructorKey(constructor)
        if (!instrumentedTargets.add(key)) return

        ByteBuddyAgent.install()

        ByteBuddy()
            .redefine(targetClass)
            .visit(
                Advice.to(UniversalConstructorAdvice::class.java).on(ElementMatchers.isConstructor())
            )
            .make()
            .load(targetClass.classLoader, ClassReloadingStrategy.fromInstalledAgent())

        logger.warn("[Katton Unsafe] Instrumented constructor: {}", key)
    }

    @JvmStatic
    /**
     * String-based overload: resolve target method and inject a before handler.
     */
    fun injectBefore(
        owner: String?,
        targetClassName: String,
        methodName: String,
        parameterTypeNames: List<String>,
        handler: (UnsafeInvocation) -> Unit
    ): InjectionHandle {
        val targetClass = Class.forName(targetClassName)
        val parameterTypes = parameterTypeNames.map(::resolveType)
        val method = findMethod(targetClass, methodName, parameterTypes)
        return injectBefore(owner, method, handler)
    }

    @JvmStatic
    /**
     * Method-based overload: inject a before handler directly by [Method].
     */
    fun injectBefore(
        owner: String?,
        method: Method,
        handler: (UnsafeInvocation) -> Unit
    ): InjectionHandle {
        val targetClass = method.declaringClass
        ensureInstrumented(targetClass, method)

        val key = targetKey(method)
        val id = UUID.randomUUID().toString()
        beforeHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }
            .add(BeforeEntry(id, owner, handler))
        handles[id] = HandleMeta(id, owner, key, Phase.BEFORE)

        return InjectionHandle(id, owner, method.declaringClass.name, method.name, Phase.BEFORE)
    }

    @JvmStatic
    /**
     * String-based overload: resolve target method and inject an after handler.
     */
    fun injectAfter(
        owner: String?,
        targetClassName: String,
        methodName: String,
        parameterTypeNames: List<String>,
        handler: (UnsafeInvocation, Any?, Throwable?) -> Unit
    ): InjectionHandle {
        val targetClass = Class.forName(targetClassName)
        val parameterTypes = parameterTypeNames.map(::resolveType)
        val method = findMethod(targetClass, methodName, parameterTypes)
        return injectAfter(owner, method, handler)
    }

    @JvmStatic
    /**
     * Method-based overload: inject an after handler directly by [Method].
     */
    fun injectAfter(
        owner: String?,
        method: Method,
        handler: (UnsafeInvocation, Any?, Throwable?) -> Unit
    ): InjectionHandle {
        val targetClass = method.declaringClass
        ensureInstrumented(targetClass, method)

        val key = targetKey(method)
        val id = UUID.randomUUID().toString()
        afterHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }
            .add(AfterEntry(id, owner, handler))
        handles[id] = HandleMeta(id, owner, key, Phase.AFTER)

        return InjectionHandle(id, owner, method.declaringClass.name, method.name, Phase.AFTER)
    }

    @JvmStatic
    fun injectReplace(
        owner: String?,
        targetClassName: String,
        methodName: String,
        parameterTypeNames: List<String>,
        handler: (UnsafeInvocation) -> Any?
    ): InjectionHandle {
        val targetClass = Class.forName(targetClassName)
        val parameterTypes = parameterTypeNames.map(::resolveType)
        val method = findMethod(targetClass, methodName, parameterTypes)
        return injectReplace(owner, method, handler)
    }

    @JvmStatic
    fun injectReplace(
        owner: String?,
        method: Method,
        handler: (UnsafeInvocation) -> Any?
    ): InjectionHandle {
        val targetClass = method.declaringClass
        ensureInstrumented(targetClass, method)

        val key = targetKey(method)
        val id = UUID.randomUUID().toString()
        replaceHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }
            .add(ReplaceEntry(id, owner, handler))
        handles[id] = HandleMeta(id, owner, key, Phase.REPLACE)

        return InjectionHandle(id, owner, method.declaringClass.name, method.name, Phase.REPLACE)
    }

    @JvmStatic
    fun injectRedirect(
        owner: String?,
        sourceClassName: String,
        sourceMethodName: String,
        sourceParameterTypeNames: List<String>,
        targetClassName: String,
        targetMethodName: String,
        targetParameterTypeNames: List<String>
    ): InjectionHandle {
        val sourceClass = Class.forName(sourceClassName)
        val sourceParamTypes = sourceParameterTypeNames.map(::resolveType)
        val sourceMethod = findMethod(sourceClass, sourceMethodName, sourceParamTypes)

        val targetClass = Class.forName(targetClassName)
        val targetParamTypes = targetParameterTypeNames.map(::resolveType)
        val targetMethod = findMethod(targetClass, targetMethodName, targetParamTypes)

        return injectRedirect(owner, sourceMethod, targetMethod)
    }

    @JvmStatic
    fun injectRedirect(
        owner: String?,
        sourceMethod: Method,
        targetMethod: Method
    ): InjectionHandle {
        val targetClass = sourceMethod.declaringClass
        ensureInstrumented(targetClass, sourceMethod)

        val key = targetKey(sourceMethod)
        val id = UUID.randomUUID().toString()
        redirectHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }
            .add(RedirectEntry(id, owner, targetMethod.also { it.isAccessible = true }))
        handles[id] = HandleMeta(id, owner, key, Phase.REDIRECT)

        return InjectionHandle(id, owner, sourceMethod.declaringClass.name, sourceMethod.name, Phase.REDIRECT)
    }

    @JvmStatic
    fun injectConstructorBefore(
        owner: String?,
        targetClassName: String,
        parameterTypeNames: List<String>,
        handler: (UnsafeConstructorInvocation) -> Unit
    ): InjectionHandle {
        val targetClass = Class.forName(targetClassName)
        val parameterTypes = parameterTypeNames.map(::resolveType)
        val constructor = findConstructor(targetClass, parameterTypes)
        return injectConstructorBefore(owner, constructor, handler)
    }

    @JvmStatic
    fun injectConstructorBefore(
        owner: String?,
        constructor: Constructor<*>,
        handler: (UnsafeConstructorInvocation) -> Unit
    ): InjectionHandle {
        val targetClass = constructor.declaringClass
        ensureConstructorInstrumented(targetClass, constructor)

        val key = constructorKey(constructor)
        val id = UUID.randomUUID().toString()
        constructorBeforeHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }
            .add(ConstructorBeforeEntry(id, owner, handler))
        handles[id] = HandleMeta(id, owner, key, Phase.CONSTRUCTOR_BEFORE)

        return InjectionHandle(id, owner, constructor.declaringClass.name, "<init>", Phase.CONSTRUCTOR_BEFORE)
    }

    @JvmStatic
    fun injectConstructorAfter(
        owner: String?,
        targetClassName: String,
        parameterTypeNames: List<String>,
        handler: (UnsafeConstructorInvocation) -> Unit
    ): InjectionHandle {
        val targetClass = Class.forName(targetClassName)
        val parameterTypes = parameterTypeNames.map(::resolveType)
        val constructor = findConstructor(targetClass, parameterTypes)
        return injectConstructorAfter(owner, constructor, handler)
    }

    @JvmStatic
    fun injectConstructorAfter(
        owner: String?,
        constructor: Constructor<*>,
        handler: (UnsafeConstructorInvocation) -> Unit
    ): InjectionHandle {
        val targetClass = constructor.declaringClass
        ensureConstructorInstrumented(targetClass, constructor)

        val key = constructorKey(constructor)
        val id = UUID.randomUUID().toString()
        constructorAfterHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }
            .add(ConstructorAfterEntry(id, owner, handler))
        handles[id] = HandleMeta(id, owner, key, Phase.CONSTRUCTOR_AFTER)

        return InjectionHandle(id, owner, constructor.declaringClass.name, "<init>", Phase.CONSTRUCTOR_AFTER)
    }

    @JvmStatic
    /**
     * Roll back a single injection by handle id.
     */
    fun rollback(handleId: String): Boolean {
        val meta = handles.remove(handleId) ?: return false
        when (meta.phase) {
            Phase.BEFORE -> beforeHandlers[meta.targetKey]?.removeIf { it.id == meta.id }
            Phase.AFTER -> afterHandlers[meta.targetKey]?.removeIf { it.id == meta.id }
            Phase.REPLACE -> replaceHandlers[meta.targetKey]?.removeIf { it.id == meta.id }
            Phase.REDIRECT -> redirectHandlers[meta.targetKey]?.removeIf { it.id == meta.id }
            Phase.CONSTRUCTOR_BEFORE -> constructorBeforeHandlers[meta.targetKey]?.removeIf { it.id == meta.id }
            Phase.CONSTRUCTOR_AFTER -> constructorAfterHandlers[meta.targetKey]?.removeIf { it.id == meta.id }
        }
        return true
    }

    @JvmStatic
    /**
     * Roll back all injections belonging to the given owner.
     */
    fun rollbackByOwner(owner: String) {
        handles.values.filter { it.owner == owner }.map { it.id }.forEach(::rollback)
    }

    @JvmStatic
    /**
     * Called before script reload to clear all dynamic injection registries.
     */
    fun beginReload() {
        handles.clear()
        beforeHandlers.clear()
        afterHandlers.clear()
        replaceHandlers.clear()
        redirectHandlers.clear()
        constructorBeforeHandlers.clear()
        constructorAfterHandlers.clear()
    }

    @JvmStatic
    /**
     * Advice enter dispatcher.
     */
    fun dispatchBefore(method: Method, instance: Any?, args: Array<Any?>, enterControl: EnterControl) {
        val key = targetKey(method)

        val replaceEntry = replaceHandlers[key]?.lastOrNull()
        if (replaceEntry != null) {
            runCatching {
                Event.withScriptOwner(replaceEntry.owner) {
                    val invocation = UnsafeInvocation(method, instance, args, replaceEntry.owner).bindEnter(enterControl)
                    val replaced = replaceEntry.handler(invocation)
                    invocation.cancelWith(replaced)
                }
            }.onFailure {
                logger.error("[Katton Unsafe] replace handler failed at {}", key, it)
            }
        }

        val redirectEntry = redirectHandlers[key]?.lastOrNull()
        if (redirectEntry != null) {
            runCatching {
                Event.withScriptOwner(redirectEntry.owner) {
                    val invocation = UnsafeInvocation(method, instance, args, redirectEntry.owner).bindEnter(enterControl)
                    val target = redirectEntry.target
                    val receiver = if (java.lang.reflect.Modifier.isStatic(target.modifiers)) null else instance
                    val redirected = target.invoke(receiver, *args)
                    invocation.cancelWith(redirected)
                }
            }.onFailure {
                logger.error("[Katton Unsafe] redirect handler failed at {}", key, it)
            }
        }

        val entries = beforeHandlers[key] ?: return
        for (entry in entries) {
            runCatching {
                Event.withScriptOwner(entry.owner) {
                    entry.handler(UnsafeInvocation(method, instance, args, entry.owner).bindEnter(enterControl))
                }
            }.onFailure {
                logger.error("[Katton Unsafe] before handler failed at {}", key, it)
            }
        }
    }

    @JvmStatic
    /**
     * Advice exit dispatcher.
     */
    fun dispatchAfter(
        method: Method,
        instance: Any?,
        args: Array<Any?>,
        result: Any?,
        throwable: Throwable?,
        exitControl: ExitControl
    ) {
        val key = targetKey(method)
        val entries = afterHandlers[key] ?: return
        for (entry in entries) {
            runCatching {
                Event.withScriptOwner(entry.owner) {
                    entry.handler(
                        UnsafeInvocation(method, instance, args, entry.owner).bindExit(exitControl),
                        result,
                        throwable
                    )
                }
            }.onFailure {
                logger.error("[Katton Unsafe] after handler failed at {}", key, it)
            }
        }
    }

    @JvmStatic
    fun dispatchConstructorBefore(constructor: Constructor<*>, instance: Any?, args: Array<Any?>) {
        val key = constructorKey(constructor)
        val entries = constructorBeforeHandlers[key] ?: return
        for (entry in entries) {
            runCatching {
                Event.withScriptOwner(entry.owner) {
                    entry.handler(UnsafeConstructorInvocation(constructor, instance, args, entry.owner))
                }
            }.onFailure {
                logger.error("[Katton Unsafe] constructor before handler failed at {}", key, it)
            }
        }
    }

    @JvmStatic
    fun dispatchConstructorAfter(constructor: Constructor<*>, instance: Any?, args: Array<Any?>) {
        val key = constructorKey(constructor)
        val entries = constructorAfterHandlers[key] ?: return
        for (entry in entries) {
            runCatching {
                Event.withScriptOwner(entry.owner) {
                    entry.handler(UnsafeConstructorInvocation(constructor, instance, args, entry.owner))
                }
            }.onFailure {
                logger.error("[Katton Unsafe] constructor after handler failed at {}", key, it)
            }
        }
    }

    class UniversalConstructorAdvice {
        companion object {
            @JvmStatic
            @Advice.OnMethodEnter(suppress = Throwable::class)
            fun onEnter(
                @Advice.Origin constructor: Constructor<*>,
                @Advice.This(optional = true) instance: Any?,
                @Advice.AllArguments args: Array<Any?>
            ) {
                dispatchConstructorBefore(constructor, instance, args)
            }

            @JvmStatic
            @Advice.OnMethodExit(suppress = Throwable::class)
            fun onExit(
                @Advice.Origin constructor: Constructor<*>,
                @Advice.This(optional = true) instance: Any?,
                @Advice.AllArguments args: Array<Any?>
            ) {
                dispatchConstructorAfter(constructor, instance, args)
            }
        }
    }
}

