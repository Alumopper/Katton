package top.katton.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import top.katton.api.LOGGER
import top.katton.pack.ScriptPack
import top.katton.pack.SafePackFileIo
import top.katton.pack.ScriptPackFileLimits
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime configuration manager. Loaded from manifest.json "config" field,
 * modifiable via /katton config command, readable from scripts via KattonConfigApi.
 */
object KattonConfigManager {

    private const val MANIFEST_FILE_NAME = "manifest.json"

    private data class PackConfigMeta(
        val location: Path,
        val kind: ScriptPackKind,
        val scope: ScriptPackScope
    )

    /** packId → config entries */
    private val packConfigs = ConcurrentHashMap<String, ConcurrentHashMap<String, Any>>()

    /** packId → pack metadata for persistence */
    private val packMeta = ConcurrentHashMap<String, PackConfigMeta>()

    /** FQCN (no scope prefix) → packId — populated during script loading */
    private val fqcnToPackId = ConcurrentHashMap<String, String>()

    // ── Pack registration ──────────────────────────────────────────

    fun registerPack(pack: ScriptPack) {
        packMeta[pack.manifest.id] = PackConfigMeta(
            location = pack.location,
            kind = pack.kind,
            scope = pack.scope
        )
        val config = ConcurrentHashMap<String, Any>()
        config.putAll(pack.manifest.config)
        packConfigs[pack.manifest.id] = config
    }

    fun registerFqcnMapping(fqcn: String, packId: String) {
        fqcnToPackId[fqcn] = packId
    }

    /** Derive FQCN from a .kt source file and register it to the given pack. */
    fun registerScriptFile(packId: String, fileContent: String, fileName: String) {
        val fqcn = deriveFqcn(fileContent, fileName)
        registerFqcnMapping(fqcn, packId)
    }

    // ── Runtime access ─────────────────────────────────────────────

    fun get(packId: String, key: String): Any? {
        return packConfigs[packId]?.get(key)
    }

    @Synchronized
    fun set(packId: String, key: String, value: Any): Boolean {
        if (value !is String && value !is Number && value !is Boolean) return false
        val config = packConfigs[packId] ?: return false
        val previous = config.put(key, value)
        if (persistPackConfig(packId)) return true

        // Keep the in-memory view consistent with disk if persistence was
        // rejected (for example, because the manifest is signed).
        if (previous == null) config.remove(key) else config[key] = previous
        return false
    }

    @Synchronized
    fun remove(packId: String, key: String): Boolean {
        val config = packConfigs[packId] ?: return false
        val previous = config.remove(key) ?: return false
        if (persistPackConfig(packId)) return true
        config[key] = previous
        return false
    }

    fun all(packId: String): Map<String, Any> {
        return packConfigs[packId]?.toMap() ?: emptyMap()
    }

    fun knownPackIds(): Set<String> = packConfigs.keys.toSet()

    // ── Script context resolution ──────────────────────────────────

    /**
     * Resolves the current script's pack ID from [ScriptExecutionContext.currentScriptOwner].
     * Owner format: `"<scope_serialized>:<phase>:<fqcn>"`.
     */
    fun resolveCurrentPack(): String? {
        val owner = ScriptExecutionContext.currentScriptOwner() ?: return null
        val parts = owner.split(':', limit = 3)
        if (parts.size < 2) return null
        val fqcn = if (parts.size == 3) parts[2] else parts[1]
        return fqcnToPackId[fqcn]
    }

    // ── Cleanup ────────────────────────────────────────────────────

    fun clearPack(packId: String) {
        packConfigs.remove(packId)
        packMeta.remove(packId)
        fqcnToPackId.entries.removeAll { it.value == packId }
    }

    fun clearAll() {
        packConfigs.clear()
        packMeta.clear()
        fqcnToPackId.clear()
    }

    // ── Persistence ────────────────────────────────────────────────

    private fun persistPackConfig(packId: String): Boolean {
        val meta = packMeta[packId] ?: return false
        // JAR manifests cannot be rewritten; retaining the value in memory is
        // still useful for the current session and preserves previous behavior.
        if (meta.kind != ScriptPackKind.DIRECTORY) return true

        val config = packConfigs[packId] ?: return false
        val manifestFile = meta.location.resolve(MANIFEST_FILE_NAME)
        if (!Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(manifestFile)) {
            return false
        }

        return runCatching {
            val raw = SafePackFileIo.readUtf8(
                manifestFile,
                ScriptPackFileLimits.MAX_MANIFEST_BYTES,
                "script pack manifest"
            )
            val root = JsonParser.parseString(raw).asJsonObject
            require(!root.has("signature")) {
                "signed manifest cannot be modified; change config and run signKattonPack again"
            }

            val configJson = JsonObject()
            for ((key, value) in config) {
                when (value) {
                    is String -> configJson.addProperty(key, value)
                    is Number -> configJson.addProperty(key, value)
                    is Boolean -> configJson.addProperty(key, value)
                }
            }
            root.add("config", configJson)

            SafePackFileIo.writeUtf8Atomically(
                manifestFile,
                root.toString(),
                ScriptPackFileLimits.MAX_MANIFEST_BYTES,
                "script pack manifest"
            )
            LOGGER.info("Persisted config for pack '{}' to {}", packId, manifestFile)
            true
        }.onFailure {
            LOGGER.warn("Failed to persist config for pack '{}'", packId, it)
        }.getOrDefault(false)
    }

    // ── FQCN derivation ────────────────────────────────────────────

    private val packageRegex = Regex("""package\s+([\w.]+)""")

    /**
     * Derives the expected compiled FQCN from a .kt source file.
     * Example: file `main.kt` with `package com.example` → `com.example.MainKt`
     */
    fun deriveFqcn(fileContent: String, fileName: String): String {
        val packageMatch = packageRegex.find(fileContent)
        val packageName = packageMatch?.groupValues?.get(1) ?: ""
        val baseName = fileName.removeSuffix(".kt").removeSuffix(".kts")
        val className = baseName.replaceFirstChar { it.uppercaseChar() } + "Kt"
        return if (packageName.isEmpty()) className else "$packageName.$className"
    }
}
