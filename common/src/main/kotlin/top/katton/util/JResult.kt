package top.katton.util

import java.io.Serializable
import java.util.Optional

/**
 * Java-friendly boxed result type.
 *
 * Unlike [Result], this is a regular class, so it can be consumed from Java
 * without Kotlin value-class interop issues.
 */
@Suppress("unused")
class JResult<T> private constructor(
    @PublishedApi
    internal val value: Any?,
    @PublishedApi
    internal val error: String?,
    val isSuccess: Boolean
) : Serializable {

    val isFailure: Boolean
        get() = !isSuccess

    @Suppress("UNCHECKED_CAST")
    fun getOrNull(): T? = if (isSuccess) value as T else null

    fun errorOrNull(): String? = error

    fun toOptional(): Optional<T & Any> = Optional.ofNullable(getOrNull())

    /**
     * Returns the encapsulated value if this instance represents success or throws [IllegalStateException]
     * with the failure message if it is failure.
     */
    @Suppress("UNCHECKED_CAST")
    fun getOrThrow(): T {
        if (!isSuccess) error(error ?: "Unknown error")
        return value as T
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> map(transform: (T) -> R): JResult<R> {
        return if (isSuccess) {
            success(transform(value as T))
        } else {
            failure(error ?: "Unknown error")
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> flatMap(transform: (T) -> JResult<R>): JResult<R> {
        return if (isSuccess) {
            transform(value as T)
        } else {
            failure(error ?: "Unknown error")
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun recover(transform: (String) -> @UnsafeVariance T): JResult<T> {
        return if (isFailure) {
            success(transform(error ?: "Unknown error"))
        } else {
            this
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun onSuccess(action: (T) -> Unit): JResult<T> {
        if (isSuccess) action(value as T)
        return this
    }

    inline fun onFailure(action: (String) -> Unit): JResult<T> {
        if (isFailure) action(error ?: "Unknown error")
        return this
    }

    @Suppress("UNCHECKED_CAST")
    inline fun getOrElse(onFailure: (String) -> @UnsafeVariance T): T {
        return if (isSuccess) {
            value as T
        } else {
            onFailure(error ?: "Unknown error")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getOrDefault(defaultValue: @UnsafeVariance T): T {
        return if (isSuccess) {
            value as T
        } else {
            defaultValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (String) -> R): R {
        return if (isSuccess) {
            onSuccess(value as T)
        } else {
            onFailure(error ?: "Unknown error")
        }
    }

    fun toResult(): Result<T> =
        if (isSuccess) {
            @Suppress("UNCHECKED_CAST")
            Result.success(value as T)
        } else {
            Result.failure(error ?: "Unknown error")
        }

    override fun toString(): String =
        if (isSuccess) "JResult.success($value)" else "JResult.failure($error)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JResult<*>) return false

        if (value != other.value) return false
        if (error != other.error) return false
        if (isSuccess != other.isSuccess) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value?.hashCode() ?: 0
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + isSuccess.hashCode()
        return result
    }

    inline fun notEmptyAndMatch(predicate: (T?) -> Boolean): Boolean {
        return isSuccess && predicate(getOrNull())
    }

    fun notEmptyAndNotNull(): Boolean{
        return getOrNull() != null
    }

    fun notEmptyAndTrue(): Boolean {
        return getOrNull() == true
    }

    fun notEmptyAndFalse(): Boolean {
        return getOrNull() == false
    }

    fun notEmptyAndEquals(expected: T?): Boolean {
        return getOrNull() == expected
    }

    fun notEmptyAndNotEquals(unexpected: T?): Boolean {
        return getOrNull() != unexpected
    }

    fun emptyOrMatch(predicate: (T?) -> Boolean): Boolean {
        return isFailure || predicate(getOrNull())
    }

    fun emptyOrTrue(): Boolean {
        return isFailure || getOrNull() == true
    }

    fun emptyOrFalse(): Boolean {
        return isFailure || getOrNull() == false
    }

    companion object {
        @JvmStatic
        fun <T> success(value: T?): JResult<T> = JResult(value, null, true)

        @JvmStatic
        fun success(): JResult<Unit> = JResult(Unit, null, true)

        @JvmStatic
        fun <T> failure(message: String): JResult<T> = JResult(null, message, false)

        @JvmStatic
        fun <T> failure(exception: Throwable): JResult<T> =
            JResult(null, exception.message ?: exception.toString(), false)

        @JvmStatic
        inline fun <T> catch(block: () -> T): JResult<T> {
            return try {
                success(block())
            } catch (e: Throwable) {
                failure(e)
            }
        }

        @JvmStatic
        fun <T> from(result: Result<T>): JResult<T> =
            if (result.isSuccess) {
                success(result.getOrNull())
            } else {
                failure(result.errorOrNull() ?: "Unknown error")
            }
    }
}
