@file:Suppress("unused")

package top.katton.api.mod

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.storage.loot.LootTable
import org.jetbrains.annotations.ApiStatus
import org.slf4j.LoggerFactory
import top.katton.api.server
import top.katton.datapack.ServerDatapackManager
import top.katton.registry.id

private val LOGGER = LoggerFactory.getLogger("top.katton.api.mod.KattonLootTableModificationApi")

/**
 * Returns the JSON form of an existing loot table, or `null` when the table is
 * not registered or the server is offline.
 *
 * Reads from `server.reloadableRegistries()`, which is where loot tables live
 * in MC 1.21.5+, then re-encodes through [LootTable.DIRECT_CODEC].
 */
@ApiStatus.Experimental
fun getLootTable(lootTableId: String): JsonObject? = getLootTable(id(lootTableId))

@ApiStatus.Experimental
fun getLootTable(lootTableId: Identifier): JsonObject? {
    val server = server ?: return null
    val provider = server.reloadableRegistries().lookup()
    val registryAccess = provider as? RegistryAccess ?: return null

    val lootRegistry = registryAccess.registries()
        .filter { it.key() == Registries.LOOT_TABLE }
        .findFirst()
        .orElse(null)
        ?.value() ?: return null

    @Suppress("UNCHECKED_CAST")
    val typedRegistry = lootRegistry as net.minecraft.core.Registry<LootTable>
    val key = ResourceKey.create(Registries.LOOT_TABLE, lootTableId)
    val table = typedRegistry.getOptional(key).orElse(null) ?: return null

    val ctx = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess())
    return LootTable.DIRECT_CODEC.encodeStart(ctx, table).result().orElse(null) as? JsonObject
}

/**
 * Mutates an existing loot table by reading its current JSON, applying the
 * configured changes, and re-registering it through [ServerDatapackManager].
 *
 * Requires a running server. Returns `false` and logs a warning when the
 * server is offline or the loot table cannot be resolved.
 *
 * @example
 * ```kotlin
 * modifyLootTable("minecraft:blocks/stone") {
 *     pool {
 *         rolls = 1
 *         addItem("minecraft:diamond", weight = 1)
 *     }
 *     removeItem("minecraft:cobblestone")
 * }
 * ```
 */
@ApiStatus.Experimental
fun modifyLootTable(lootTableId: String, configure: LootTableModificationConfig.() -> Unit): Boolean {
    return modifyLootTable(id(lootTableId), configure)
}

@ApiStatus.Experimental
fun modifyLootTable(lootTableId: Identifier, configure: LootTableModificationConfig.() -> Unit): Boolean {
    val json = getLootTable(lootTableId) ?: run {
        LOGGER.warn("modifyLootTable: loot table {} not found", lootTableId)
        return false
    }
    val config = LootTableModificationConfig(json).apply(configure)
    config.apply()
    ServerDatapackManager.registerLootTable(lootTableId, json)
    return true
}

/**
 * Mutation API over a loot table JSON document. The modifications are applied
 * directly to the underlying JSON tree; no schema validation beyond what
 * Minecraft's codec already does on re-registration.
 */
class LootTableModificationConfig internal constructor(private val json: JsonObject) {

    private val pendingPools = mutableListOf<JsonObject>()
    private val itemRemovals = mutableListOf<String>()
    private val poolRemovals = mutableListOf<Int>()

    /**
     * Adds a new pool built through the existing [LootPoolBuilderJson] DSL.
     */
    fun pool(block: LootPoolBuilderJson.() -> Unit) {
        pendingPools += LootPoolBuilderJson().apply(block).toJson()
    }

    /**
     * Adds a raw pool JSON object as-is. Useful when migrating existing
     * datapack payloads.
     */
    fun rawPool(poolJson: JsonObject) {
        pendingPools += poolJson
    }

    /**
     * Removes a pool by zero-based index. Indices are interpreted against the
     * pool list as it exists at apply time, after additions and other
     * removals have not yet been applied.
     */
    fun removePool(index: Int) {
        poolRemovals += index
    }

    /**
     * Removes every item entry referencing the given item id from every pool.
     * Tag entries (`type: minecraft:tag`) are not touched.
     */
    fun removeItem(itemId: String) {
        itemRemovals += itemId
    }

    internal fun apply() {
        val pools = ensurePoolsArray(json)
        if (poolRemovals.isNotEmpty()) {
            poolRemovals.sortedDescending().forEach { idx ->
                if (idx in 0 until pools.size()) pools.remove(idx)
            }
        }
        if (itemRemovals.isNotEmpty()) {
            removeItemsFromPools(pools, itemRemovals)
        }
        pendingPools.forEach { pools.add(it) }
    }

    private fun ensurePoolsArray(target: JsonObject): JsonArray {
        if (target.has("pools") && target.get("pools").isJsonArray) {
            return target.getAsJsonArray("pools")
        }
        val arr = JsonArray()
        target.add("pools", arr)
        return arr
    }

    private fun removeItemsFromPools(pools: JsonArray, items: List<String>) {
        for (i in 0 until pools.size()) {
            val poolEl = pools.get(i)
            if (!poolEl.isJsonObject) continue
            val pool = poolEl.asJsonObject
            if (!pool.has("entries") || !pool.get("entries").isJsonArray) continue
            val entries = pool.getAsJsonArray("entries")
            val keep = JsonArray()
            for (j in 0 until entries.size()) {
                val entry = entries.get(j)
                if (!entry.isJsonObject) {
                    keep.add(entry)
                    continue
                }
                val obj = entry.asJsonObject
                val type = obj.get("type")?.asString
                val name = obj.get("name")?.asString
                if (type == "minecraft:item" && name != null && name in items) {
                    continue
                }
                keep.add(entry)
            }
            pool.add("entries", keep)
        }
    }
}

/**
 * Lightweight pool builder that reuses the JSON shape produced by
 * [top.katton.api.datapack.LootPoolBuilder] without depending on it directly,
 * so the mod-API namespace stays self-contained.
 */
class LootPoolBuilderJson internal constructor() {
    /** Pool roll count. Defaults to 1. */
    var rolls: Int = 1
    private val entries = JsonArray()

    /** Adds a single item entry to this pool. */
    fun addItem(itemId: String, weight: Int = 1, quality: Int = 0) {
        val entry = JsonObject()
        entry.addProperty("type", "minecraft:item")
        entry.addProperty("name", itemId)
        entry.addProperty("weight", weight)
        if (quality != 0) entry.addProperty("quality", quality)
        entries.add(entry)
    }

    /** Adds a tag-based entry to this pool. */
    fun addTag(tagId: String, weight: Int = 1, expand: Boolean = false) {
        val entry = JsonObject()
        entry.addProperty("type", "minecraft:tag")
        entry.addProperty("name", tagId)
        entry.addProperty("weight", weight)
        entry.addProperty("expand", expand)
        entries.add(entry)
    }

    /** Adds an empty (drop-nothing) entry to this pool. */
    fun addEmpty(weight: Int = 1) {
        val entry = JsonObject()
        entry.addProperty("type", "minecraft:empty")
        entry.addProperty("weight", weight)
        entries.add(entry)
    }

    internal fun toJson(): JsonObject {
        val pool = JsonObject()
        val rollsObj = JsonObject()
        rollsObj.addProperty("type", "minecraft:constant")
        rollsObj.addProperty("value", rolls)
        pool.add("rolls", rollsObj)
        pool.add("entries", entries)
        return pool
    }
}
