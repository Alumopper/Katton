package top.katton.api

/**
 * Marks a top-level no-argument function as a client-side script entrypoint.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ClientScriptEntrypoint
