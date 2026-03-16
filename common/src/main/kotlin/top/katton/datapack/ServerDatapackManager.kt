package top.katton.datapack

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.mojang.serialization.JsonOps
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementTree
import net.minecraft.advancements.TreeNodePosition
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.tags.TagKey
import net.minecraft.tags.TagLoader
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeMap
import top.katton.api.LOGGER
import top.katton.util.ReflectUtil
import net.minecraft.resources.Identifier

object ServerDatapackManager {

    internal val recipes = linkedMapOf<Identifier, JsonObject>()
    internal val removedRecipes = linkedSetOf<Identifier>()

    internal val advancements = linkedMapOf<Identifier, JsonObject>()
    internal val removedAdvancements = linkedSetOf<Identifier>()

    internal val tagMutations = linkedMapOf<ResourceKey<out Registry<*>>, LinkedHashMap<Identifier, TagMutation>>()

    fun beginReload() {
        recipes.clear()
        removedRecipes.clear()
        advancements.clear()
        removedAdvancements.clear()
        tagMutations.clear()
    }

    fun registerRecipe(id: Identifier, recipe: JsonObject) {
        recipes[id] = recipe
        removedRecipes.remove(id)
    }

    fun removeRecipe(id: Identifier) {
        recipes.remove(id)
        removedRecipes.add(id)
    }

    fun registerAdvancement(id: Identifier, advancement: JsonObject) {
        advancements[id] = advancement
        removedAdvancements.remove(id)
    }

    fun removeAdvancement(id: Identifier) {
        advancements.remove(id)
        removedAdvancements.add(id)
    }

    fun mutateTag(registryKey: ResourceKey<out Registry<*>>, tagId: Identifier, block: TagMutation.() -> Unit) {
        val mutation = tagMutations
            .computeIfAbsent(registryKey) { linkedMapOf() }
            .computeIfAbsent(tagId) { TagMutation() }
        mutation.block()
    }

    fun apply(server: MinecraftServer): Boolean {
        var changed = false
        changed = applyRecipes(server) || changed
        changed = applyAdvancements(server) || changed
        changed = applyTags(server) || changed

        if (changed) {
            server.playerList.reloadResources()
        }
        return changed
    }

    private fun applyRecipes(server: MinecraftServer): Boolean {
        if (recipes.isEmpty() && removedRecipes.isEmpty()) {
            return false
        }

        val recipeManager = server.recipeManager
        val serializationContext = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess())
        val merged = linkedMapOf<ResourceKey<Recipe<*>>, RecipeHolder<*>>()
        val overridden = recipes.keys

        recipeManager.recipes.forEach { holder ->
            val id = holder.id().identifier()
            if (id !in removedRecipes && id !in overridden) {
                merged[holder.id()] = holder
            }
        }

