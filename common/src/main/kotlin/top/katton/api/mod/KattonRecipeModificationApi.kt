@file:Suppress("unused")

package top.katton.api.mod

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.crafting.Recipe
import org.jetbrains.annotations.ApiStatus
import top.katton.api.server
import top.katton.datapack.ServerDatapackManager
import top.katton.registry.id

/**
 * %en
 * Configuration for modifying existing recipe properties.
 *
 * This class provides a fluent API for modifying properties of existing
 * recipes registered in Minecraft's recipe manager. Similar to KubeJS's recipe
 * modification system.
 *
 * %zh
 * 用于修改现有配方属性的配置对象。
 * 这个类提供一个流式 API，用于修改已经注册到 Minecraft 配方管理器中的配方属性。
 * 风格上类似 KubeJS 的配方修改系统。
 * @property recipeId
 * %en The identifier of the recipe to modify
 * %zh 要修改的配方标识符。
 */
class RecipeModificationConfig(
    val recipeId: Identifier
) {
    var result: String? = null
    var resultCount: Int? = null
    var group: String? = null
    var experience: Float? = null
    var cookingTime: Int? = null
    var pattern: List<String>? = null
    val keys = mutableMapOf<String, String>()

    fun result(value: String) {
        result = value
    }

    fun resultCount(value: Int) {
        resultCount = value
    }

    fun group(value: String) {
        group = value
    }

    fun experience(value: Float) {
        experience = value
    }

    fun cookingTime(value: Int) {
        cookingTime = value
    }

    fun pattern(vararg rows: String) {
        pattern = rows.toList()
    }

    fun key(key: String, itemId: String) {
        keys[key] = itemId
    }
}

/**
 * %en
 * Modifies an existing recipe's properties.
 *
 * This function allows you to modify properties of recipes already registered
 * in Minecraft's recipe manager. Changes are applied by re-registering the
 * modified recipe through the datapack system.
 *
 * %zh
 * 修改已有配方的属性。
 * 这个函数允许你修改已经注册到 Minecraft 配方管理器中的配方属性。
 * 变更会通过数据包系统重新注册修改后的配方来应用。
 * @param recipeId
 * %en The identifier of the recipe to modify (e.g., "minecraft:iron_ingot_from_smelting")
 * %zh 要修改的配方标识符（例如 "minecraft:iron_ingot_from_smelting"）。
 * @param configure
 * %en Configuration lambda for recipe modifications
 * %zh 配方修改配置 lambda。
 * @example
 * %en
 * ```kotlin
 * modifyRecipe("minecraft:iron_ingot_from_smelting") {
 *     result = "minecraft:gold_ingot"
 *     experience = 2.0f
 *     cookingTime = 100
 * }
 * ```
 * %zh 示例代码见英文部分。
 */
@ApiStatus.Experimental
fun modifyRecipe(recipeId: String, configure: RecipeModificationConfig.() -> Unit) {
    modifyRecipe(id(recipeId), configure)
}

/**
 * %en
 * Modifies an existing recipe's properties.
 *
 * %zh
 * 修改已有配方的属性。
 * @param recipeId
 * %en The identifier of the recipe to modify
 * %zh 要修改的配方标识符。
 * @param configure
 * %en Configuration lambda for recipe modifications
 * %zh 配方修改配置 lambda。
 */
@ApiStatus.Experimental
fun modifyRecipe(recipeId: Identifier, configure: RecipeModificationConfig.() -> Unit) {
    val server = server ?: error("Server is not available. Recipe modifications require a running server.")
    val recipeManager = server.recipeManager
    val holder = recipeManager.recipes.find { it.id().identifier() == recipeId }
        ?: throw IllegalArgumentException("Recipe not found: $recipeId")

    val serializationContext = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess())
    val json = Recipe.CODEC.encodeStart(serializationContext, holder.value())
        .getOrThrow { msg -> IllegalStateException("Failed to serialize recipe $recipeId: $msg") }
        as? JsonObject ?: error("Failed to serialize recipe $recipeId to JsonObject")

    val config = RecipeModificationConfig(recipeId).apply(configure)
    applyRecipeModifications(json, config)
    ServerDatapackManager.registerRecipe(recipeId, json)
}

private fun applyRecipeModifications(json: JsonObject, config: RecipeModificationConfig) {
    config.group?.let { json.addProperty("group", it) }

    config.result?.let { resultId ->
        val result = json.get("result")
        if (result?.isJsonObject == true) {
            result.asJsonObject.addProperty("id", resultId)
        } else if (result?.isJsonPrimitive == true) {
            json.addProperty("result", resultId)
        }
    }

    config.resultCount?.let { count ->
        val result = json.get("result")
        if (result?.isJsonObject == true) {
            result.asJsonObject.addProperty("count", count)
        }
    }

    config.experience?.let { json.addProperty("experience", it) }
    config.cookingTime?.let { json.addProperty("cookingtime", it) }

    config.pattern?.let { rows ->
        if (json.has("pattern")) {
            val newArr = com.google.gson.JsonArray()
            rows.forEach { newArr.add(it) }
            json.add("pattern", newArr)
        }
    }

    if (json.has("key") && json.get("key").isJsonObject) {
        val keyObj = json.getAsJsonObject("key")
        config.keys.forEach { (k, itemId) ->
           val value = if (itemId.startsWith("#")) itemId else itemId
            keyObj.add(k, JsonPrimitive(value))
        }
    }
}

/**
 * %en
 * Gets a recipe by its identifier as a JsonObject.
 *
 * %zh
 * 根据标识符获取配方，并以 JsonObject 形式返回。
 * @param recipeId
 * %en The recipe identifier
 * %zh 配方标识符。
 * @return
 * %en recipe as JsonObject, or null if not found
 * %zh 找到时返回配方的 JsonObject，未找到时返回 null。
 */
fun getRecipe(recipeId: String): JsonObject? {
    return getRecipe(id(recipeId))
}

/**
 * %en
 * Gets a recipe by its identifier as a JsonObject.
 *
 * %zh
 * 根据标识符获取配方，并以 JsonObject 形式返回。
 * @param recipeId
 * %en The recipe identifier
 * %zh 配方标识符。
 * @return
 * %en recipe as JsonObject, or null if not found
 * %zh 找到时返回配方的 JsonObject，未找到时返回 null。
 */
fun getRecipe(recipeId: Identifier): JsonObject? {
    val server = server ?: return null
    val recipeManager = server.recipeManager
    val holder = recipeManager.recipes.find { it.id().identifier() == recipeId } ?: return null

    val serializationContext = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess())
    return Recipe.CODEC.encodeStart(serializationContext, holder.value())
        .result().orElse(null) as? JsonObject
}

/**
 * %en
 * Removes a recipe by its identifier.
 *
 * %zh
 * 根据标识符移除配方。
 * @param recipeId
 * %en The recipe identifier
 * %zh 配方标识符。
 */
fun removeRecipe(recipeId: String) {
    removeRecipe(id(recipeId))
}

/**
 * %en
 * Removes a recipe by its identifier.
 *
 * %zh
 * 根据标识符移除配方。
 * @param recipeId
 * %en The recipe identifier
 * %zh 配方标识符。
 */
fun removeRecipe(recipeId: Identifier) {
    ServerDatapackManager.removeRecipe(recipeId)
}
