package top.katton.util

import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope

/**
 * Tracks the currently executing script context during script entrypoint invocation.
 *
 * This object maintains owner, scope, and environment [ThreadLocal] values so that
 * registration APIs (items, blocks, effects, events, commands, injections) can
 * determine which script they are being called from without requiring every
 * API function to pass explicit context parameters.
 *
 * Entrypoint owners use `"<scope>:<phase>:<fqcn>"` so registrations from
 * different lifecycle phases can be replaced independently.
 */
object ScriptExecutionContext {
    private val currentScriptOwner = ThreadLocal<String?>()
    private val currentScriptScope = ThreadLocal<ScriptPackScope?>()
    private val currentScriptEnvironment = ThreadLocal<ScriptEnvironment?>()

    fun currentScriptOwner(): String? = currentScriptOwner.get()
    fun currentScriptScope(): ScriptPackScope? = currentScriptScope.get()
    fun currentScriptEnvironment(): ScriptEnvironment? = currentScriptEnvironment.get()

    /**
     * Executes [action] with [owner] as the current script owner.
     * The previous owner (if any) is restored in a `finally` block.
     */
    fun <R> withOwner(owner: String?, action: () -> R): R {
        if (owner == null) return action()
        val previous = currentScriptOwner.get()
        currentScriptOwner.set(owner)
        return try {
            action()
        } finally {
            if (previous == null) {
                currentScriptOwner.remove()
            } else {
                currentScriptOwner.set(previous)
            }
        }
    }

    /**
     * Executes [action] with [scope] as the current script scope.
     * The previous scope (if any) is restored in a `finally` block.
     */
    fun <R> withScope(scope: ScriptPackScope?, action: () -> R): R {
        if (scope == null) return action()
        val previous = currentScriptScope.get()
        currentScriptScope.set(scope)
        return try {
            action()
        } finally {
            if (previous == null) {
                currentScriptScope.remove()
            } else {
                currentScriptScope.set(previous)
            }
        }
    }

    fun <R> withEnvironment(environment: ScriptEnvironment?, action: () -> R): R {
        if (environment == null) return action()
        val previous = currentScriptEnvironment.get()
        currentScriptEnvironment.set(environment)
        return try {
            action()
        } finally {
            if (previous == null) {
                currentScriptEnvironment.remove()
            } else {
                currentScriptEnvironment.set(previous)
            }
        }
    }
}