        recipes.forEach { (id, json) ->
            val key = ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id)
            val recipe = Recipe.CODEC.parse(serializationContext, json).getOrThrow(::JsonParseException)
            merged[key] = RecipeHolder(key, recipe)
        }

        ReflectUtil.set(recipeManager, "recipes", RecipeMap.create(merged.values))
        recipeManager.finalizeRecipeLoading(server.worldData.enabledFeatures())
        LOGGER.info("Applied {} scripted recipes and removed {} recipes", recipes.size, removedRecipes.size)
        return true
    }

    private fun applyAdvancements(server: MinecraftServer): Boolean {
        if (advancements.isEmpty() && removedAdvancements.isEmpty()) {
            return false
        }

        val advancementManager = server.advancements
        val serializationContext = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess())
        val merged = linkedMapOf<Identifier, Advancement>()
        val overridden = advancements.keys

        advancementManager.allAdvancements.forEach { holder ->
            val id = holder.id()
            if (id !in removedAdvancements && id !in overridden) {
                merged[id] = holder.value()
            }
        }

        advancements.forEach { (id, json) ->
            merged[id] = Advancement.CODEC.parse(serializationContext, json).getOrThrow(::JsonParseException)
        }

        val holders = linkedMapOf<Identifier, AdvancementHolder>()
        merged.forEach { (id, advancement) ->
            holders[id] = AdvancementHolder(id, advancement)
        }

        val tree = AdvancementTree()
        tree.addAll(holders.values)
        tree.roots().forEach { node ->
            if (node.holder().value().display().isPresent) {
                TreeNodePosition.run(node)
            }
        }

        ReflectUtil.set(advancementManager, "advancements", java.util.Map.copyOf(holders))
        ReflectUtil.set(advancementManager, "tree", tree)
        LOGGER.info("Applied {} scripted advancements and removed {} advancements", advancements.size, removedAdvancements.size)
        return true
    }

    private fun applyTags(server: MinecraftServer): Boolean {
        if (tagMutations.isEmpty()) {
            return false
        }

        var changed = false
        tagMutations.forEach { (registryKey, mutations) ->
            changed = applyTagRegistry(server, registryKey, mutations) || changed
        }
        if (changed) {
            LOGGER.info("Applied scripted tag mutations for {} registries", tagMutations.size)
        }
        return changed
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyTagRegistry(
        server: MinecraftServer,
        registryKey: ResourceKey<out Registry<*>>,
        mutations: Map<Identifier, TagMutation>
    ): Boolean {
        val registry = server.registryAccess().registries()
            .filter { entry -> entry.key() == registryKey }
            .findFirst()
            .orElse(null)
            ?.value() as? Registry<Any>
            ?: return false

        val tagMap = linkedMapOf<TagKey<Any>, MutableList<Holder<Any>>>()
        registry.listTags().forEach { named ->
            val values = mutableListOf<Holder<Any>>()
            named.forEach { holder -> values.add(holder) }
            tagMap[named.key()] = values
        }

        mutations.forEach { (tagId, mutation) ->
            val key = TagKey.create(registry.key(), tagId)
            val values = linkedSetOf<Holder<Any>>()
            if (!mutation.replaceContents) {
                values.addAll(tagMap[key].orEmpty())
            }

            mutation.removedEntries.forEach { entry ->
                resolveTagEntry(registry, tagMap, entry).forEach(values::remove)
            }

            mutation.addedEntries.forEach { entry ->
                values.addAll(resolveTagEntry(registry, tagMap, entry))
            }

            tagMap[key] = values.toMutableList()
        }

        val loadResult = TagLoader.LoadResult(
            registry.key(),
            tagMap.mapValues { it.value.toList() }
        )
        registry.prepareTagReload(loadResult).apply()
        return true
    }

    private fun resolveTagEntry(
        registry: Registry<Any>,
        tagMap: Map<TagKey<Any>, List<Holder<Any>>>,
        entry: TagEntryRef
    ): List<Holder<Any>> {
        return if (entry.isTag) {
            tagMap[TagKey.create(registry.key(), entry.id)].orEmpty()
        } else {
            registry.get(entry.id).map { listOf<Holder<Any>>(it) }.orElse(emptyList())
        }
    }
}

class TagMutation {
    internal val addedEntries = mutableListOf<TagEntryRef>()
    internal val removedEntries = mutableListOf<TagEntryRef>()
    internal var replaceContents: Boolean = false

    fun clear() {
        replaceContents = true
        addedEntries.clear()
        removedEntries.clear()
    }

    fun replace(block: TagMutation.() -> Unit) {
        clear()
        block()
    }

    fun add(id: Identifier) {
        addedEntries += TagEntryRef(id, false)
    }

    fun add(id: String) {
        add(Identifier.parse(id))
    }

    fun addTag(id: Identifier) {
        addedEntries += TagEntryRef(id, true)
    }

    fun addTag(id: String) {
        addTag(Identifier.parse(id))
    }

    fun remove(id: Identifier) {
        removedEntries += TagEntryRef(id, false)
    }

    fun remove(id: String) {
        remove(Identifier.parse(id))
    }

    fun removeTag(id: Identifier) {
        removedEntries += TagEntryRef(id, true)
    }

    fun removeTag(id: String) {
        removeTag(Identifier.parse(id))
    }
}

data class TagEntryRef(val id: Identifier, val isTag: Boolean)