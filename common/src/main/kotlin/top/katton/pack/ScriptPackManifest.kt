package top.katton.pack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.nio.file.Path

private const val MAX_MANIFEST_DEPENDENCIES = 1_024
private const val MAX_PACK_ID_LENGTH = 256
private const val MAX_PACK_NAME_LENGTH = 512
private const val MAX_PACK_VERSION_LENGTH = 256
private const val MAX_PACK_DESCRIPTION_LENGTH = 16_384
private const val MAX_AUTHORS = 256
private const val MAX_AUTHOR_LENGTH = 256
private const val MAX_DEPENDENCY_ID_LENGTH = 256
private const val MAX_DEPENDENCY_VERSION_LENGTH = 256
private const val MAX_SIGNATURE_ALGORITHM_LENGTH = 32
private const val MAX_SIGNATURE_KEY_ID_LENGTH = 256
private const val MAX_PUBLIC_KEY_TEXT_LENGTH = 1_024
private const val MAX_SIGNATURE_TEXT_LENGTH = 512

/** Current unambiguous, length-framed remote pack signature format. */
internal const val SCRIPT_PACK_SIGNATURE_PAYLOAD_VERSION = 2
private val POSITIVE_INTEGER_PATTERN = Regex("[1-9][0-9]*")

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
        /** Parses a manifest using its folder or JAR filename as the fallback ID. */
        fun parse(packPath: Path, manifestJson: String): ScriptPackManifest =
            parse(packPath, manifestJson, fallbackIdOverride = null)

        /** Cache loading supplies the server-side fallback ID because its folder name is encoded. */
        internal fun parse(
            packPath: Path,
            manifestJson: String,
            fallbackIdOverride: String?
        ): ScriptPackManifest {
            val root = runCatching {
                val parsed = JsonParser.parseString(manifestJson)
                require(parsed.isJsonObject) { "manifest root must be a JSON object" }
                parsed.asJsonObject
            }.getOrElse { cause ->
                throw IllegalArgumentException("Invalid Katton pack manifest JSON at $packPath", cause)
            }

            val fileName = packPath.fileName.toString()
            val fallbackId = fallbackIdOverride
                ?: if (fileName.endsWith(".jar")) fileName.removeSuffix(".jar") else fileName
            val id = root.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: fallbackId
            val name = root.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: id
            val version = root.stringOrNull("version") ?: "unknown"
            val description = root.stringOrNull("description") ?: ""
            val authors = root.arrayOrNull("authors")?.toStringList().orEmpty()
            require(id.length <= MAX_PACK_ID_LENGTH) {
                "Pack id at $packPath is longer than $MAX_PACK_ID_LENGTH characters"
            }
            require(name.length <= MAX_PACK_NAME_LENGTH) {
                "Pack name at $packPath is longer than $MAX_PACK_NAME_LENGTH characters"
            }
            require(version.length <= MAX_PACK_VERSION_LENGTH) {
                "Pack version at $packPath is longer than $MAX_PACK_VERSION_LENGTH characters"
            }
            require(description.length <= MAX_PACK_DESCRIPTION_LENGTH) {
                "Pack description at $packPath is longer than $MAX_PACK_DESCRIPTION_LENGTH characters"
            }
            require(authors.size <= MAX_AUTHORS && authors.all { it.length <= MAX_AUTHOR_LENGTH }) {
                "Pack authors at $packPath exceed the count or length limit"
            }
            val enabled = root.booleanOrNull("enabled") ?: true
            val clientSync = root.booleanOrNull("clientSync") ?: true
            val signatureElement = root.get("signature")
            val signature = when {
                signatureElement == null || signatureElement.isJsonNull -> null
                signatureElement.isJsonObject -> signatureElement.asJsonObject.toSignature(packPath)
                else -> throw IllegalArgumentException("Pack signature at $packPath must be an object")
            }
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
    require(dependenciesElement.asJsonArray.size() <= MAX_MANIFEST_DEPENDENCIES) {
        "Pack manifest at $packPath declares too many dependencies " +
            "(${dependenciesElement.asJsonArray.size()}, maximum $MAX_MANIFEST_DEPENDENCIES)"
    }
    val parsedDependencies = ArrayList<ScriptDependency>(dependenciesElement.asJsonArray.size())
    return dependenciesElement.asJsonArray.mapIndexed { index, element ->
        require(element.isJsonObject) { "Dependency #$index at $packPath must be an object" }
        element.asJsonObject.toDependency(packPath, index).also { dependency ->
            val overlaps = parsedDependencies.any { existing ->
                existing.id.equals(dependency.id, ignoreCase = true) &&
                    existing.platforms.any(dependency.platforms::contains) &&
                    environmentsOverlap(existing.environment, dependency.environment)
            }
            require(!overlaps) {
                "Dependency '${dependency.id}' has overlapping declarations at $packPath"
            }
            parsedDependencies += dependency
        }
    }
}

