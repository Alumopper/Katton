package top.katton.engine

import top.katton.api.ClientPhase
import top.katton.api.InvocationReason
import top.katton.api.ServerPhase
import top.katton.pack.ScriptPackScope

/** Pure lifecycle rules shared by entrypoint validation and tests. */
object ScriptLifecyclePolicy {
    fun isValid(scope: ScriptPackScope, environment: ScriptEnvironment, phaseName: String): Boolean =
        when (environment) {
            ScriptEnvironment.SERVER -> when (ServerPhase.valueOf(phaseName)) {
                ServerPhase.BOOTSTRAP -> scope == ScriptPackScope.GLOBAL
                ServerPhase.READY -> scope == ScriptPackScope.GLOBAL || scope == ScriptPackScope.WORLD
            }
            ScriptEnvironment.CLIENT -> when (ClientPhase.valueOf(phaseName)) {
                ClientPhase.READY -> scope == ScriptPackScope.GLOBAL
                ClientPhase.REGISTRY_SETUP, ClientPhase.JOINED ->
                    scope == ScriptPackScope.WORLD || scope == ScriptPackScope.SERVER_CACHE
            }
        }

    fun shouldInvoke(scope: ScriptPackScope, reason: InvocationReason, replay: Boolean): Boolean {
        if (reason == InvocationReason.INITIAL_LOAD) return true
        return when (scope) {
            ScriptPackScope.GLOBAL -> false
            ScriptPackScope.WORLD -> replay
            ScriptPackScope.SERVER_CACHE -> true
        }
    }
}
