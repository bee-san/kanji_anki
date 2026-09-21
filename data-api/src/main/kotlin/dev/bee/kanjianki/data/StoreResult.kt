package dev.bee.kanjianki.data

/**
 * Structured error boundary for database operations. UI layers map this to
 * user-facing error/retry states instead of catching untyped exceptions.
 */
sealed class StoreResult<out T> {
    data class Ok<T>(val value: T) : StoreResult<T>()
    data class TransientError(val cause: Exception) : StoreResult<Nothing>()
    data class PermanentError(val cause: Exception) : StoreResult<Nothing>()

    fun isOk(): Boolean = this is Ok
    fun valueOrNull(): T? = (this as? Ok)?.value

    companion object {
        @JvmStatic
        fun <T> ok(value: T): StoreResult<T> = Ok(value)

        @JvmStatic
        fun <T> transient(cause: Exception): StoreResult<T> = TransientError(cause)

        @JvmStatic
        fun <T> permanent(cause: Exception): StoreResult<T> = PermanentError(cause)
    }
}
