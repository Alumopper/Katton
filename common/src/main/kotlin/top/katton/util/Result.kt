package top.katton.util

import java.io.Serializable
import java.util.Optional

/**
 * A performant Result type holding either a value of type [T] or a failure message.
 *
 * Uses [String] instead of [Throwable] for failures — no stack trace overhead.
 * Regular class (not value class) for seamless Java interop. Common immutable
 * success values are shared so hot event and reflection paths avoid repeatedly
 * allocating wrappers for `Unit`, booleans, and `null`.
 */
@Suppress("unused")
class Result<out T> private constructor(
    @PublishedApi internal val value: Any?,
    @PublishedApi internal val error: String?
) : Serializable {

    // ── status queries ──────────────────────────────────────────────

    val isSuccess: Boolean get() = error == null
    val isFailure: Boolean get() = error != null

    // ── value extraction ────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    fun getOrNull(): T? = if (isSuccess) value as T else null

    fun errorOrNull(): String? = error

    /**
     * Returns the encapsulated value on success, or throws [IllegalStateException]
     * with the failure message.
     */
    @Suppress("UNCHECKED_CAST")
    fun getOrThrow(): T {
        if (!isSuccess) error(error ?: "Unknown error")
        return value as T
    }

    @Suppress("UNCHECKED_CAST")
    fun getOrDefault(defaultValue: @UnsafeVariance T): T {
        return if (isSuccess) value as T else defaultValue
    }

    fun toOptional(): Optional<T & Any> = Optional.ofNullable(getOrNull())

    // ── combinators ──────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    inline fun <R> map(transform: (T) -> R): Result<R> {
        return if (isSuccess) success(transform(value as T))
        else failure(error ?: "Unknown error")
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> {
        return if (isSuccess) transform(value as T)
        else failure(error ?: "Unknown error")
    }

    @Suppress("UNCHECKED_CAST")
    inline fun recover(transform: (String) -> @UnsafeVariance T): Result<T> {
        return if (isFailure) success(transform(error ?: "Unknown error"))
        else this
    }

    @Suppress("UNCHECKED_CAST")
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (isSuccess) action(value as T)
        return this
    }

    inline fun onFailure(action: (String) -> Unit): Result<T> {
        if (isFailure) action(error ?: "Unknown error")
        return this
    }

    @Suppress("UNCHECKED_CAST")
    inline fun getOrElse(onFailure: (String) -> @UnsafeVariance T): T {
        return if (isSuccess) value as T
        else onFailure(error ?: "Unknown error")
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (String) -> R): R {
        return if (isSuccess) onSuccess(value as T)
        else onFailure(error ?: "Unknown error")
    }

    // ── predicate combinators (Java-friendly) ────────────────────────

    inline fun notEmptyAndMatch(predicate: (T?) -> Boolean): Boolean {
        return isSuccess && predicate(getOrNull())
    }

    fun notEmptyAndNotNull(): Boolean = getOrNull() != null

    fun notEmptyAndTrue(): Boolean = getOrNull() == true

    fun notEmptyAndFalse(): Boolean = getOrNull() == false

    fun notEmptyAndEquals(expected: @UnsafeVariance T?): Boolean = getOrNull() == expected

    fun notEmptyAndNotEquals(unexpected: @UnsafeVariance T?): Boolean = getOrNull() != unexpected

    inline fun emptyOrMatch(predicate: (T?) -> Boolean): Boolean {
        return isFailure || predicate(getOrNull())
    }

    fun emptyOrTrue(): Boolean = isFailure || getOrNull() == true

    fun emptyOrFalse(): Boolean = isFailure || getOrNull() == false

    // ── toString / equals / hashCode ─────────────────────────────────

    override fun toString(): String =
        if (isSuccess) "Result.success($value)" else "Result.failure($error)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Result<*>) return false
        if (value != other.value) return false
        if (error != other.error) return false
        return true
    }

    override fun hashCode(): Int {
        var result = value?.hashCode() ?: 0
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }

    // ── factories ────────────────────────────────────────────────────

    companion object {
        private val NULL_SUCCESS = Result<Any?>(null, null)
        private val UNIT_SUCCESS = Result<Unit>(Unit, null)
        private val TRUE_SUCCESS = Result<Boolean>(true, null)
        private val FALSE_SUCCESS = Result<Boolean>(false, null)

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T> success(value: T?): Result<T> = when {
            value == null -> NULL_SUCCESS
            value === Unit -> UNIT_SUCCESS
            value is Boolean -> if (value) TRUE_SUCCESS else FALSE_SUCCESS
            else -> Result<T>(value, null)
        } as Result<T>

        @JvmStatic
        fun success(): Result<Unit> = UNIT_SUCCESS

        @JvmStatic
        fun <T> failure(message: String): Result<T> = Result(null, message)

        @JvmStatic
        fun <T> failure(exception: Throwable): Result<T> =
            Result(null, exception.message ?: exception.toString())

        inline fun <T> catch(block: () -> T): Result<T> {
            return try {
                success(block())
            } catch (e: Throwable) {
                failure(e)
            }
        }
    }
}
