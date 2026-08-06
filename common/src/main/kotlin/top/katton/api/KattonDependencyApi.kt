package top.katton.api

import top.katton.engine.ScriptDependencyManager

/** Script-facing access to optional mod and plugin dependency state. */
object dependencies {
    /** Returns whether the current platform dependency is installed and enabled. */
    fun isLoaded(id: String): Boolean = ScriptDependencyManager.find(id)?.enabled == true

    /** Returns the installed dependency version, or `null` when the dependency is unavailable. */
    fun version(id: String): String? = ScriptDependencyManager.find(id)?.version

    /**
     * Returns the installed and enabled dependency.
     *
     * @throws IllegalStateException when the dependency is not installed or enabled
     */
    fun require(id: String) = ScriptDependencyManager.find(id)
        ?.takeIf { it.enabled }
        ?: error("Required dependency '$id' is not installed or enabled")
}
