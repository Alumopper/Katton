package top.katton.util

import com.mojang.logging.LogUtils
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.MappingResolver
import org.slf4j.Logger
import java.lang.invoke.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.toTypedArray

/**
 * Lightweight utility for fast reflective access using VarHandle, MethodHandle and LambdaMetafactory.
 *
 *
 * Usage examples:
 * <pre>
 * // VarHandle read/write
 * VarHandle vh = ReflectUtil.findVarHandle(MyClass.class, "value", int.class);
 * int v = (int) ReflectUtil.vhGet(vh, myObj);
 * ReflectUtil.vhSet(vh, myObj, 123);
 *
 * // Method handle invoke
 * MethodHandle mh = ReflectUtil.findMethodHandle(MyClass.class, "compute", int.class);
 * Object result = ReflectUtil.invoke(mh, myObj, 5);
</pre> *
 *
 */
@Suppress("unused")
object ReflectUtil {
    private data class FieldKey(val owner: Class<*>, val name: String, val fieldType: Class<*>?)
    private data class MethodKey(val owner: Class<*>, val name: String, val paramTypes: List<Class<*>>)
    private data class MethodArgsKey(val owner: Class<*>, val name: String, val argTypes: List<Class<*>?>)
    private data class ConstructorKey(val owner: Class<*>, val paramTypes: List<Class<*>>)
    private data class ConstructorArgsKey(val owner: Class<*>, val argTypes: List<Class<*>?>)
    private data class LambdaKey(
        val funcInterface: Class<*>,
        val target: Class<*>,
        val methodName: String,
        val paramTypes: List<Class<*>>
    )

    private val LOGGER: Logger = LogUtils.getLogger()
    private val PUBLIC_LOOKUP: MethodHandles.Lookup = MethodHandles.lookup()
    private val VAR_HANDLE_CACHE = ConcurrentHashMap<FieldKey, Optional<VarHandle>>()
    private val MH_HANDLE_CACHE = ConcurrentHashMap<MethodKey, Optional<MethodHandle>>()
    private val METHOD_BY_ARGS_CACHE = ConcurrentHashMap<MethodArgsKey, Optional<MethodKey>>()
    private val CONSTRUCTOR_HANDLE_CACHE = ConcurrentHashMap<ConstructorKey, Optional<MethodHandle>>()
    private val CONSTRUCTOR_MATCH_CACHE = ConcurrentHashMap<ConstructorArgsKey, Optional<List<Class<*>>>>()
    private val LAMBDA_CACHE = ConcurrentHashMap<LambdaKey, Optional<Any>>()
    private val MR: MappingResolver = FabricLoader.getInstance().mappingResolver
    private val CLASS_CACHE = ConcurrentHashMap<String, Optional<Class<*>>>()
    private val PRIMITIVE_TO_WRAPPER: Map<Class<*>, Class<*>> = mapOf(
        Boolean::class.javaPrimitiveType!! to java.lang.Boolean::class.java,
        Byte::class.javaPrimitiveType!! to java.lang.Byte::class.java,
        Char::class.javaPrimitiveType!! to Character::class.java,
        Short::class.javaPrimitiveType!! to java.lang.Short::class.java,
        Int::class.javaPrimitiveType!! to Integer::class.java,
        Long::class.javaPrimitiveType!! to java.lang.Long::class.java,
        Float::class.javaPrimitiveType!! to java.lang.Float::class.java,
        Double::class.javaPrimitiveType!! to java.lang.Double::class.java,
        Void.TYPE to Void::class.java
    )

    private fun failure(message: String): Result<Nothing> =
        Result.failure(IllegalStateException(message))

    private fun failure(message: String, cause: Throwable): Result<Nothing> =
        Result.failure(IllegalStateException(message, cause))

    private fun wrapType(type: Class<*>): Class<*> = PRIMITIVE_TO_WRAPPER[type] ?: type

    private fun isTypeCompatible(parameterType: Class<*>, argumentType: Class<*>?): Boolean {
        if (argumentType == null) {
            return !parameterType.isPrimitive
        }
        return wrapType(parameterType).isAssignableFrom(wrapType(argumentType))
    }

