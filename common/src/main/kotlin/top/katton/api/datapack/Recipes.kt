@file:Suppress("unused")

package top.katton.api.datapack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import top.katton.datapack.ServerDatapackManager

fun recipes(block: RecipeEvent.() -> Unit) {
    RecipeEvent().apply(block)
}

class RecipeEvent {
    fun remove(id: String) {
        ServerDatapackManager.removeRecipe(Identifier.parse(id))
    }

    fun shaped(id: String, result: String, count: Int = 1, block: ShapedRecipeBuilder.() -> Unit) {
        val builder = ShapedRecipeBuilder(Identifier.parse(id), RecipeResult(result, count)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }

    fun shapeless(id: String, result: String, count: Int = 1, block: ShapelessRecipeBuilder.() -> Unit) {
        val builder = ShapelessRecipeBuilder(Identifier.parse(id), RecipeResult(result, count)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }

    fun smelting(id: String, result: String, count: Int = 1, block: CookingRecipeBuilder.() -> Unit) {
        registerCooking(id, "minecraft:smelting", result, count, block)
    }

    fun blasting(id: String, result: String, count: Int = 1, block: CookingRecipeBuilder.() -> Unit) {
        registerCooking(id, "minecraft:blasting", result, count, block)
    }

    fun smoking(id: String, result: String, count: Int = 1, block: CookingRecipeBuilder.() -> Unit) {
        registerCooking(id, "minecraft:smoking", result, count, block)
    }

    fun campfireCooking(id: String, result: String, count: Int = 1, block: CookingRecipeBuilder.() -> Unit) {
        registerCooking(id, "minecraft:campfire_cooking", result, count, block)
    }

    fun stonecutting(id: String, result: String, count: Int = 1, block: StonecuttingRecipeBuilder.() -> Unit) {
        val builder = StonecuttingRecipeBuilder(Identifier.parse(id), RecipeResult(result, count)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }

    private fun registerCooking(
        id: String,
        type: String,
        result: String,
        count: Int,
        block: CookingRecipeBuilder.() -> Unit
    ) {
        val builder = CookingRecipeBuilder(Identifier.parse(id), type, RecipeResult(result, count)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }
}

open class RecipeBuilderBase(
    val id: Identifier,
    private val type: String,
    protected val result: RecipeResult
) {
    var group: String? = null

    protected fun baseJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("type", type)
        group?.takeIf { it.isNotBlank() }?.let { json.addProperty("group", it) }
        return json
    }

    protected fun recipeResultJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("id", result.item.toString())
        if (result.count != 1) {
            json.addProperty("count", result.count)
        }
        return json
    }
}

class ShapedRecipeBuilder(id: Identifier, result: RecipeResult) : RecipeBuilderBase(id, "minecraft:crafting_shaped", result) {
    private val pattern = mutableListOf<String>()
    private val key = linkedMapOf<Char, IngredientSpec>()

    fun pattern(vararg rows: String) {
        pattern.clear()
        pattern.addAll(rows)
    }

    fun define(symbol: Char, ingredient: String) {
        key[symbol] = IngredientSpec.parse(ingredient)
    }

    fun define(symbol: Char, ingredient: IngredientSpec) {
        key[symbol] = ingredient
    }

    fun toJson(): JsonObject {
        require(pattern.isNotEmpty()) { "Shaped recipe $id requires at least one pattern row" }
        require(key.isNotEmpty()) { "Shaped recipe $id requires key mappings" }

        val json = baseJson()
        val patternJson = JsonArray()
        pattern.forEach(patternJson::add)
        json.add("pattern", patternJson)

        val keyJson = JsonObject()
        key.forEach { (symbol, ingredient) ->
            keyJson.add(symbol.toString(), ingredient.toJson())
        }
        json.add("key", keyJson)
        json.add("result", recipeResultJson())
        return json
    }
}

class ShapelessRecipeBuilder(id: Identifier, result: RecipeResult) : RecipeBuilderBase(id, "minecraft:crafting_shapeless", result) {
    private val ingredients = mutableListOf<IngredientSpec>()

    fun input(vararg values: String) {
        values.forEach { ingredients += IngredientSpec.parse(it) }
    }

    fun input(vararg values: IngredientSpec) {
        ingredients.addAll(values)
    }

    fun toJson(): JsonObject {
        require(ingredients.isNotEmpty()) { "Shapeless recipe $id requires at least one ingredient" }

        val json = baseJson()
        val ingredientsJson = JsonArray()
        ingredients.forEach { ingredientsJson.add(it.toJson()) }
        json.add("ingredients", ingredientsJson)
        json.add("result", recipeResultJson())
        return json
    }
}

class CookingRecipeBuilder(id: Identifier, type: String, result: RecipeResult) : RecipeBuilderBase(id, type, result) {
    private var ingredient: IngredientSpec? = null
    var experience: Float = 0.0f
    var cookingTime: Int = 200

    fun input(value: String) {
        ingredient = IngredientSpec.parse(value)
    }

    fun input(value: IngredientSpec) {
        ingredient = value
    }

    fun toJson(): JsonObject {
        val builtIngredient = requireNotNull(ingredient) { "Cooking recipe $id requires an input ingredient" }
        val json = baseJson()
        json.add("ingredient", builtIngredient.toJson())
        json.add("result", recipeResultJson())
        json.addProperty("experience", experience)
        json.addProperty("cookingtime", cookingTime)
        return json
    }
}

class StonecuttingRecipeBuilder(id: Identifier, result: RecipeResult) : RecipeBuilderBase(id, "minecraft:stonecutting", result) {
    private var ingredient: IngredientSpec? = null

    fun input(value: String) {
        ingredient = IngredientSpec.parse(value)
    }

    fun input(value: IngredientSpec) {
        ingredient = value
    }

    fun toJson(): JsonObject {
        val builtIngredient = requireNotNull(ingredient) { "Stonecutting recipe $id requires an input ingredient" }
        val json = baseJson()
        json.add("ingredient", builtIngredient.toJson())
        json.add("result", recipeResultJson())
        return json
    }
}

data class RecipeResult(val item: Identifier, val count: Int) {
    constructor(item: String, count: Int) : this(Identifier.parse(item), count)
}

data class IngredientSpec(val id: Identifier, val isTag: Boolean) {
    fun toJson(): JsonObject {
        val json = JsonObject()
        if (isTag) {
            json.addProperty("tag", id.toString())
        } else {
            json.addProperty("item", id.toString())
        }
        return json
    }

    companion object {
        fun item(id: String) = IngredientSpec(Identifier.parse(id), false)
        fun tag(id: String) = IngredientSpec(Identifier.parse(id.removePrefix("#")), true)

        fun parse(id: String): IngredientSpec = if (id.startsWith("#")) tag(id) else item(id)
    }
}

fun itemRef(id: String): IngredientSpec = IngredientSpec.item(id)

fun tagRef(id: String): IngredientSpec = IngredientSpec.tag(id)