package top.katton.api

/**
 * Marks a top-level no-argument function as a server-side script entrypoint.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ServerScriptEntrypoint