    private fun findFieldReflective(target: Class<*>?, fieldName: String): java.lang.reflect.Field? {
        var current = target
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun mapField(named: String?, owner: Class<*>, target: Class<*>): String? {
        return MR.mapFieldName("named", owner.getName(), named, target.getName())
    }

    //region variable
    /**
     * Find and cache varhandle in a class.
     * @param target  the class to look up the varhandle
     * @param fieldName  the name of the field to look up
     * @param fieldType  the type of the field to look up
     * @return the varhandle if found, or null if not found
     */
    private fun findVarHandle(target: Class<*>, fieldName: String, fieldType: Class<*>): VarHandle? {
        val k = FieldKey(target, fieldName, fieldType)
        val result = VAR_HANDLE_CACHE.computeIfAbsent(k) {
            try {
                val lookup =
                    MethodHandles.privateLookupIn(target, PUBLIC_LOOKUP)
                Optional.ofNullable(lookup.findVarHandle(target, fieldName, fieldType))
            } catch (e: ReflectiveOperationException) {
                LOGGER.error("Failed to find varhandle for field {} in class {}", fieldName, target, e)
                Optional.empty()
            }
        }
        return result.orElse(null)
    }

    /**
     * Find and cache varhandle in a class without specifying the field type.
     * @param target  the class to look up the varhandle
     * @param fieldName  the name of the field to look up
     * @return the varhandle if found, or null if not found
     */
    private fun findVarHandle(target: Class<*>, fieldName: String): VarHandle? {
        val k = FieldKey(target, fieldName, null)
        val result = VAR_HANDLE_CACHE.computeIfAbsent(k) {
            try {
                val field = findFieldReflective(target, fieldName)
                    ?: return@computeIfAbsent Optional.empty()
                val fieldType = field.type
                val lookup =
                    MethodHandles.privateLookupIn(field.declaringClass, PUBLIC_LOOKUP)
                Optional.ofNullable(lookup.findVarHandle(field.declaringClass, fieldName, fieldType))
            } catch (e: ReflectiveOperationException) {
                LOGGER.error("Failed to find varhandle for field {} in class {}", fieldName, target, e)
                Optional.empty()
            }
        }
        return result.orElse(null)
    }

    /**
     * Find and cache the first varhandle in a class with a specified field type.
     * @param target  the class to look up the varhandle
     * @param fieldType  the type of the field to look up
     * @return the varhandle if found, or null if not found
     */
    fun findFirstVarHandle(target: Class<*>, fieldType: Class<*>?): VarHandle? {
        for (field in target.declaredFields) {
            if (field.type == fieldType) {
                return findVarHandle(target, field.name, fieldType)
            }
        }
        return null
    }

    /**
     * Get a field in a class using a varhandle
     * @param vh a varhandle, which should obtained from [ReflectUtil.findVarHandle]
     * @param receiver the object to get the field value from
     * @return the value of the field
     */
    private fun vhGet(vh: VarHandle, receiver: Any?): Any? {
        return vh.get(receiver)
    }

    /**
     * Check if a field exists in a class
     * @param object the object to check the field existence from
     * @param fieldName the name of the field to check
     * @param fieldType the type of the field to check
     * @return whether the field exists or not
     */
    fun exist(`object`: Any, fieldName: String, fieldType: Class<*>): Boolean {
        return findVarHandle(`object`.javaClass, fieldName, fieldType) != null
    }


    /**
     * Check if a field exists in a class
     * @param object the object to check the field existence from
     * @param fieldName the name of the field to check
     * @return whether the field exists or not
     */
    fun exist(`object`: Any, fieldName: String): Boolean {
        return findVarHandle(`object`.javaClass, fieldName) != null
    }

    /**
     * Get a field in a class
     * @param object the object to get the field value from
     * @param fieldName the name of the field to get
     * @param fieldType the type of the field to get
     * @return the value of the field
     */
    fun get(`object`: Any, fieldName: String, fieldType: Class<*>): Result<*> {
        val vh = findVarHandle(`object`.javaClass, fieldName, fieldType)
        return if (vh == null) {
            failure("Field not found: $fieldName in ${`object`.javaClass.name}")
        } else {
            Result.success(vhGet(vh, `object`))
        }
    }

    /**
     * Get a field in a class
     * @param object the object to get the field value from
     * @param fieldName the name of the field to get
     * @return the value of the field
     */
    fun get(`object`: Any, fieldName: String): Result<*> {
        val vh = findVarHandle(`object`.javaClass, fieldName)
        return if (vh == null) {
            failure("Field not found: $fieldName in ${`object`.javaClass.name}")
        } else {
            Result.success(vhGet(vh, `object`))
        }
    }

    fun <T> getT(`object`: Any, fieldName: String, fieldType: Class<T?>): Result<T?> {
        val vh = findVarHandle(`object`.javaClass, fieldName, fieldType)
        return if (vh == null) {
            failure("Field not found: $fieldName in ${`object`.javaClass.name}")
        } else {
            @Suppress("UNCHECKED_CAST")
            Result.success(vhGet(vh, `object`) as T?)
        }
    }

    fun <T> getT(`object`: Any, fieldName: String): Result<T?> {
        val vh = findVarHandle(`object`.javaClass, fieldName)
        return if (vh == null) {
            failure("Field not found: $fieldName in ${`object`.javaClass.name}")
        } else {
            @Suppress("UNCHECKED_CAST")
            Result.success(vhGet(vh, `object`) as T?)
        }
    }

    /**
     * Set a field in a class using a varhandle
     * @param vh a varhandle, which should obtained from [ReflectUtil.findVarHandle]
     * @param receiver the object to set the field value to
     * @param value the value to set
     */
    private fun vhSet(vh: VarHandle, receiver: Any?, value: Any?) {
        vh.set(receiver, value)
    }

    /**
     * Set a field in a class
     * @param object the object to set the field value to
     * @param fieldName the name of the field to set
     * @param fieldType the type of the field to set
     * @param value the value to set
     * @return success if the field was set, failed if not
     */
    fun set(`object`: Any, fieldName: String, fieldType: Class<*>, value: Any?): Result<*> {
        val vh = findVarHandle(`object`.javaClass, fieldName, fieldType)
        if (vh == null) {
            return failure("Field not found: $fieldName in ${`object`.javaClass.name}")
        } else {
            vhSet(vh, `object`, value)
            return Result.success(Unit)
        }
    }

    fun set(`object`: Any, fieldName: String, value: Any?): Result<*> {
        val vh = findVarHandle(`object`.javaClass, fieldName)
        if (vh == null) {
            return failure("Field not found: $fieldName in ${`object`.javaClass.name}")
        } else {
            vhSet(vh, `object`, value)
            return Result.success(Unit)
        }
    }

    fun <T> setT(`object`: Any, fieldName: String, fieldType: Class<T?>, value: T?): Result<*> {
        val vh = findVarHandle(`object`.javaClass, fieldName, fieldType)
        if (vh == null) {
            return failure("Field not found: $fieldName in ${`object`.javaClass.name}")
        } else {
            vhSet(vh, `object`, value)
            return Result.success(Unit)
        }
    }

    fun <T> setT(`object`: Any, fieldName: String, value: T?): Result<*> {
        val vh = if (value == null) {
            findVarHandle(`object`.javaClass, fieldName)
        } else {
            findVarHandle(`object`.javaClass, fieldName, value.javaClass)
        }
        if (vh == null) {
            return failure("Field not found: $fieldName in ${`object`.javaClass.name}")
        } else {
            vhSet(vh, `object`, value)
            return Result.success(Unit)
        }
    }

    //endregion
    //region method
    /**
     * Find and cache methodhandle in a class.
     * @param target  the class to look up the methodhandle
     * @param name  the name of the method to look up
     * @param paramTypes  the types of the parameters of the method to look up
     * @return the methodhandle if found, or null if not found
     */
    private fun findMethodHandle(target: Class<*>, name: String, vararg paramTypes: Class<*>): MethodHandle? {
        val k = MethodKey(target, name, paramTypes.toList())
        val result = MH_HANDLE_CACHE.computeIfAbsent(k) {
            try {
                val m = findMethodReflective(target, name, *paramTypes)
                if (m == null) {
                    LOGGER.error("Method not found: {}({})", name, paramTypes.contentToString())
                    return@computeIfAbsent Optional.empty()
                }
                m.setAccessible(true)
                Optional.ofNullable(PUBLIC_LOOKUP.unreflect(m))
            } catch (e: ReflectiveOperationException) {
                LOGGER.error("Error while looking up method: {}({})", name, paramTypes.contentToString(), e)
                Optional.empty()
            }
        }
        return result.orElse(null)
    }

    private fun findMethodHandleByArgs(target: Class<*>, name: String, vararg args: Any?): MethodHandle? {
        val argKey = MethodArgsKey(target, name, args.map { it?.javaClass })
        val methodKeyOpt = METHOD_BY_ARGS_CACHE.computeIfAbsent(argKey) {
            try {
                val m = findMethodByArgs(target, name, *args)
                if (m == null) {
                    LOGGER.error("Method not found: {}({})", name, args.contentToString())
                    return@computeIfAbsent Optional.empty()
                }
                Optional.of(MethodKey(m.declaringClass, m.name, m.parameterTypes.toList()))
            } catch (e: Throwable) {
                LOGGER.error("Error while resolving method: {}({})", name, args.contentToString(), e)
                Optional.empty()
            }
        }
        val methodKey = methodKeyOpt.orElse(null) ?: return null
        return findMethodHandle(methodKey.owner, methodKey.name, *methodKey.paramTypes.toTypedArray())
    }

    private fun findMethodReflective(target: Class<*>?, name: String, vararg paramTypes: Class<*>): Method? {
        var c = target
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, *paramTypes)
            } catch (_: NoSuchMethodException) {
            }