private fun environmentsOverlap(left: DependencyEnvironment, right: DependencyEnvironment): Boolean =
    left == right || left == DependencyEnvironment.BOTH || right == DependencyEnvironment.BOTH

private fun JsonObject.toDependency(packPath: Path, index: Int): ScriptDependency {
    val id = stringOrNull("id")?.trim().orEmpty()
    require(id.isNotEmpty()) { "Dependency #$index at $packPath must declare a non-empty id" }
    require(id.length <= MAX_DEPENDENCY_ID_LENGTH) {
        "Dependency #$index at $packPath has an id longer than $MAX_DEPENDENCY_ID_LENGTH characters"
    }
    val version = stringOrNull("version")?.trim()?.takeIf(String::isNotEmpty) ?: "*"
    require(version.length <= MAX_DEPENDENCY_VERSION_LENGTH) {
        "Dependency '$id' at $packPath has a version constraint longer than $MAX_DEPENDENCY_VERSION_LENGTH characters"
    }
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
) {
    private var payloadVersionValue: Int = 1

    /** Missing on legacy v1 manifests; the verifier intentionally rejects v1. */
    val payloadVersion: Int
        get() = payloadVersionValue

    /** Keeps the original four-field data-class ABI while allowing the parser to record v2. */
    constructor(
        algorithm: String,
        keyId: String,
        publicKey: String?,
        signature: String,
        payloadVersion: Int
    ) : this(algorithm, keyId, publicKey, signature) {
        this.payloadVersionValue = payloadVersion
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

private fun JsonObject.toSignature(packPath: Path): ScriptPackSignature {
    val algorithm = stringOrNull("algorithm")?.trim()?.takeIf { it.isNotEmpty() } ?: "Ed25519"
    require(algorithm.length <= MAX_SIGNATURE_ALGORITHM_LENGTH) {
        "Pack signature algorithm at $packPath is too long"
    }
    val keyId = stringOrNull("keyId")?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Pack signature at $packPath must declare a non-empty keyId")
    require(keyId.length <= MAX_SIGNATURE_KEY_ID_LENGTH) {
        "Pack signature keyId at $packPath is longer than $MAX_SIGNATURE_KEY_ID_LENGTH characters"
    }
    val signature = stringOrNull("signature")?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Pack signature at $packPath must contain a signature value")
    require(signature.length <= MAX_SIGNATURE_TEXT_LENGTH) {
        "Pack signature value at $packPath is too long"
    }
    val publicKey = stringOrNull("publicKey")?.takeIf { it.isNotBlank() }
    require(publicKey == null || publicKey.length <= MAX_PUBLIC_KEY_TEXT_LENGTH) {
        "Pack signature public key at $packPath is too long"
    }
    val payloadVersion = when (val element = get("payloadVersion")) {
        null -> 1
        else -> {
            require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
                "Pack signature payloadVersion at $packPath must be an integer"
            }
            val numberText = element.asJsonPrimitive.asString
            require(numberText.matches(POSITIVE_INTEGER_PATTERN)) {
                "Pack signature payloadVersion at $packPath must be a positive integer"
            }
            numberText.toIntOrNull()
                ?: throw IllegalArgumentException("Pack signature payloadVersion at $packPath is too large")
        }
    }
    return ScriptPackSignature(
        algorithm = algorithm,
        keyId = keyId,
        publicKey = publicKey,
        signature = signature,
        payloadVersion = payloadVersion
    )
}
