package top.katton.engine

import top.katton.api.ClientScriptEntrypoint
import top.katton.api.ServerScriptEntrypoint

enum class ScriptEnvironment(
    val annotationClassName: String
) {
    CLIENT(ClientScriptEntrypoint::class.qualifiedName!!),
    SERVER(ServerScriptEntrypoint::class.qualifiedName!!)
}
