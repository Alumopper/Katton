@file:Suppress("unused")

package top.katton.api.mod

import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.component.Consumables
import net.minecraft.world.item.component.DamageResistant
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.UseRemainder
import net.minecraft.world.item.component.Weapon
import net.minecraft.network.chat.Component
import org.jetbrains.annotations.ApiStatus
import org.slf4j.LoggerFactory
import top.katton.api.server
import top.katton.bridger.ModifyContextImpl
import top.katton.registry.id
import top.katton.util.ReflectUtil
import java.util.concurrent.ConcurrentHashMap

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
    var fireResistant: Boolean? = null
    var craftingRemainder: ItemStack? = null
    var attackDamage: Double? = null
    var attackSpeed: Double? = null

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

    fun fireResistant(value: Boolean = true) {
        fireResistant = value
    }

    fun craftingRemainder(value: ItemStack) {
        craftingRemainder = value
    }

    fun attackDamage(value: Double) {
        attackDamage = value
    }

    fun attackSpeed(value: Double) {
        attackSpeed = value
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
    ACTIVE_ITEM_MODIFICATIONS[itemId] = config

    applyItemModifications(item, config)
    return item
}

fun clearItemModifications() {
    ACTIVE_ITEM_MODIFICATIONS.clear()
}

fun reapplyItemModifications() {
    ACTIVE_ITEM_MODIFICATIONS.values.forEach { config ->
        val item = BuiltInRegistries.ITEM.getOptional(config.itemId).orElse(null) ?: return@forEach
        runCatching { applyItemModifications(item, config) }
            .onFailure { LOGGER.warn("Failed to reapply item modification for {}", config.itemId, it) }
    }
}

private fun applyItemModifications(item: Item, config: ItemModificationConfig) {
    validateDurabilityStacking(item, config)
    ModifyContextImpl.modify(item) { builder ->
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
            builder.set(DataComponents.CONSUMABLE, Consumables.defaultFood().animation(ItemUseAnimation.EAT).build())
        }

        config.fireResistant?.let { enabled ->
            if (enabled) {
                createFireResistance()?.let { builder.set(DataComponents.DAMAGE_RESISTANT, it) }
            } else {
                LOGGER.warn(
                    "Disabling fireResistant on already-resistant items is not supported via DataComponentMap.Builder; ignoring for {}",
                    item
                )
            }
        }

        config.craftingRemainder?.let { remainder ->
            builder.set(DataComponents.USE_REMAINDER, UseRemainder(ItemStackTemplate.fromNonEmptyStack(remainder)))
        }

        val attackDamage = config.attackDamage
        val attackSpeed = config.attackSpeed
        if (attackDamage != null || attackSpeed != null) {
            builder.set(DataComponents.ATTRIBUTE_MODIFIERS, createAttributeModifiers(attackDamage, attackSpeed))
            if (!currentComponents(item).has(DataComponents.WEAPON)) {
                builder.set(DataComponents.WEAPON, Weapon(1))
            }
        }
    }
}

private fun validateDurabilityStacking(item: Item, config: ItemModificationConfig) {
    val components = currentComponents(item)
    val targetMaxStack = config.maxStackSize ?: components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1)
    val targetMaxDamage = config.maxDamage ?: components.getOrDefault(DataComponents.MAX_DAMAGE, 0)
    if (targetMaxStack > 1 && targetMaxDamage > 0) {
        throw IllegalArgumentException(
            "Item ${BuiltInRegistries.ITEM.getKey(item)} cannot have both maxStackSize=$targetMaxStack and maxDamage=$targetMaxDamage"
        )
    }
}

private fun currentComponents(item: Item): DataComponentMap {
    return ReflectUtil.getT<DataComponentMap>(item, "components").getOrNull()
        ?: runCatching { item.components() }.getOrDefault(DataComponentMap.EMPTY)
}

private fun createFireResistance(): DamageResistant? {
    val server = server ?: run {
        LOGGER.warn("fireResistant item modification requires a running server for DAMAGE_TYPE registry access")
        return null
    }
    val damageTypeRegistry = server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
    return DamageResistant(HolderSet.emptyNamed(damageTypeRegistry, DamageTypeTags.IS_FIRE))
}

private fun createAttributeModifiers(attackDamage: Double?, attackSpeed: Double?): ItemAttributeModifiers {
    val builder = ItemAttributeModifiers.builder()
    attackDamage?.let { damage ->
        builder.add(
            Attributes.ATTACK_DAMAGE,
            AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
        )
    }
    attackSpeed?.let { speed ->
        builder.add(
            Attributes.ATTACK_SPEED,
            AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed - 4.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND
        )
    }
    return builder.build()
}

private val ACTIVE_ITEM_MODIFICATIONS = ConcurrentHashMap<Identifier, ItemModificationConfig>()
private val LOGGER = LoggerFactory.getLogger("top.katton.api.mod.KattonItemModificationApi")

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
