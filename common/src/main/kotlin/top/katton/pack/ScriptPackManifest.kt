package top.katton.pack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.nio.file.Path

data class ScriptPackManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val authors: List<String>,
    val enabledByDefault: Boolean,
    val config: Map<String, Any> = emptyMap()
) {
    companion object {
        fun parse(packPath: Path, manifestJson: String): ScriptPackManifest {
            val root = runCatching { JsonParser.parseString(manifestJson).asJsonObject }
                .getOrElse { JsonObject() }

            val fileName = packPath.fileName.toString()
            val fallbackId = if (fileName.endsWith(".jar")) fileName.removeSuffix(".jar") else fileName
            val id = root.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: fallbackId
            val name = root.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: id
            val version = root.stringOrNull("version") ?: "unknown"
            val description = root.stringOrNull("description") ?: ""
            val authors = root.arrayOrNull("authors")?.toStringList().orEmpty()
            val enabled = root.booleanOrNull("enabled") ?: true
            val config = root.jsonObjectOrNull("config")?.toConfigMap().orEmpty()

            return ScriptPackManifest(
                id = id,
                name = name,
                version = version,
                description = description,
                authors = authors,
                enabledByDefault = enabled,
                config = config
            )
        }
    }
}

private fun JsonObject.stringOrNull(key: String): String? {
    val element = this.get(key) ?: return null
    return if (element.isJsonPrimitive && element.asJsonPrimitive.isString) element.asString else null
}

private fun JsonObject.booleanOrNull(key: String): Boolean? {
    val element = this.get(key) ?: return null
    return if (element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) element.asBoolean else null
}

private fun JsonObject.arrayOrNull(key: String): JsonArray? {
    val element = this.get(key) ?: return null
    return if (element.isJsonArray) element.asJsonArray else null
}

private fun JsonArray.toStringList(): List<String> {
    return buildList {
        for (element in this@toStringList) {
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                add(element.asString)
            }
        }
    }
}

private fun JsonObject.jsonObjectOrNull(key: String): JsonObject? {
    val element = this.get(key) ?: return null
    return if (element.isJsonObject) element.asJsonObject else null
}

private fun JsonObject.toConfigMap(): Map<String, Any> {
    val map = LinkedHashMap<String, Any>()
    for ((key, value) in entrySet()) {
        when {
            value.isJsonPrimitive -> {
                val primitive = value.asJsonPrimitive
                when {
                    primitive.isString -> map[key] = primitive.asString
                    primitive.isNumber -> map[key] = primitive.asNumber
                    primitive.isBoolean -> map[key] = primitive.asBoolean
                }
            }
        }
    }
    return map
}
