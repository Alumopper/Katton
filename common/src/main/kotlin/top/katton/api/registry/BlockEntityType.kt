@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.entity.BlockEntityType
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * Registers a native BlockEntityType with hot-reload support.
 *
 * @param id BlockEntityType identifier (e.g., "mymod:custom_block_entity")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param blockEntityTypeFactory Factory function to create the BlockEntityType instance
 * @return The registered KattonBlockEntityTypeEntry
 */
fun registerNativeBlockEntityType(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    blockEntityTypeFactory: () -> BlockEntityType<*>
): KattonRegistry.KattonBlockEntityTypeEntry = registerNativeBlockEntityType(id(id), registerMode, blockEntityTypeFactory)

/**
 * Registers a native BlockEntityType with hot-reload support.
 *
 * @param id BlockEntityType identifier
 * @param registerMode Registration mode
 * @param blockEntityTypeFactory Factory function to create the BlockEntityType instance
 * @return The registered KattonBlockEntityTypeEntry
 */
fun registerNativeBlockEntityType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    blockEntityTypeFactory: () -> BlockEntityType<*>
): KattonRegistry.KattonBlockEntityTypeEntry {
    return KattonRegistry.BLOCK_ENTITY_TYPES.newNative(id, registerMode, blockEntityTypeFactory)
}
