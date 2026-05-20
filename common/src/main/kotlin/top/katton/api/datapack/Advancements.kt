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
    fun remove(id: Identifier) {
        ServerDatapackManager.removeAdvancement(id)
    }

    fun remove(id: String) {
        remove(Identifier.parse(id))
    }

    fun add(id: Identifier, block: AdvancementBuilder.() -> Unit) {
        val builder = AdvancementBuilder(id).apply(block)
        ServerDatapackManager.registerAdvancement(builder.id, builder.toJson())
    }

    fun add(id: String, block: AdvancementBuilder.() -> Unit) {
        add(Identifier.parse(id), block)
    }
}

class AdvancementBuilder(val id: Identifier) {
    var parent: String? = null
    private var displayConfigured = false
    private val display = DisplayBuilder()
    private val criteria = linkedMapOf<String, CriterionBuilder>()
    private val requirements = mutableListOf<List<String>>()
    private val rewards = RewardBuilder()

    fun display(block: DisplayBuilder.() -> Unit) {
        displayConfigured = true
        display.apply(block)
    }

    fun criterion(name: String, block: CriterionBuilder.() -> Unit) {
        criteria[name] = CriterionBuilder().apply(block)
    }

    fun requirements(vararg criterionNames: String) {
        requirements.add(criterionNames.toList())
    }

    fun rewards(block: RewardBuilder.() -> Unit) {
        rewards.apply(block)
    }

    fun toJson(): JsonObject {
        require(criteria.isNotEmpty()) { "Advancement $id requires at least one criterion" }

        val json = JsonObject()

        parent?.takeIf { it.isNotBlank() }?.let { json.addProperty("parent", it) }

        if (displayConfigured) {
            json.add("display", display.toJson())
        }

        val criteriaJson = JsonObject()
        criteria.forEach { (name, builder) ->
            criteriaJson.add(name, builder.toJson())
        }
        json.add("criteria", criteriaJson)

        if (requirements.isNotEmpty()) {
            val requirementsJson = JsonArray()
            requirements.forEach { group ->
                val groupJson = JsonArray()
                group.forEach(groupJson::add)
                requirementsJson.add(groupJson)
            }
            json.add("requirements", requirementsJson)
        }

        if (rewards.hasContent()) {
            json.add("rewards", rewards.toJson())
        }

        return json
    }
}

class DisplayBuilder {
    private var iconItem: String? = null
    private var iconCount: Int = 1
    private var title: JsonObject? = null
    private var description: JsonObject? = null
    private var frame: FrameType = FrameType.TASK
    private var background: String? = null
    private var showToast: Boolean = true
    private var announceToChat: Boolean = true
    private var hidden: Boolean = false

    fun icon(item: Identifier, count: Int = 1) {
        iconItem = item.toString()
        iconCount = count
    }

    fun icon(item: String, count: Int = 1) {
        icon(Identifier.parse(item), count)
    }

    fun title(text: String) {
        title = JsonObject().apply { addProperty("text", text) }
    }

    fun titleJson(rawJson: JsonObject) {
        title = rawJson
    }

    fun description(text: String) {
        description = JsonObject().apply { addProperty("text", text) }
    }

    fun descriptionJson(rawJson: JsonObject) {
        description = rawJson
    }

    fun frame(frameType: FrameType) {
        frame = frameType
    }

    fun background(texture: Identifier) {
        background = texture.toString()
    }

    fun background(texture: String) {
        background(Identifier.parse(texture))
    }

    fun showToast(bool: Boolean) {
        showToast = bool
    }

    fun announceToChat(bool: Boolean) {
        announceToChat = bool
    }

    fun hidden(bool: Boolean) {
        hidden = bool
    }

    fun toJson(): JsonObject {
        requireNotNull(title) { "Advancement display requires a title" }
        requireNotNull(description) { "Advancement display requires a description" }
        requireNotNull(iconItem) { "Advancement display requires an icon" }

        val json = JsonObject()
        val iconJson = JsonObject()
        iconJson.addProperty("id", iconItem)
        if (iconCount != 1) {
            iconJson.addProperty("count", iconCount)
        }
        json.add("icon", iconJson)
        json.add("title", title)
        json.add("description", description)
        json.addProperty("frame", frame.name.lowercase())
        background?.let { json.addProperty("background", it) }
        if (!showToast) json.addProperty("show_toast", false)
        if (!announceToChat) json.addProperty("announce_to_chat", false)
        if (hidden) json.addProperty("hidden", true)
        return json
    }
}

enum class FrameType {
    TASK,
    GOAL,
    CHALLENGE
}

class CriterionBuilder {
    private var trigger: String? = null
    private var conditions: JsonObject? = null

    fun trigger(triggerType: Identifier) {
        trigger = triggerType.toString()
    }

    fun trigger(triggerType: String) {
        trigger(Identifier.parse(triggerType))
    }

    fun conditions(json: JsonObject) {
        conditions = json
    }

    fun toJson(): JsonObject {
        val builtTrigger = requireNotNull(trigger) { "Criterion requires a trigger" }
        val json = JsonObject()
        json.addProperty("trigger", builtTrigger)
        conditions?.let { json.add("conditions", it) }
        return json
    }
}

class RewardBuilder {
    private var experience: Int? = null
    private var function: String? = null
    private val loot = mutableListOf<String>()
    private val recipes = mutableListOf<String>()

    fun experience(n: Int) {
        experience = n
    }

    fun function(id: Identifier) {
        function = id.toString()
    }

    fun function(id: String) {
        function(Identifier.parse(id))
    }

    fun loot(id: Identifier) {
        loot.add(id.toString())
    }

    fun loot(id: String) {
        loot(Identifier.parse(id))
    }

    fun recipe(id: Identifier) {
        recipes.add(id.toString())
    }

    fun recipe(id: String) {
        recipe(Identifier.parse(id))
    }

    fun hasContent(): Boolean = experience != null || function != null || loot.isNotEmpty() || recipes.isNotEmpty()

    fun toJson(): JsonObject {
        val json = JsonObject()
        experience?.let { json.addProperty("experience", it) }
        function?.let { json.addProperty("function", it) }
        if (loot.isNotEmpty()) {
            val lootJson = JsonArray()
            loot.forEach(lootJson::add)
            json.add("loot", lootJson)
        }
        if (recipes.isNotEmpty()) {
            val recipesJson = JsonArray()
            recipes.forEach(recipesJson::add)
            json.add("recipes", recipesJson)
        }
        return json
    }
}
