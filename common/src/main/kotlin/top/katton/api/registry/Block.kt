package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id


/**
 * Registers a native Block with hot-reload support.
 */
@ApiStatus.Experimental
fun registerNativeBlock(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    blockFactory: (BlockBehaviour.Properties) -> Block
): KattonRegistry.KattonBlockEntry = registerNativeBlock(id(id), registerMode, blockFactory)

/**
 * Registers a native Block with hot-reload support.
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
 */
fun createSimpleBlock(properties: BlockBehaviour.Properties = BlockBehaviour.Properties.of()): Block {
    return Block(properties)
}
