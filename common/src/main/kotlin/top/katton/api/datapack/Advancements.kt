@file:Suppress("unused")

package top.katton.api.datapack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import top.katton.datapack.ServerDatapackManager

fun advancements(block: AdvancementEvent.() -> Unit) {
    AdvancementEvent().apply(block)
}

class AdvancementEvent {
    fun remove(id: String) {
        ServerDatapackManager.removeAdvancement(Identifier.parse(id))
    }

    fun advancement(id: String, block: AdvancementBuilder.() -> Unit) {
        val builder = AdvancementBuilder(Identifier.parse(id)).apply(block)
        ServerDatapackManager.registerAdvancement(builder.id, builder.toJson())
    }
}

class AdvancementBuilder(val id: Identifier) {
    private var parent: Identifier? = null
    private val criteria = linkedMapOf<String, CriterionSpec>()
    private val rewards = AdvancementRewardsBuilder()
    private val display = AdvancementDisplayBuilder()
    private var requirementsStrategy: RequirementsStrategy = RequirementsStrategy.ALL

    fun parent(id: String) {
        parent = Identifier.parse(id)
    }

    fun display(block: AdvancementDisplayBuilder.() -> Unit) {
        display.apply(block)
    }

    fun rewards(block: AdvancementRewardsBuilder.() -> Unit) {
        rewards.apply(block)
    }

    fun impossible(name: String) {
        criteria[name] = CriterionSpec.impossible()
    }

    fun tick(name: String) {
        criteria[name] = CriterionSpec.tick()
    }

    fun recipeUnlocked(name: String, recipeId: String) {
        criteria[name] = CriterionSpec.recipeUnlocked(recipeId)
    }

    fun inventoryChanged(name: String, vararg items: String) {
        criteria[name] = CriterionSpec.inventoryChanged(items.toList())
    }

    fun requireAll() {
        requirementsStrategy = RequirementsStrategy.ALL
    }

    fun requireAny() {
        requirementsStrategy = RequirementsStrategy.ANY
    }

    fun toJson(): JsonObject {
        require(criteria.isNotEmpty()) { "Advancement $id requires at least one criterion" }
        val json = JsonObject()
        parent?.let { json.addProperty("parent", it.toString()) }
        display.takeIf { it.isConfigured() }?.let { json.add("display", it.toJson()) }
        rewards.takeIf { it.isConfigured() }?.let { json.add("rewards", it.toJson()) }

        val criteriaJson = JsonObject()
        criteria.forEach { (name, criterion) -> criteriaJson.add(name, criterion.toJson()) }
        json.add("criteria", criteriaJson)

        val requirementsJson = JsonArray()
        when (requirementsStrategy) {
            RequirementsStrategy.ALL -> criteria.keys.forEach { name ->
                val row = JsonArray()
                row.add(name)
                requirementsJson.add(row)
            }

            RequirementsStrategy.ANY -> {
                val row = JsonArray()
                criteria.keys.forEach(row::add)
                requirementsJson.add(row)
            }
        }
        json.add("requirements", requirementsJson)
        return json
    }
}

class AdvancementDisplayBuilder {
    var title: String = ""
    var description: String = ""
    var icon: String = "minecraft:stone"
    var frame: String = "task"
    var background: String? = null
    var showToast: Boolean = true
    var announceToChat: Boolean = true
    var hidden: Boolean = false

    fun isConfigured(): Boolean = title.isNotBlank() || description.isNotBlank()

    fun toJson(): JsonObject {
        val json = JsonObject()
        val iconJson = JsonObject()
        iconJson.addProperty("id", Identifier.parse(icon).toString())
        json.add("icon", iconJson)

        val titleJson = JsonObject()
        titleJson.addProperty("text", title)
        json.add("title", titleJson)

        val descriptionJson = JsonObject()
        descriptionJson.addProperty("text", description)
        json.add("description", descriptionJson)

        json.addProperty("frame", frame)
        json.addProperty("show_toast", showToast)
        json.addProperty("announce_to_chat", announceToChat)
        json.addProperty("hidden", hidden)
        background?.let { json.addProperty("background", it) }
        return json
    }
}

class AdvancementRewardsBuilder {
    var experience: Int = 0
    private val recipes = mutableListOf<String>()

    fun recipe(id: String) {
        recipes += Identifier.parse(id).toString()
    }

    fun isConfigured(): Boolean = experience != 0 || recipes.isNotEmpty()

    fun toJson(): JsonObject {
        val json = JsonObject()
        if (experience != 0) {
            json.addProperty("experience", experience)
        }
        if (recipes.isNotEmpty()) {
            val array = JsonArray()
            recipes.forEach(array::add)
            json.add("recipes", array)
        }
        return json
    }
}

class CriterionSpec(private val trigger: String, private val conditions: JsonObject? = null) {
    fun toJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("trigger", trigger)
        conditions?.let { json.add("conditions", it) }
        return json
    }

    companion object {
        fun impossible() = CriterionSpec("minecraft:impossible")

        fun tick() = CriterionSpec("minecraft:tick")

        fun recipeUnlocked(recipeId: String): CriterionSpec {
            val conditions = JsonObject()
            conditions.addProperty("recipe", Identifier.parse(recipeId).toString())
            return CriterionSpec("minecraft:recipe_unlocked", conditions)
        }

        fun inventoryChanged(items: List<String>): CriterionSpec {
            val conditions = JsonObject()
            val itemArray = JsonArray()
            items.forEach { itemId ->
                val predicate = JsonObject()
                val itemIds = JsonArray()
                itemIds.add(Identifier.parse(itemId).toString())
                predicate.add("items", itemIds)
                itemArray.add(predicate)
            }
            conditions.add("items", itemArray)
            return CriterionSpec("minecraft:inventory_changed", conditions)
        }
    }
}

enum class RequirementsStrategy {
    ALL,
    ANY
}