package top.katton.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import top.katton.api.LOGGER
import top.katton.pack.ScriptPack
import top.katton.pack.ScriptPackKind
import top.katton.pack.ScriptPackScope
import top.katton.util.ScriptExecutionContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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

    fun set(packId: String, key: String, value: Any): Boolean {
        val config = packConfigs[packId] ?: return false
        config[key] = value
        persistPackConfig(packId)
        return true
    }

    fun remove(packId: String, key: String): Boolean {
        val config = packConfigs[packId] ?: return false
        val removed = config.remove(key) != null
        if (removed) persistPackConfig(packId)
        return removed
    }

    fun all(packId: String): Map<String, Any> {
        return packConfigs[packId]?.toMap() ?: emptyMap()
    }

    fun knownPackIds(): Set<String> = packConfigs.keys.toSet()

    // ── Script context resolution ──────────────────────────────────

    /**
     * Resolves the current script's pack ID from [ScriptExecutionContext.currentScriptOwner].
     * Owner format: `"<scope_serialized>:<fqcn>"` (e.g. `"global:top.katton.mypack.MainKt"`).
     */
    fun resolveCurrentPack(): String? {
        val owner = ScriptExecutionContext.currentScriptOwner() ?: return null
        // Owner format: "scope:fqcn"
        val colonIndex = owner.indexOf(':')
        if (colonIndex < 0) return null
        val fqcn = owner.substring(colonIndex + 1)
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

    private fun persistPackConfig(packId: String) {
        val meta = packMeta[packId] ?: return
        if (meta.kind != ScriptPackKind.DIRECTORY) return

        val config = packConfigs[packId] ?: return
        val manifestFile = meta.location.resolve(MANIFEST_FILE_NAME)
        if (!Files.isRegularFile(manifestFile)) return

        runCatching {
            val raw = Files.readString(manifestFile, StandardCharsets.UTF_8)
            val root = JsonParser.parseString(raw).asJsonObject

            val configJson = JsonObject()
            for ((key, value) in config) {
                when (value) {
                    is String -> configJson.addProperty(key, value)
                    is Number -> configJson.addProperty(key, value)
                    is Boolean -> configJson.addProperty(key, value)
                }
            }
            root.add("config", configJson)

            Files.writeString(manifestFile, root.toString(), StandardCharsets.UTF_8)
            LOGGER.info("Persisted config for pack '{}' to {}", packId, manifestFile)
        }.onFailure {
            LOGGER.warn("Failed to persist config for pack '{}'", packId, it)
        }
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
