@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import top.katton.registry.KattonEntityProperties
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * %en
 * Registers a native EntityType with hot-reload support.
 *
 * This is a lower-level API that only registers the EntityType itself.
 * For complete entity registration (including attributes, spawn egg, and
 * spawn placement), use [registerNativeEntity] instead.
 *
 * %zh
 * 注册原生 EntityType，并支持热重载。
 * 这是一个更底层的 API，只负责注册 EntityType 本身。
 * 如果需要完整的实体注册流程（包括属性、刷怪蛋和生成位置），请改用 [registerNativeEntity]。
 * @param id
 * %en Entity identifier (e.g., "mymod:custom_entity")
 * %zh 实体标识符，例如 "mymod:custom_entity"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param entityTypeFactory
 * %en Factory function to create the EntityType instance
 * %zh 创建 EntityType 实例的工厂函数。
 * @return
 * %en registered KattonEntityTypeEntry
 * %zh 已注册的 KattonEntityTypeEntry。
 */
fun registerNativeEntityType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    entityTypeFactory: () -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry = registerNativeEntityType(id(id), registerMode, entityTypeFactory)

/**
 * %en
 * Registers a native EntityType with hot-reload support (Identifier overload).
 *
 * %zh
 * 注册原生 EntityType，并支持热重载（Identifier 重载）。
 * @param id
 * %en Entity identifier
 * %zh 实体标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param entityTypeFactory
 * %en Factory function to create the EntityType instance
 * %zh 创建 EntityType 实例的工厂函数。
 * @return
 * %en registered KattonEntityTypeEntry
 * %zh 已注册的 KattonEntityTypeEntry。
 */
fun registerNativeEntityType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    entityTypeFactory: () -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry {
    return KattonRegistry.ENTITY_TYPES.newNative(id, registerMode, entityTypeFactory)
}
