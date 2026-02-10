package top.katton.util

class MessageOnlyException(message: String) : Exception(message) {
    override fun fillInStackTrace(): Throwable? {
        return this
    }
}