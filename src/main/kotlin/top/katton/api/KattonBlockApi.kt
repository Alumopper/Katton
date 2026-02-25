package top.katton.api

import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id

class KattonLevelBlockCollection(
    val level: Level
) {
    operator fun get(blockPos: BlockPos): Block {
        return level.getBlockState(blockPos).block
    }

    operator fun set(blockPos: BlockPos, block: Block) {
        setBlock(level, blockPos, block)
    }

    operator fun set(start: BlockPos, end: BlockPos, block: Block) {
        fill(level, start, end, block)
    }
}

class KattonLevelBlockStateCollection(
    val level: Level
) {
    operator fun get(blockPos: BlockPos): BlockState {
        return level.getBlockState(blockPos)
    }

    operator fun set(blockPos: BlockPos, blockState: BlockState) {
        setBlock(level, blockPos, blockState)
    }

    operator fun set(start: BlockPos, end: BlockPos, blockState: BlockState) {
        fill(level, start, end, blockState)
    }
}

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
