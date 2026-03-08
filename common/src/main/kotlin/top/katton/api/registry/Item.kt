@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonItemProperties
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id
import java.util.*


/**
 * Registers a native Item with hot-reload support.
 * 
 * This is the primary API for registering custom Item subclasses from scripts.
 * The item will be registered in the global Minecraft registry with full
 * hot-reload capability.
 *
 * @param id Item identifier (e.g., "mymod:my_item")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param configure Configuration lambda for item properties
 * @param itemFactory Factory function to create the Item instance
 * @return The registered KattonItemEntry
 *
 * @example
 * ```kotlin
 * registerNativeItem(
 *     id = "mymod:custom_sword",
 *     registerMode = RegisterMode.RELOADABLE,
 *     configure = {
 *         setName(Component.literal("Custom Sword"))
 *         stacksTo(1)
 *     }
 * ) { properties ->
 *     object : Item(properties) {
 *         override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
 *             // Custom behavior
 *             InteractionResult.SUCCESS
 *         }
 *     }
 * }
 * ```
 */
@ApiStatus.Experimental
fun registerNativeItem(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    configure: KattonItemProperties.() -> Unit = {},
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry = registerNativeItem(id(id), registerMode, configure, itemFactory)

/**
 * Registers a native Item with hot-reload support.
 *
 * @param id Item identifier
 * @param registerMode Registration mode
 * @param configure Configuration lambda for item properties
 * @param itemFactory Factory function to create the Item instance
 * @return The registered KattonItemEntry
 */
@ApiStatus.Experimental
fun registerNativeItem(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    configure: KattonItemProperties.() -> Unit = {},
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry {
    val properties = KattonItemProperties.components(id).apply(configure)
    return registerNativeItemInternal(id, properties, registerMode) { itemFactory(properties) }
}

/**
 * Registers a native Item with pre-configured properties.
 *
 * @param id Item identifier
 * @param properties Pre-configured item properties
 * @param registerMode Registration mode
 * @param itemFactory Factory function to create the Item instance
 * @return The registered KattonItemEntry
 */
@ApiStatus.Experimental
fun registerNativeItem(
    id: String,
    properties: KattonItemProperties,
    registerMode: RegisterMode = RegisterMode.AUTO,
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry = registerNativeItemInternal(id(id), properties, registerMode) { itemFactory(properties) }

// Internal implementation
private fun registerNativeItemInternal(
    id: Identifier,
    properties: KattonItemProperties,
    registerMode: RegisterMode = RegisterMode.AUTO,
    itemFactory: () -> Item
): KattonRegistry.KattonItemEntry {
    require(properties.id == id) {
        "Item id mismatch: id=$id properties.id=${properties.id}"
    }
    return KattonRegistry.ITEMS.newNative(properties, registerMode) { itemFactory() }
}
