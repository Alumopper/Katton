package top.katton.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import top.katton.api.LOGGER
import top.katton.pack.SafePackFileIo
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackFileLimits
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime configuration keyed by a scope-qualified ID. Local packs use
 * [ScriptPack.syncId]; server-cache packs add a `server_cache:` prefix so their
 * preserved network identity cannot collide with a local pack.
 *
 * User overrides are stored outside the pack, so directory packs, signed packs,
 * and JAR packs all have identical persistence semantics.
 */
object KattonConfigManager {
    private data class PackConfigMeta(
        val configId: String,
        val manifestId: String,
        val location: Path,
        val kind: ScriptPackKind,
        val scope: ScriptPackScope
    )

    private val packConfigs = ConcurrentHashMap<String, ConcurrentHashMap<String, Any>>()
    private val packMeta = ConcurrentHashMap<String, PackConfigMeta>()
    /** `<scope>:<fqcn>` to syncId. */
    private val fqcnToSyncId = ConcurrentHashMap<String, String>()

    @Volatile
    private var storageDirectory: Path? = null

    @JvmStatic
    fun setStorageDirectory(path: Path?) {
        storageDirectory = path?.toAbsolutePath()?.normalize()
    }

    fun registerPack(pack: ScriptPack) {
        val configId = configId(pack)
        packMeta[configId] = PackConfigMeta(
            configId = configId,
            manifestId = pack.manifest.id,
            location = pack.location,
            kind = pack.kind,
            scope = pack.scope
        )
        val effective = loadOverride(configId) ?: pack.manifest.config
        packConfigs[configId] = ConcurrentHashMap<String, Any>().apply { putAll(effective) }
    }

    /** SERVER_CACHE preserves the server's original syncId, so add its runtime scope for config isolation. */
    fun configId(pack: ScriptPack): String =
        if (pack.scope == ScriptPackScope.SERVER_CACHE) "${pack.scope.serializedName}:${pack.syncId}" else pack.syncId

    fun registerFqcnMapping(fqcn: String, configId: String) {
        val scope = packMeta[configId]?.scope?.serializedName
            ?: configId.substringBefore(':', missingDelimiterValue = "")
        if (scope.isBlank()) return
        fqcnToSyncId["$scope:$fqcn"] = configId
    }

    fun registerScriptFile(packId: String, fileContent: String, fileName: String) {
        val syncId = resolveSyncId(packId) ?: return
        registerFqcnMapping(deriveFqcn(fileContent, fileName), syncId)
    }

    fun get(packId: String, key: String): Any? =
        resolveSyncId(packId)?.let(packConfigs::get)?.get(key)

    @Synchronized
    fun set(packId: String, key: String, value: Any): Boolean {
        if (value !is String && value !is Number && value !is Boolean) return false
        val syncId = resolveSyncId(packId) ?: return false
        val config = packConfigs[syncId] ?: return false
        val previous = config.put(key, value)
        if (persistPackConfig(syncId)) return true
        if (previous == null) config.remove(key) else config[key] = previous
        return false
    }

    @Synchronized
    fun remove(packId: String, key: String): Boolean {
        val syncId = resolveSyncId(packId) ?: return false
        val config = packConfigs[syncId] ?: return false
        val previous = config.remove(key) ?: return false
        if (persistPackConfig(syncId)) return true
        config[key] = previous
        return false
    }

    fun all(packId: String): Map<String, Any> =
        resolveSyncId(packId)?.let(packConfigs::get)?.toMap() ?: emptyMap()

    /** Scope-qualified IDs are intentionally returned to avoid global/world ambiguity. */
    fun knownPackIds(): Set<String> = packConfigs.keys.toSet()

    fun resolveCurrentPack(): String? {
        val owner = ScriptExecutionContext.currentScriptOwner() ?: return null
        val parts = owner.split(':', limit = 3)
        if (parts.size != 3) return null
        return fqcnToSyncId["${parts[0]}:${parts[2]}"]
    }

    fun clearPack(packId: String) {
        val syncId = resolveSyncId(packId) ?: return
        packConfigs.remove(syncId)
        packMeta.remove(syncId)
        fqcnToSyncId.entries.removeAll { it.value == syncId }
    }

