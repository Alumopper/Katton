package top.katton.util

import java.io.Serializable

/**
 * A Result type that holds either a value of type [T] or an error message [String].
 */
@Suppress("unused")
@JvmInline
value class Result<out T> @PublishedApi internal constructor(
    @PublishedApi internal val value: Any?
) : Serializable {

    // Internal marker for failure to distinguish from success values
    @PublishedApi
    internal class Failure(@PublishedApi internal val message: String) : Serializable {
        override fun toString() = "Failure($message)"

        override fun equals(other: Any?): Boolean {
             if (this === other) return true
             if (javaClass != other?.javaClass) return false
             other as Failure
             return message == other.message
        }

        override fun hashCode(): Int = message.hashCode()
    }

    val isSuccess: Boolean get() = value !is Failure
    val isFailure: Boolean get() = value is Failure

    @Suppress("UNCHECKED_CAST")
    fun getOrNull(): T? = if (isSuccess) value as T else null

    fun errorOrNull(): String? = (value as? Failure)?.message

    /**
     * Returns the encapsulated value if this instance represents success or throws [IllegalStateException]
     * with the failure message if it is failure.
     */
    @Suppress("UNCHECKED_CAST")
    fun getOrThrow(): T {
        if (value is Failure) error(value.message)
        return value as T
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> map(transform: (T) -> R): Result<R> {
        return if (isSuccess) {
            success(transform(value as T))
        } else {
            Result(value) // Propagate failure
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> {
        return if (isSuccess) {
            transform(value as T)
        } else {
            Result(value) // Propagate failure
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun recover(transform: (String) -> @UnsafeVariance T): Result<T> {
        return if (value is Failure) {
            success(transform(value.message))
        } else {
            this
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (isSuccess) action(value as T)
        return this
    }

    inline fun onFailure(action: (String) -> Unit): Result<T> {
        if (value is Failure) action(value.message)
        return this
    }

    @Suppress("UNCHECKED_CAST")
    inline fun getOrElse(onFailure: (String) -> @UnsafeVariance T): T {
        return if (isSuccess) {
            value as T
        } else {
            onFailure((value as Failure).message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (String) -> R): R {
        return if (isSuccess) {
            onSuccess(value as T)
        } else {
            onFailure((value as Failure).message)
        }
    }

    companion object {
        fun <T> success(value: T?): Result<T> = Result(value)

        fun success(): Result<Unit> = Result(Unit)

        fun failure(message: String): Result<Nothing> = Result(Failure(message))

        // overload for compatibility with Exception-based flows
        fun failure(exception: Throwable): Result<Nothing> = Result(Failure(exception.message ?: exception.toString()))

        inline fun <T> catch(block: () -> T): Result<T> {
            return try {
                Result(block())
            } catch (e: Throwable) {
                Result(Failure(e.message ?: e.toString()))
            }
        }
    }
}
