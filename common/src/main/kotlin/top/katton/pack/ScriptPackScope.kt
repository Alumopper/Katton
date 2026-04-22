package top.katton.pack

enum class ScriptPackScope(
    val serializedName: String,
    val displayName: String
) {
    GLOBAL("global", "Global"),
    WORLD("world", "World"),
    SERVER_CACHE("server_cache", "Server Cache");

    companion object {
        fun fromSerializedName(value: String): ScriptPackScope {
            return entries.firstOrNull { it.serializedName == value } ?: GLOBAL
        }
    }
}
