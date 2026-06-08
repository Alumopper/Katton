package top.katton.api.dpcaller

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 *
 * Map-like access to blocks in a level by position.
 *
 *
 * 以类似 Map 的方式按位置访问关卡中的方块。
 * @property level
 * %en The Level containing the blocks
 * %zh
 */
class KattonLevelBlockCollection(
    val level: Level
) {
    /**
 * %en
 * Get the Block at a position.
 *
 * %zh
 * 获取方块 at a 位置。
 */
    operator fun get(blockPos: BlockPos): Block {
        return level.getBlockState(blockPos).block
    }

    /**
 * %en
 * Set a Block at a position using its default state.
 *
 * %zh
 * 使用默认状态在指定位置设置方块。
 */
    operator fun set(blockPos: BlockPos, block: Block) {
        setBlock(level, blockPos, block)
    }

    /**
 * %en
 * Fill a region with a Block using its default state.
 *
 * %zh
 * 使用方块的默认状态填充一个区域。
 */
    operator fun set(start: BlockPos, end: BlockPos, block: Block) {
        fill(level, start, end, block)
    }
}

/**
 * %en
 * Map-like access to block states in a level by position.
 *
 * %zh
 * 以类似 Map 的方式按位置访问关卡中的方块状态。
 * @property level
 * %en The Level containing the blocks
 * %zh 包含这些方块的 Level。
 */
class KattonLevelBlockStateCollection(
    val level: Level
) {
    /**
 * %en
 * Get the BlockState at a position.
 *
 * %zh
 * 获取方块State at a 位置。
 */
    operator fun get(blockPos: BlockPos): BlockState {
        return level.getBlockState(blockPos)
    }

    /**
 * %en
 * Set a BlockState at a position.
 *
 * %zh
 * 设置a 方块State at a 位置。
 */
    operator fun set(blockPos: BlockPos, blockState: BlockState) {
        setBlock(level, blockPos, blockState)
    }

    /**
 * %en
 * Fill a region with a BlockState.
 *
 * %zh
 * 使用方块状态填充一个区域。
 */
    operator fun set(start: BlockPos, end: BlockPos, blockState: BlockState) {
        fill(level, start, end, blockState)
    }
}