            var best: Method? = null
            var bestScore = Int.MAX_VALUE
            for (method in c.declaredMethods) {
                if (method.name != name) continue
                val score = scoreMethodByTypes(method, paramTypes)
                if (score < bestScore) {
                    best = method
                    bestScore = score
                }
            }
            if (best != null) {
                return best
            }

            c = c.getSuperclass()
        }
        return null
    }

    private fun findMethodByArgs(target: Class<*>?, name: String, vararg args: Any?): Method? {
        var c = target
        while (c != null) {
            var best: Method? = null
            var bestScore = Int.MAX_VALUE
            for (method in c.declaredMethods) {
                if (method.name != name) continue
                val score = scoreMethodByArgs(method, args)
                if (score < bestScore) {
                    best = method
                    bestScore = score
                }
            }
            if (best != null) {
                return best
            }

            c = c.superclass
        }
        return null
    }

    private fun scoreMethodByTypes(method: Method, argTypes: Array<out Class<*>>): Int {
        val params = method.parameterTypes
        if (params.size != argTypes.size) {
            return Int.MAX_VALUE
        }
        var score = 0
        for (i in params.indices) {
            val parameter = params[i]
            val argType = argTypes[i]
            if (!isTypeCompatible(parameter, argType)) {
                return Int.MAX_VALUE
            }
            score += when {
                wrapType(parameter) == wrapType(argType) -> 0
                wrapType(parameter).isAssignableFrom(wrapType(argType)) -> 1
                else -> 4
            }
        }
        return score
    }

    private fun scoreMethodByArgs(method: Method, args: Array<out Any?>): Int {
        val params = method.parameterTypes
        if (params.size != args.size) {
            return Int.MAX_VALUE
        }
        var score = 0
        for (i in params.indices) {
            val parameter = params[i]
            val argument = args[i]
            val argumentType = argument?.javaClass
            if (!isTypeCompatible(parameter, argumentType)) {
                return Int.MAX_VALUE
            }
            score += when {
                argumentType == null -> 3
                wrapType(parameter) == wrapType(argumentType) -> 0
                wrapType(parameter).isAssignableFrom(wrapType(argumentType)) -> 1
                else -> 4
            }
        }
        return score
    }

    /**
     * A convenient method to invoke a methodhandle.
     * @param mh the methodhandle to invoke
     * @param args the arguments to pass to the methodhandle
     * @return the result of the methodhandle invocation
     */
    @Throws(Throwable::class, WrongMethodTypeException::class, ClassCastException::class)
    private fun invoke(mh: MethodHandle, vararg args: Any?): Any? {
        return when (args.size) {
            0 -> mh.invoke()
            1 -> mh.invoke(args[0])
            2 -> mh.invoke(args[0], args[1])
            3 -> mh.invoke(args[0], args[1], args[2])
            4 -> mh.invoke(args[0], args[1], args[2], args[3])
            else -> mh.invokeWithArguments(*args)
        }
    }

    @Throws(Throwable::class, WrongMethodTypeException::class, ClassCastException::class)
    private fun invoke(mh: MethodHandle, args: MutableList<Any?>): Any? {
        return invoke(mh, *args.toTypedArray())
    }

    @Throws(Throwable::class, WrongMethodTypeException::class, ClassCastException::class)
    private fun invokeInstance(mh: MethodHandle, caller: Any, args: Array<out Any?>): Any? {
        return when (args.size) {
            0 -> mh.invoke(caller)
            1 -> mh.invoke(caller, args[0])
            2 -> mh.invoke(caller, args[0], args[1])
            3 -> mh.invoke(caller, args[0], args[1], args[2])
            4 -> mh.invoke(caller, args[0], args[1], args[2], args[3])
            else -> {
                val invokeArgs = arrayOfNulls<Any?>(args.size + 1)
                invokeArgs[0] = caller
                System.arraycopy(args, 0, invokeArgs, 1, args.size)
                mh.invokeWithArguments(*invokeArgs)
            }
        }
    }

    fun invokeWithParamType(
        caller: Any,
        name: String,
        paramTypes: MutableList<Class<*>>,
        vararg args: Any?
    ): Result<*> {
        val handle = findMethodHandle(caller.javaClass, name, *paramTypes.toTypedArray<Class<*>>())
        if (handle != null) {
            try {
                return Result.success(invokeInstance(handle, caller, args))
            } catch (t: Throwable) {
                LOGGER.error("Error while invoking method: {}({})", name, args.contentToString(), t)
                return failure(t.message ?: "Invoke failed", t)
            }
        } else {
            return failure("Method not found: $name(${paramTypes.joinToString { it.name }})")
        }
    }

    fun invoke(caller: Any, name: String, vararg args: Any?): Result<*> {
        val handle = findMethodHandleByArgs(caller.javaClass, name, *args)
        if (handle != null) {
            try {
                return Result.success(invokeInstance(handle, caller, args))
            } catch (t: Throwable) {
                LOGGER.error("Error while invoking method: {}({})", name, args.contentToString(), t)
                return failure(t.message ?: "Invoke failed", t)
            }
        } else {
            return failure("Method not found: $name")
        }
    }

    fun invoke(caller: Any, name: String): Result<*> {
        val handle = findMethodHandle(caller.javaClass, name)
        if (handle != null) {
            try {
                return Result.success(handle.invoke(caller))
            } catch (t: Throwable) {
                LOGGER.error("Error while invoking method: {}()", name, t)
                return failure(t.message ?: "Invoke failed", t)
            }
        } else {
            return failure("Method not found: $name")
        }
    }


    fun <T> methodAsFunctional(
        funcInterface: Class<T>,
        target: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>
    ): Result<T?> {
        val k = LambdaKey(funcInterface, target, methodName, paramTypes.toList())
        @Suppress("UNCHECKED_CAST")
        val cached = LAMBDA_CACHE[k] as Optional<T>?
        if (cached != null) {
            return Result.success(cached.orElse(null))
        }

        return try {
            val m = findMethodReflective(target, methodName, *paramTypes)
                ?: throw NoSuchMethodException(methodName)
            m.isAccessible = true
            val mh = MethodHandles.privateLookupIn(m.declaringClass, PUBLIC_LOOKUP).unreflect(m)
            val invokedType = MethodType.methodType(funcInterface)
            val sam = MethodType.methodType(mh.type().returnType(), mh.type().parameterArray())
            val cs = LambdaMetafactory.metafactory(
                MethodHandles.privateLookupIn(target, PUBLIC_LOOKUP),
                getSingleAbstractMethodName(funcInterface),
                invokedType,
                sam.erase(),
                mh,
                mh.type()
            )
            @Suppress("UNCHECKED_CAST") val lambda = cs.target.invoke() as T?
            @Suppress("UNCHECKED_CAST")
            LAMBDA_CACHE.putIfAbsent(k, Optional.ofNullable(lambda) as Optional<Any>)
            Result.success(lambda)
        } catch (t: Throwable) {
            LAMBDA_CACHE.putIfAbsent(k, Optional.empty())
            failure(t.message ?: "Create lambda failed", t)
        }
    }

    private fun getSingleAbstractMethodName(iface: Class<*>): String {
        for (m in iface.methods) {
            if (Modifier.isAbstract(m.modifiers)) return m.name
        }
        throw IllegalArgumentException("Not a functional interface: $iface")
    }

    //endregion
    private fun findConstructorHandle(target: Class<*>, vararg paramTypes: Class<*>): MethodHandle? {
        val k = ConstructorKey(target, paramTypes.toList())
        val result = CONSTRUCTOR_HANDLE_CACHE.computeIfAbsent(k) {
            try {
                val lookup = MethodHandles.privateLookupIn(target, PUBLIC_LOOKUP)
                Optional.ofNullable(lookup.findConstructor(target, MethodType.methodType(Void.TYPE, paramTypes)))
            } catch (e: ReflectiveOperationException) {
                LOGGER.error(
                    "Failed to find constructor for class {} with params {}",
                    target,
                    paramTypes.contentToString(),
                    e
                )
                Optional.empty()
            }
        }
        return result.orElse(null)
    }

    private fun resolveConstructorParamTypes(target: Class<*>, args: Array<out Any?>): List<Class<*>>? {
        val argKey = ConstructorArgsKey(target, args.map { it?.javaClass })
        val result = CONSTRUCTOR_MATCH_CACHE.computeIfAbsent(argKey) {
            val matched = target.declaredConstructors.firstOrNull { ctor ->
                val params = ctor.parameterTypes
                if (params.size != args.size) return@firstOrNull false
                params.indices.all { index -> isTypeCompatible(params[index], args[index]?.javaClass) }
            }
            if (matched != null) {
                Optional.of(matched.parameterTypes.toList())
            } else {
                Optional.empty()
            }
        }
        return result.orElse(null)
    }

    fun <T> newInstance(target: Class<T>, vararg args: Any?): Result<T?> {
        val resolvedParamTypes = resolveConstructorParamTypes(target, args)
        val fallbackParamTypes = args.map { arg: Any? -> arg?.javaClass ?: Any::class.java }
        val paramTypes = resolvedParamTypes ?: fallbackParamTypes
        val ctor = findConstructorHandle(target, *paramTypes.toTypedArray())
        if (ctor == null) {
            return failure("Constructor not found for $target with params ${paramTypes.toTypedArray().contentToString()}")
        }
        try {
            @Suppress("UNCHECKED_CAST")
            return Result.success(invoke(ctor, *args) as T?)
        } catch (t: Throwable) {
            LOGGER.error(
                "Error while invoking constructor for class {} with args {}",
                target,
                args.contentToString(),
                t
            )
            return failure(t.message ?: "Invoke constructor failed", t)
        }
    }


    /**
     * Try to get a class from possible names.
     * @param names possible class names
     * @return the result containing class if found, otherwise a failure result
     */
    fun getPossibleClassFromNames(vararg names: String): Result<Class<*>?> {
        for (name in names) {
            var cached = CLASS_CACHE[name]
            if (cached == null) {
                var loaded: Class<*>? = null
                try {
                    loaded = Class.forName(name)
                } catch (_: ClassNotFoundException) {
                }
                //if class not found, put null into cache
                val opt = Optional.ofNullable(loaded)
                CLASS_CACHE.put(name, opt)
                cached = opt
            }
            if (cached.isPresent) {
                return Result.success(cached.get())
            }
        }
        return failure("Class not found: ${names.contentToString()}")
    }
}
