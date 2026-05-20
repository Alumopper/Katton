@file:Suppress("unused")

package top.katton.api.datapack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import top.katton.datapack.ServerDatapackManager

fun lootTables(block: LootTableEvent.() -> Unit) {
    LootTableEvent().apply(block)
}

class LootTableEvent {
    fun remove(id: String) {
        ServerDatapackManager.removeLootTable(Identifier.parse(id))
    }

    fun add(id: String, block: LootTableBuilder.() -> Unit) {
        val builder = LootTableBuilder(Identifier.parse(id)).apply(block)
        ServerDatapackManager.registerLootTable(builder.id, builder.toJson())
    }
}

class LootTableBuilder(val id: Identifier) {
    var type: String = "minecraft:generic"
    var randomSequence: String? = null
    private val pools = mutableListOf<LootPoolBuilder>()

    fun pool(block: LootPoolBuilder.() -> Unit) {
        pools += LootPoolBuilder().apply(block)
    }

    fun toJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("type", type)
        randomSequence?.let { json.addProperty("random_sequence", it) }
        if (pools.isNotEmpty()) {
            val poolsJson = JsonArray()
            pools.forEach { poolsJson.add(it.toJson()) }
            json.add("pools", poolsJson)
        }
        return json
    }
}

class LootPoolBuilder {
    var rolls: JsonObject = constant(1)
    var bonusRolls: JsonObject? = null
    private val conditions = JsonArray()
    private val functions = JsonArray()
    private val entries = mutableListOf<LootEntryBuilder>()

    fun rolls(value: JsonObject) {
        rolls = value
    }

    fun bonusRolls(value: JsonObject) {
        bonusRolls = value
    }

    fun condition(json: JsonObject) {
        conditions.add(json)
    }

    fun function(json: JsonObject) {
        functions.add(json)
    }

    fun entry(block: LootEntryBuilder.() -> Unit) {
        entries += LootEntryBuilder().apply(block)
    }

    fun toJson(): JsonObject {
        val json = JsonObject()
        json.add("rolls", rolls)
        bonusRolls?.let { json.add("bonus_rolls", it) }
        if (conditions.size() > 0) json.add("conditions", conditions)
        if (functions.size() > 0) json.add("functions", functions)
        val entriesJson = JsonArray()
        entries.forEach { entriesJson.add(it.toJson()) }
        json.add("entries", entriesJson)
        return json
    }
}

class LootEntryBuilder {
    private var type: String = "minecraft:item"
    private var name: String? = null
    private var value: String? = null
    private var weight: Int = 1
    private var quality: Int = 0
    private var expand: Boolean = false
    private val children = mutableListOf<LootEntryBuilder>()

    fun item(name: String, weight: Int = 1) {
        this.type = "minecraft:item"
        this.name = name
        this.weight = weight
    }

    fun tag(name: String, weight: Int = 1) {
        this.type = "minecraft:tag"
        this.name = name
        this.weight = weight
        this.expand = false
    }

    fun lootTable(ref: String, weight: Int = 1) {
        this.type = "minecraft:loot_table"
        this.value = ref
        this.weight = weight
    }

    fun dynamic(name: String, weight: Int = 1) {
        this.type = "minecraft:dynamic"
        this.name = name
        this.weight = weight
    }

    fun empty(weight: Int = 1) {
        this.type = "minecraft:empty"
        this.weight = weight
    }

    fun alternatives(block: LootEntryListBuilder.() -> Unit) {
        this.type = "minecraft:alternatives"
        this.children.clear()
        this.children.addAll(LootEntryListBuilder().apply(block).entries)
    }

    fun group(block: LootEntryListBuilder.() -> Unit) {
        this.type = "minecraft:group"
        this.children.clear()
        this.children.addAll(LootEntryListBuilder().apply(block).entries)
    }

    fun sequence(block: LootEntryListBuilder.() -> Unit) {
        this.type = "minecraft:sequence"
        this.children.clear()
        this.children.addAll(LootEntryListBuilder().apply(block).entries)
    }

    fun toJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("type", type)
        json.addProperty("weight", weight)
        if (quality != 0) json.addProperty("quality", quality)
        name?.let {
            when (type) {
                "minecraft:loot_table" -> {}
                else -> json.addProperty("name", it)
            }
        }
        value?.let {
            if (type == "minecraft:loot_table") {
                json.addProperty("value", it)
            }
        }
        if (type == "minecraft:tag") {
            json.addProperty("expand", expand)
        }
        if (children.isNotEmpty()) {
            val childrenJson = JsonArray()
            children.forEach { child -> childrenJson.add(child.toJson()) }
            json.add("children", childrenJson)
        }
        return json
    }
}

class LootEntryListBuilder {
    internal val entries = mutableListOf<LootEntryBuilder>()

    fun entry(block: LootEntryBuilder.() -> Unit) {
        entries += LootEntryBuilder().apply(block)
    }
}

fun constant(value: Int): JsonObject {
    val json = JsonObject()
    json.addProperty("type", "minecraft:constant")
    json.addProperty("value", value)
    return json
}

fun uniform(min: Int, max: Int): JsonObject {
    val json = JsonObject()
    json.addProperty("type", "minecraft:uniform")
    json.addProperty("min", min)
    json.addProperty("max", max)
    return json
}

fun binomial(n: Int, p: Double): JsonObject {
    val json = JsonObject()
    json.addProperty("type", "minecraft:binomial")
    json.addProperty("n", n)
    json.addProperty("p", p)
    return json
}
