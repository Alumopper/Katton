package top.katton.api

/**
 * %en
 * Marks a top-level no-argument function as a client script entrypoint.
 *
 * %zh
 * 将一个顶层无参数函数标记为客户端脚本入口点。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ClientScriptEntrypoint

/**
 * %en
 * Marks a top-level no-argument function as a server script entrypoint.
 *
 * %zh
 * 将一个顶层无参数函数标记为服务端脚本入口点。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ServerScriptEntrypoint
