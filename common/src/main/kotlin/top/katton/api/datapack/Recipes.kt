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

    fun smithingTransform(id: String, result: String, count: Int = 1, block: SmithingTransformBuilder.() -> Unit) {
        val builder = SmithingTransformBuilder(Identifier.parse(id), RecipeResult(result, count)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }

    fun smithingTrim(id: String, block: SmithingTrimBuilder.() -> Unit) {
        val builder = SmithingTrimBuilder(Identifier.parse(id)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }

    fun transmute(id: String, result: String, count: Int = 1, block: TransmuteRecipeBuilder.() -> Unit) {
        val builder = TransmuteRecipeBuilder(Identifier.parse(id), RecipeResult(result, count)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }

    fun dye(id: String, result: String, count: Int = 1, block: DyeRecipeBuilder.() -> Unit) {
        val builder = DyeRecipeBuilder(Identifier.parse(id), RecipeResult(result, count)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }

    fun imbue(id: String, result: String, count: Int = 1, block: ImbueRecipeBuilder.() -> Unit) {
        val builder = ImbueRecipeBuilder(Identifier.parse(id), RecipeResult(result, count)).apply(block)
        ServerDatapackManager.registerRecipe(builder.id, builder.toJson())
    }
}

open class RecipeBuilderBase(
    val id: Identifier,
    private val type: String,
    protected val result: RecipeResult
) {
    var group: String? = null
    var category: String = "misc"

    protected fun baseJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("type", type)
        group?.takeIf { it.isNotBlank() }?.let { json.addProperty("group", it) }
        json.addProperty("category", category)
        return json
    }

    protected fun recipeResultJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("id", result.item.toString())
        if (result.count != 1) {
            json.addProperty("count", result.count)
        }
        result.components?.let { json.add("components", it) }
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

class SmithingTransformBuilder(id: Identifier, result: RecipeResult) : RecipeBuilderBase(id, "minecraft:smithing_transform", result) {
    private var template: IngredientSpec? = null
    private var base: IngredientSpec? = null
    private var addition: IngredientSpec? = null

    fun template(value: String) { template = IngredientSpec.parse(value) }
    fun template(value: IngredientSpec) { template = value }

    fun base(value: String) { base = IngredientSpec.parse(value) }
    fun base(value: IngredientSpec) { base = value }

    fun addition(value: String) { addition = IngredientSpec.parse(value) }
    fun addition(value: IngredientSpec) { addition = value }

    fun toJson(): JsonObject {
        val builtTemplate = requireNotNull(template) { "Smithing transform recipe $id requires a template" }
        val builtBase = requireNotNull(base) { "Smithing transform recipe $id requires a base" }
        val builtAddition = requireNotNull(addition) { "Smithing transform recipe $id requires an addition" }
        val json = baseJson()
        json.add("template", builtTemplate.toJson())
        json.add("base", builtBase.toJson())
        json.add("addition", builtAddition.toJson())
        json.add("result", recipeResultJson())
        return json
    }
}

class SmithingTrimBuilder(val id: Identifier) {
    var group: String? = null
    var category: String = "misc"
    private var template: IngredientSpec? = null
    private var base: IngredientSpec? = null
    private var addition: IngredientSpec? = null

    fun template(value: String) { template = IngredientSpec.parse(value) }
    fun template(value: IngredientSpec) { template = value }

    fun base(value: String) { base = IngredientSpec.parse(value) }
    fun base(value: IngredientSpec) { base = value }

    fun addition(value: String) { addition = IngredientSpec.parse(value) }
    fun addition(value: IngredientSpec) { addition = value }

    private fun baseJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("type", "minecraft:smithing_trim")
        group?.takeIf { it.isNotBlank() }?.let { json.addProperty("group", it) }
        json.addProperty("category", category)
        return json
    }

    fun toJson(): JsonObject {
        val builtTemplate = requireNotNull(template) { "Smithing trim recipe $id requires a template" }
        val builtBase = requireNotNull(base) { "Smithing trim recipe $id requires a base" }
        val builtAddition = requireNotNull(addition) { "Smithing trim recipe $id requires an addition" }
        val json = baseJson()
        json.add("template", builtTemplate.toJson())
        json.add("base", builtBase.toJson())
        json.add("addition", builtAddition.toJson())
        return json
    }
}

class TransmuteRecipeBuilder(id: Identifier, result: RecipeResult) : RecipeBuilderBase(id, "minecraft:crafting_transmute", result) {
    private var input: IngredientSpec? = null
    private var material: IngredientSpec? = null
    private var materialCountMin: Int = 1
    private var materialCountMax: Int = 4
    private var addMaterialToResultCount: Boolean = false

    fun input(value: String) { input = IngredientSpec.parse(value) }
    fun input(value: IngredientSpec) { input = value }

    fun material(value: String) { material = IngredientSpec.parse(value) }
    fun material(value: IngredientSpec) { material = value }

    fun materialCount(min: Int, max: Int) {
        materialCountMin = min
        materialCountMax = max
    }

    fun addMaterialToResultCount(value: Boolean) { addMaterialToResultCount = value }

    fun toJson(): JsonObject {
        val builtInput = requireNotNull(input) { "Transmute recipe $id requires an input" }
        val builtMaterial = requireNotNull(material) { "Transmute recipe $id requires a material" }
        val json = baseJson()
        json.add("input", builtInput.toJson())
        json.add("material", builtMaterial.toJson())
        val materialCountJson = JsonObject()
        materialCountJson.addProperty("min", materialCountMin)
        materialCountJson.addProperty("max", materialCountMax)
        json.add("material_count", materialCountJson)
        json.addProperty("add_material_to_result_count", addMaterialToResultCount)
        json.add("result", recipeResultJson())
        return json
    }
}

class DyeRecipeBuilder(id: Identifier, result: RecipeResult) : RecipeBuilderBase(id, "minecraft:crafting_dye", result) {
    private var target: IngredientSpec? = null
    private var dye: IngredientSpec? = null

    fun target(value: String) { target = IngredientSpec.parse(value) }
    fun target(value: IngredientSpec) { target = value }

    fun dye(value: String) { dye = IngredientSpec.parse(value) }
    fun dye(value: IngredientSpec) { dye = value }

    fun toJson(): JsonObject {
        val builtTarget = requireNotNull(target) { "Dye recipe $id requires a target" }
        val builtDye = requireNotNull(dye) { "Dye recipe $id requires a dye" }
        val json = baseJson()
        json.add("target", builtTarget.toJson())
        json.add("dye", builtDye.toJson())
        json.add("result", recipeResultJson())
        return json
    }
}

class ImbueRecipeBuilder(id: Identifier, result: RecipeResult) : RecipeBuilderBase(id, "minecraft:crafting_imbue", result) {
    private var input: IngredientSpec? = null
    private var ingredient: IngredientSpec? = null

    fun input(value: String) { input = IngredientSpec.parse(value) }
    fun input(value: IngredientSpec) { input = value }

    fun ingredient(value: String) { ingredient = IngredientSpec.parse(value) }
    fun ingredient(value: IngredientSpec) { ingredient = value }

    fun toJson(): JsonObject {
        val builtInput = requireNotNull(input) { "Imbue recipe $id requires an input" }
        val builtIngredient = requireNotNull(ingredient) { "Imbue recipe $id requires an ingredient" }
        val json = baseJson()
        json.add("input", builtInput.toJson())
        json.add("ingredient", builtIngredient.toJson())
        json.add("result", recipeResultJson())
        return json
    }
}

data class RecipeResult(val item: Identifier, val count: Int, val components: JsonObject? = null) {
    constructor(item: String, count: Int, components: JsonObject? = null) : this(Identifier.parse(item), count, components)
}

data class IngredientSpec(val id: Identifier, val isTag: Boolean) {
    /**
     * Returns the JSON representation of this ingredient.
     *
     * Minecraft 1.21.5+ uses `HolderSet<Item>` for ingredients, which serializes as:
     * - Plain item: `"minecraft:diamond"` (string primitive)
     * - Tag reference: `"#minecraft:planks"` (string primitive with `#` prefix)
     *
     * The OLD format `{"item": "..."}` and `{"tag": "..."}` is no longer accepted.
     */
    fun toJson(): com.google.gson.JsonElement {
        val value = if (isTag) "#${id}" else id.toString()
        return com.google.gson.JsonPrimitive(value)
    }

    companion object {
        fun item(id: String) = IngredientSpec(Identifier.parse(id), false)
        fun tag(id: String) = IngredientSpec(Identifier.parse(id.removePrefix("#")), true)

        fun parse(id: String): IngredientSpec = if (id.startsWith("#")) tag(id) else item(id)
    }
}

fun itemRef(id: String): IngredientSpec = IngredientSpec.item(id)

fun tagRef(id: String): IngredientSpec = IngredientSpec.tag(id)