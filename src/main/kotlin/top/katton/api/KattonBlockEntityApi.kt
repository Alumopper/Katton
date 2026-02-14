package top.katton.api

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

class KattonLevelBlockEntityCollection(
    val level: Level
) {
    operator fun get(blockPos: BlockPos): BlockEntity? {
        return level.getBlockEntity(blockPos)
    }

    operator fun set(blockPos: BlockPos, blockEntity: BlockEntity) {
        if (blockEntity.blockPos == blockPos) {
            level.setBlockEntity(blockEntity)
        }
    }

    fun set(blockEntity: BlockEntity) {
        level.setBlockEntity(blockEntity)
    }
}


var BlockEntity.nbt: CompoundTag
    get() = getBlockNbt(this)
    set(value) {
        setBlockNbt(this, value)
    }