package top.katton.api

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

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