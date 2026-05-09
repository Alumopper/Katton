package top.katton.engine

import top.katton.api.ClientScriptEntrypoint
import top.katton.api.ServerScriptEntrypoint
import kotlin.reflect.KClass

enum class ScriptEnvironment(
    val annotationClass: Class<*>, val annotationClassName: String = annotationClass.kotlin.qualifiedName!!
) {
    CLIENT(ClientScriptEntrypoint::class.java),
    SERVER(ServerScriptEntrypoint::class.java)
}
