@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.entity.BlockEntityType
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * %en
 * Registers a native BlockEntityType with hot-reload support.
 *
 * %zh
 * 注册原生 BlockEntityType，并支持热重载。
 * @param id
 * %en BlockEntityType identifier (e.g., "mymod:custom_block_entity")
 * %zh BlockEntityType 标识符，例如 "mymod:custom_block_entity"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param blockEntityTypeFactory
 * %en Factory function to create the BlockEntityType instance
 * %zh 创建 BlockEntityType 实例的工厂函数。
 * @return
 * %en registered KattonBlockEntityTypeEntry
 * %zh 已注册的 KattonBlockEntityTypeEntry。
 */
fun registerNativeBlockEntityType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    blockEntityTypeFactory: () -> BlockEntityType<*>
): KattonRegistry.KattonBlockEntityTypeEntry = registerNativeBlockEntityType(id(id), registerMode, blockEntityTypeFactory)

/**
 * %en
 * Registers a native BlockEntityType with hot-reload support.
 *
 * %zh
 * 注册原生 BlockEntityType，并支持热重载。
 * @param id
 * %en BlockEntityType identifier
 * %zh BlockEntityType 标识符。
 * @param registerMode
 * %en Registration mode
 * %zh 注册模式。
 * @param blockEntityTypeFactory
 * %en Factory function to create the BlockEntityType instance
 * %zh 创建 BlockEntityType 实例的工厂函数。
 * @return
 * %en registered KattonBlockEntityTypeEntry
 * %zh 已注册的 KattonBlockEntityTypeEntry。
 */
fun registerNativeBlockEntityType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    blockEntityTypeFactory: () -> BlockEntityType<*>
): KattonRegistry.KattonBlockEntityTypeEntry {
    return KattonRegistry.BLOCK_ENTITY_TYPES.newNative(id, registerMode, blockEntityTypeFactory)
}
