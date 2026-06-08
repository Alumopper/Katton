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
 * %en
 * Returns the JSON form of an existing loot table, or `null` when the table is
 * not registered or the server is offline.
 *
 * Reads from `server.reloadableRegistries()`, which is where loot tables live
 * in MC 1.21.5+, then re-encodes through [LootTable.DIRECT_CODEC].
 *
 * %zh
 * 返回现有战利品表的 JSON 形式；如果战利品表未注册或服务器离线，则返回 `null`。
 * 这里会从 `server.reloadableRegistries()` 读取，因为从 MC 1.21.5+ 开始，战利品表就存放在那里，
 * 然后再通过 [LootTable.DIRECT_CODEC] 重新编码。
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
 * %en
 * Mutates an existing loot table by reading its current JSON, applying the
 * configured changes, and re-registering it through [ServerDatapackManager].
 *
 * Requires a running server. Returns `false` and logs a warning when the
 * server is offline or the loot table cannot be resolved.
 *
 * %zh
 * 通过读取当前 JSON、应用配置修改，再经由 [ServerDatapackManager] 重新注册的方式来修改现有战利品表。
 * 需要服务器运行；如果服务器离线或战利品表无法解析，则返回 `false` 并记录警告。
 * @example
 * %en
 * ```kotlin
 * modifyLootTable("minecraft:blocks/stone") {
 *     pool {
 *         rolls = 1
 *         addItem("minecraft:diamond", weight = 1)
 *     }
 *     removeItem("minecraft:cobblestone")
 * }
 * ```
 * %zh 示例代码见英文部分。
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
 * %en
 * Mutation API over a loot table JSON document. The modifications are applied
 * directly to the underlying JSON tree; no schema validation beyond what
 * Minecraft's codec already does on re-registration.
 *
 * %zh
 * 作用于战利品表 JSON 文档的修改 API。
 * 修改会直接应用到底层 JSON 树；除了 Minecraft 在重新注册时通过 codec 做的校验外，
 * 不再额外做 schema 校验。
 */
class LootTableModificationConfig internal constructor(private val json: JsonObject) {

    private val pendingPools = mutableListOf<JsonObject>()
    private val itemRemovals = mutableListOf<String>()
    private val poolRemovals = mutableListOf<Int>()

    /**
 * %en
 * Adds a new pool built through the existing [LootPoolBuilderJson] DSL.
 *
 * %zh
 * 添加一个通过现有 [LootPoolBuilderJson] DSL 构建的新池。
 */
    fun pool(block: LootPoolBuilderJson.() -> Unit) {
        pendingPools += LootPoolBuilderJson().apply(block).toJson()
    }

    /**
 * %en
 * Adds a raw pool JSON object as-is. Useful when migrating existing
 * datapack payloads.
 *
 * %zh
 * 直接按原样添加一个原始池 JSON 对象。适合迁移现有数据包内容时使用。
 */
    fun rawPool(poolJson: JsonObject) {
        pendingPools += poolJson
    }

    /**
 * %en
 * Removes a pool by zero-based index. Indices are interpreted against the
 * pool list as it exists at apply time, after additions and other
 * removals have not yet been applied.
 *
 * %zh
 * 按从零开始的索引移除一个池。索引会以应用时的池列表为准，
 * 即在新增内容和其他移除操作尚未生效之前的状态。
 */
    fun removePool(index: Int) {
        poolRemovals += index
    }

    /**
 * %en
 * Removes every item entry referencing the given item id from every pool.
 * Tag entries (`type: minecraft:tag`) are not touched.
 *
 * %zh
 * 从所有池中移除引用指定物品 ID 的所有物品条目。
 * `type: minecraft:tag` 的标签条目不会受影响。
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
 * %en
 * Lightweight pool builder that reuses the JSON shape produced by
 * [top.katton.api.datapack.LootPoolBuilder] without depending on it directly,
 * so the mod-API namespace stays self-contained.
 *
 * %zh
 * 轻量级池构建器，复用 [top.katton.api.datapack.LootPoolBuilder] 生成的 JSON 结构，
 * 但不直接依赖它，从而让 mod-API 命名空间保持自包含。
 */
class LootPoolBuilderJson internal constructor() {
    /**
 * %en
 *  Pool roll count. Defaults to 1.
 *
 * %zh
 * 池的抽取次数。默认值为 1。
 */
    var rolls: Int = 1
    private val entries = JsonArray()

    /**
 * %en
 *  Adds a single item entry to this pool.
 *
 * %zh
 * 向当前池添加一个物品条目。
 */
    fun addItem(itemId: String, weight: Int = 1, quality: Int = 0) {
        val entry = JsonObject()
        entry.addProperty("type", "minecraft:item")
        entry.addProperty("name", itemId)
        entry.addProperty("weight", weight)
        if (quality != 0) entry.addProperty("quality", quality)
        entries.add(entry)
    }

    /**
 * %en
 *  Adds a tag-based entry to this pool.
 *
 * %zh
 * 向当前池添加一个基于标签的条目。
 */
    fun addTag(tagId: String, weight: Int = 1, expand: Boolean = false) {
        val entry = JsonObject()
        entry.addProperty("type", "minecraft:tag")
        entry.addProperty("name", tagId)
        entry.addProperty("weight", weight)
        entry.addProperty("expand", expand)
        entries.add(entry)
    }

    /**
 * %en
 *  Adds an empty (drop-nothing) entry to this pool.
 *
 * %zh
 * 向当前池添加一个空条目（不掉落任何东西）。
 */
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
