package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

/**
 * Block registration API for custom blocks.
 *
 * This module provides functions to register custom Blocks with hot-reload support.
 * Blocks registered through this API can be reloaded during development without
 * restarting the game.
 *
 * Note: Block registration is experimental and may have limitations with
 * complex block behaviors or block entities.
 */

/**
 * Registers a native Block with hot-reload support (String overload).
 *
 * This is the primary API for registering custom Block subclasses from scripts.
 * The block will be registered in the global Minecraft registry with full
 * hot-reload capability.
 *
 * @param id Block identifier (e.g., "mymod:custom_block")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param blockFactory Factory function to create the Block instance, receives Properties
 * @return The registered KattonBlockEntry
 *
 * @example
 * ```kotlin
 * registerNativeBlock("mymod:custom_block") { properties ->
 *     object : Block(properties) {
 *         // Custom block behavior
 *     }
 * }
 * ```
 */
@ApiStatus.Experimental
fun registerNativeBlock(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    blockFactory: (BlockBehaviour.Properties) -> Block
): KattonRegistry.KattonBlockEntry = registerNativeBlock(id(id), registerMode, blockFactory)

/**
 * Registers a native Block with hot-reload support (Identifier overload).
 *
 * @param id Block identifier
 * @param registerMode Registration mode
 * @param blockFactory Factory function to create the Block instance
 * @return The registered KattonBlockEntry
 */
@ApiStatus.Experimental
fun registerNativeBlock(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    blockFactory: (BlockBehaviour.Properties) -> Block
): KattonRegistry.KattonBlockEntry {
    return KattonRegistry.BLOCKS.newNative(id, registerMode, blockFactory)
}

/**
 * Utility factory for quickly creating a simple custom block.
 *
 * Creates a basic Block with the specified properties.
 * For more complex blocks, use the full factory pattern with registerNativeBlock.
 *
 * @param properties Block behavior properties (default: basic properties)
 * @return A new Block instance
 */
fun createSimpleBlock(properties: BlockBehaviour.Properties = BlockBehaviour.Properties.of()): Block {
    return Block(properties)
}
