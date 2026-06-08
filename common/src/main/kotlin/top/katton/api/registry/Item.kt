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
 * %en
 * Registers a native Item with hot-reload support.
 *
 * This is the primary API for registering custom Item subclasses from scripts.
 * The item will be registered in the global Minecraft registry with full
 * hot-reload capability.
 *
 * %zh
 * 注册原生 Item，并支持热重载。
 * 这是脚本中注册自定义 Item 子类的主要 API。通过该 API 注册的 Item
 * 会进入全局 Minecraft 注册表，并具备完整的热重载能力。
 * @param id
 * %en Item identifier (e.g., "mymod:my_item")
 * %zh Item 标识符，例如 "mymod:my_item"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param configure
 * %en Configuration lambda for item properties
 * %zh Item 属性配置 lambda。
 * @param itemFactory
 * %en Factory function to create the Item instance
 * %zh 用于创建 Item 实例的工厂函数。
 * @return
 * %en registered KattonItemEntry
 * %zh 已注册的 KattonItemEntry。
 * @example
 * %en
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
 * %zh 示例代码见英文部分。
 */
@ApiStatus.Experimental
fun registerNativeItem(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonItemProperties.() -> Unit = {},
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry = registerNativeItem(id(id), registerMode, configure, itemFactory)

/**
 * %en
 * Registers a native Item with hot-reload support.
 *
 * %zh
 * 注册原生 Item，并支持热重载。
 * @param id
 * %en Item identifier
 * %zh Item 标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param configure
 * %en Configuration lambda for item properties
 * %zh Item 属性配置 lambda。
 * @param itemFactory
 * %en Factory function to create the Item instance
 * %zh 用于创建 Item 实例的工厂函数。
 * @return
 * %en registered KattonItemEntry
 * %zh 已注册的 KattonItemEntry。
 */
@ApiStatus.Experimental
fun registerNativeItem(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonItemProperties.() -> Unit = {},
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry {
    val properties = KattonItemProperties.components(id).apply(configure)
    return registerNativeItemInternal(id, properties, registerMode) { itemFactory(properties) }
}

/**
 * %en
 * Registers a native Item with pre-configured properties.
 *
 * %zh
 * 注册带有预配置属性的原生 Item。
 * @param id
 * %en Item identifier
 * %zh Item 标识符。
 * @param properties
 * %en Pre-configured item properties
 * %zh 预先配置好的 Item 属性。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param itemFactory
 * %en Factory function to create the Item instance
 * %zh 用于创建 Item 实例的工厂函数。
 * @return
 * %en registered KattonItemEntry
 * %zh 已注册的 KattonItemEntry。
 */
@ApiStatus.Experimental
fun registerNativeItem(
    id: String,
    properties: KattonItemProperties,
    registerMode: RegisterMode = RegisterMode.WORLD,
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry = registerNativeItemInternal(id(id), properties, registerMode) { itemFactory(properties) }

// Internal implementation
private fun registerNativeItemInternal(
    id: Identifier,
    properties: KattonItemProperties,
    registerMode: RegisterMode = RegisterMode.WORLD,
    itemFactory: () -> Item
): KattonRegistry.KattonItemEntry {
    require(properties.id == id) {
        "Item id mismatch: id=$id properties.id=${properties.id}"
    }
    return KattonRegistry.ITEMS.newNative(properties, registerMode) { itemFactory() }
}
