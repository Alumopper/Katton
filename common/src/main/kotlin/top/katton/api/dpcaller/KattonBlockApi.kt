package top.katton.api.dpcaller

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * Block management API for level block operations.
 *
 * This module provides convenient access to blocks within a level
 * using operator syntax for getting and setting blocks.
 */

/**
 * Map-like access to blocks in a level by position.
 *
 * @property level The Level containing the blocks
 */
class KattonLevelBlockCollection(
    val level: Level
) {
    /**
     * Get the Block at a position.
     */
    operator fun get(blockPos: BlockPos): Block {
        return level.getBlockState(blockPos).block
    }

    /**
     * Set a Block at a position using its default state.
     */
    operator fun set(blockPos: BlockPos, block: Block) {
        setBlock(level, blockPos, block)
    }

    /**
     * Fill a region with a Block using its default state.
     */
    operator fun set(start: BlockPos, end: BlockPos, block: Block) {
        fill(level, start, end, block)
    }
}

/**
 * Map-like access to block states in a level by position.
 *
 * @property level The Level containing the blocks
 */
class KattonLevelBlockStateCollection(
    val level: Level
) {
    /**
     * Get the BlockState at a position.
     */
    operator fun get(blockPos: BlockPos): BlockState {
        return level.getBlockState(blockPos)
    }

    /**
     * Set a BlockState at a position.
     */
    operator fun set(blockPos: BlockPos, blockState: BlockState) {
        setBlock(level, blockPos, blockState)
    }

    /**
     * Fill a region with a BlockState.
     */
    operator fun set(start: BlockPos, end: BlockPos, blockState: BlockState) {
        fill(level, start, end, blockState)
    }
}
