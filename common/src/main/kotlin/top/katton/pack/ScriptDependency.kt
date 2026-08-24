package top.katton.pack

import java.util.Locale

enum class ScriptPlatform(val serializedName: String) {
    UNKNOWN("unknown"),
    FABRIC("fabric"),
    NEOFORGE("neoforge"),
    PAPER("paper");

    companion object {
        fun parse(value: String): ScriptPlatform? =
            entries.firstOrNull { it.serializedName == value.lowercase(Locale.ROOT) }
    }
}

enum class DependencyEnvironment(val serializedName: String) {
    SERVER("server"),
    CLIENT("client"),
    BOTH("both");

    companion object {
        fun parse(value: String): DependencyEnvironment? =
            entries.firstOrNull { it.serializedName == value.lowercase(Locale.ROOT) }
    }
}

data class ScriptDependency(
    val id: String,
    val version: String,
    val required: Boolean,
    val platforms: Set<ScriptPlatform>,
    val environment: DependencyEnvironment
) {
    fun appliesTo(platform: ScriptPlatform, client: Boolean): Boolean {
        if (platform !in platforms) return false
        return when (environment) {
            DependencyEnvironment.BOTH -> true
            DependencyEnvironment.CLIENT -> client
            DependencyEnvironment.SERVER -> !client
        }
    }
}
