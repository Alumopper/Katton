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
    val clientSync: Boolean,
    val signature: ScriptPackSignature?,
    val dependencies: List<ScriptDependency>,
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
            val clientSync = root.booleanOrNull("clientSync") ?: true
            val signature = root.jsonObjectOrNull("signature")?.toSignature()
            val dependencies = root.dependenciesOrThrow(packPath)
            val config = root.jsonObjectOrNull("config")?.toConfigMap().orEmpty()

            return ScriptPackManifest(
                id = id,
                name = name,
                version = version,
                description = description,
                authors = authors,
                enabledByDefault = enabled,
                clientSync = clientSync,
                signature = signature,
                dependencies = dependencies,
                config = config
            )
        }
    }
}

private fun JsonObject.dependenciesOrThrow(packPath: Path): List<ScriptDependency> {
    if (!has("dependencies")) {
        throw IllegalArgumentException(
            "Pack manifest at $packPath must declare a dependencies array; use \"dependencies\": [] when none are required"
        )
    }
    val dependenciesElement = get("dependencies")
    require(dependenciesElement.isJsonArray) { "Pack manifest dependencies at $packPath must be an array" }
    return dependenciesElement.asJsonArray.mapIndexed { index, element ->
        require(element.isJsonObject) { "Dependency #$index at $packPath must be an object" }
        element.asJsonObject.toDependency(packPath, index)
    }
}

private fun JsonObject.toDependency(packPath: Path, index: Int): ScriptDependency {
    val id = stringOrNull("id")?.trim().orEmpty()
    require(id.isNotEmpty()) { "Dependency #$index at $packPath must declare a non-empty id" }
    val version = stringOrNull("version")?.trim()?.takeIf(String::isNotEmpty) ?: "*"
    val required = booleanOrNull("required") ?: true
    val environmentValue = stringOrNull("environment") ?: DependencyEnvironment.BOTH.serializedName
    val environment = DependencyEnvironment.parse(environmentValue)
        ?: throw IllegalArgumentException(
            "Dependency '$id' at $packPath has invalid environment '$environmentValue' (expected server, client, or both)"
        )
    val platformsElement = get("platforms")
    require(platformsElement != null && platformsElement.isJsonArray) {
        "Dependency '$id' at $packPath must declare a non-empty platforms array"
    }
    val platforms = platformsElement.asJsonArray.map { platformElement ->
        require(platformElement.isJsonPrimitive && platformElement.asJsonPrimitive.isString) {
            "Dependency '$id' at $packPath contains a non-string platform"
        }
        val value = platformElement.asString
        ScriptPlatform.parse(value)
            ?.takeIf { it != ScriptPlatform.UNKNOWN }
            ?: throw IllegalArgumentException("Dependency '$id' at $packPath has unsupported platform '$value'")
    }.toSet()
    require(platforms.isNotEmpty()) { "Dependency '$id' at $packPath must target at least one platform" }
    return ScriptDependency(id, version, required, platforms, environment)
}

data class ScriptPackSignature(
    val algorithm: String,
    val keyId: String,
    val publicKey: String?,
    val signature: String
)

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

private fun JsonObject.toSignature(): ScriptPackSignature? {
    val algorithm = stringOrNull("algorithm")?.takeIf { it.isNotBlank() } ?: "Ed25519"
    val keyId = stringOrNull("keyId")?.takeIf { it.isNotBlank() } ?: return null
    val signature = stringOrNull("signature")?.takeIf { it.isNotBlank() } ?: return null
    return ScriptPackSignature(
        algorithm = algorithm,
        keyId = keyId,
        publicKey = stringOrNull("publicKey")?.takeIf { it.isNotBlank() },
        signature = signature
    )
}
