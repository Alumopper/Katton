@file:Suppress("unused")

package top.katton.api.mod

import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.id

/**
 * Configuration for modifying existing item properties.
 * 
 * This class provides a fluent API for modifying properties of existing
 * items registered in Minecraft's item registry. Similar to KubeJS's item
 * modification system.
 * 
 * @property itemId The identifier of the item to modify
 */
class ItemModificationConfig(
    val itemId: Identifier
) {
    var maxStackSize: Int? = null
    var maxDamage: Int? = null
    var rarity: Rarity? = null
    var name: Component? = null
    var foodProperties: FoodProperties? = null

    fun maxStackSize(value: Int) {
        maxStackSize = value
    }

    fun maxDamage(value: Int) {
        maxDamage = value
    }

    fun rarity(value: Rarity) {
        rarity = value
    }

    fun name(value: Component) {
        name = value
    }

    fun food(value: FoodProperties) {
        foodProperties = value
    }
}

/**
 * Modifies an existing item's properties.
 * 
 * This function allows you to modify properties of items already registered
 * in Minecraft's item registry. Changes are applied to the item's default
 * components and will affect all ItemStacks of that type.
 * 
 * @param itemId The identifier of the item to modify (e.g., "minecraft:diamond")
 * @param configure Configuration lambda for item modifications
 * @return The modified Item instance
 * 
 * @example
 * ```kotlin
 * modifyItem("minecraft:diamond") {
 *     maxStackSize = 16
 *     rarity = Rarity.EPIC
 *     name(Component.literal("Super Diamond"))
 * }
 * ```
 */
@ApiStatus.Experimental
fun modifyItem(itemId: String, configure: ItemModificationConfig.() -> Unit): Item {
    return modifyItem(id(itemId), configure)
}

/**
 * Modifies an existing item's properties.
 * 
 * @param itemId The identifier of the item to modify
 * @param configure Configuration lambda for item modifications
 * @return The modified Item instance
 */
@ApiStatus.Experimental
fun modifyItem(itemId: Identifier, configure: ItemModificationConfig.() -> Unit): Item {
    val item = BuiltInRegistries.ITEM.getOptional(itemId)
        .orElseThrow { IllegalArgumentException("Item not found: $itemId") }
    val config = ItemModificationConfig(itemId).apply(configure)
    
    applyItemModifications(item, config)
    return item
}

private fun applyItemModifications(item: Item, config: ItemModificationConfig) {
    val holder = item.builtInRegistryHolder
    val currentComponents = holder.components ?: DataComponentMap.EMPTY
    val builder = DataComponentMap.builder()
    
    builder.addAll(currentComponents)
    
    config.maxStackSize?.let { size ->
        builder.set(DataComponents.MAX_STACK_SIZE, size)
    }
    
    config.maxDamage?.let { damage ->
        builder.set(DataComponents.MAX_DAMAGE, damage)
    }
    
    config.rarity?.let { rarity ->
        builder.set(DataComponents.RARITY, rarity)
    }
    
    config.name?.let { name ->
        builder.set(DataComponents.ITEM_NAME, name)
    }
    
    config.foodProperties?.let { food ->
        builder.set(DataComponents.FOOD, food)
    }
    
    holder.components = builder.build()
}

/**
 * Gets an item by its identifier.
 * 
 * @param itemId The item identifier
 * @return The Item instance, or null if not found
 */
fun getItem(itemId: String): Item? {
    return getItem(id(itemId))
}

/**
 * Gets an item by its identifier.
 * 
 * @param itemId The item identifier
 * @return The Item instance, or null if not found
 */
fun getItem(itemId: Identifier): Item? {
    return BuiltInRegistries.ITEM.getOptional(itemId).orElse(null)
}

/**
 * Creates an ItemStack for an item.
 * 
 * @param itemId The item identifier
 * @param count The stack size
 * @return The created ItemStack
 */
fun itemStack(itemId: String, count: Int = 1): ItemStack {
    return itemStack(id(itemId), count)
}

/**
 * Creates an ItemStack for an item.
 * 
 * @param itemId The item identifier
 * @param count The stack size
 * @return The created ItemStack
 */
fun itemStack(itemId: Identifier, count: Int = 1): ItemStack {
    val item = getItem(itemId) ?: throw IllegalArgumentException("Item not found: $itemId")
    return ItemStack(item, count)
}