    fun clearAll() {
        packConfigs.clear()
        packMeta.clear()
        fqcnToSyncId.clear()
    }

    /** Removes runtime mappings for inactive packs without deleting persisted overrides. */
    @Synchronized
    fun retainPacks(scopes: Set<ScriptPackScope>, retainedConfigIds: Set<String>) {
        val staleIds = packMeta.values
            .filter { it.scope in scopes && it.configId !in retainedConfigIds }
            .map { it.configId }
        staleIds.forEach { syncId ->
            packConfigs.remove(syncId)
            packMeta.remove(syncId)
            fqcnToSyncId.entries.removeAll { it.value == syncId }
        }
    }

    private fun resolveSyncId(packId: String): String? {
        if (packConfigs.containsKey(packId)) return packId
        val matches = packMeta.values.filter { it.manifestId == packId }.map { it.configId }
        return matches.singleOrNull().also {
            if (it == null && matches.size > 1) {
                LOGGER.warn("Config pack id '{}' is ambiguous; use one of {}", packId, matches.sorted())
            }
        }
    }

    private fun loadOverride(syncId: String): Map<String, Any>? {
        val path = overridePath(syncId) ?: return null
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) return null
        return runCatching {
            val raw = SafePackFileIo.readUtf8(path, ScriptPackFileLimits.MAX_STATE_BYTES, "script pack config override")
            val root = JsonParser.parseString(raw)
            require(root.isJsonObject) { "config override root must be an object" }
            root.asJsonObject.toConfigMap()
        }.onFailure {
            LOGGER.warn("Failed to load config override for pack '{}' from {}", syncId, path, it)
        }.getOrNull()
    }

    private fun persistPackConfig(syncId: String): Boolean {
        val path = overridePath(syncId) ?: return false
        val config = packConfigs[syncId] ?: return false
        return runCatching {
            val configJson = JsonObject()
            config.toSortedMap().forEach { (key, value) ->
                when (value) {
                    is String -> configJson.addProperty(key, value)
                    is Number -> configJson.addProperty(key, value)
                    is Boolean -> configJson.addProperty(key, value)
                }
            }
            SafePackFileIo.writeUtf8Atomically(
                path,
                configJson.toString(),
                ScriptPackFileLimits.MAX_STATE_BYTES,
                "script pack config override"
            )
            LOGGER.info("Persisted config override for pack '{}' to {}", syncId, path)
            true
        }.onFailure {
            LOGGER.warn("Failed to persist config override for pack '{}'", syncId, it)
        }.getOrDefault(false)
    }

    private fun overridePath(syncId: String): Path? {
        val root = storageDirectory ?: return null
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(syncId.toByteArray(Charsets.UTF_8))
        return root.resolve("$encoded.json")
    }

    private fun JsonObject.toConfigMap(): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        entrySet().forEach { (key, value) ->
            if (!value.isJsonPrimitive) return@forEach
            val primitive = value.asJsonPrimitive
            when {
                primitive.isString -> result[key] = primitive.asString
                primitive.isBoolean -> result[key] = primitive.asBoolean
                primitive.isNumber -> result[key] = primitive.asNumber
            }
        }
        return result
    }

    private val packageRegex = Regex("""package\s+([\w.]+)""")
    private val fileJvmNameRegex = Regex(
        "@file:\\s*(?:kotlin\\.jvm\\.)?JvmName\\(\\s*\"([A-Za-z_\$][A-Za-z0-9_\$]*)\"\\s*\\)"
    )

    fun deriveFqcn(fileContent: String, fileName: String): String {
        val packageName = packageRegex.find(fileContent)?.groupValues?.get(1).orEmpty()
        val baseName = fileName.removeSuffix(".kt").removeSuffix(".kts")
        val className = fileJvmNameRegex.find(fileContent)?.groupValues?.get(1)
            ?: baseName.replaceFirstChar { it.uppercaseChar() } + "Kt"
        return if (packageName.isEmpty()) className else "$packageName.$className"
    }
}
