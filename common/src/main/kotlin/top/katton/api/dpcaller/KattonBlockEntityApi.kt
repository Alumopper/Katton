package top.katton.api.dpcaller

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * Block entity management API for block entity operations.
 *
 * This module provides convenient access to block entities within a level
 * using operator syntax for getting and setting block entities.
 */

/**
 * Map-like access to block entities in a level by position.
 *
 * @property level The Level containing the block entities
 */
class KattonLevelBlockEntityCollection(
    val level: Level
) {
    /**
     * Get the BlockEntity at a position.
     */
    operator fun get(blockPos: BlockPos): BlockEntity? {
        return level.getBlockEntity(blockPos)
    }

    /**
     * Set a BlockEntity at a specific position.
     *
     * The block entity's position must match the target position.
     */
    operator fun set(blockPos: BlockPos, blockEntity: BlockEntity) {
        if (blockEntity.blockPos == blockPos) {
            level.setBlockEntity(blockEntity)
        }
    }

    /**
     * Set a BlockEntity in the level at its own position.
     */
    fun set(blockEntity: BlockEntity) {
        level.setBlockEntity(blockEntity)
    }
}

/**
 * Extension property to get/set NBT data on a BlockEntity.
 */
var BlockEntity.nbt: CompoundTag
    get() = getBlockNbt(this)
    set(value) {
        setBlockNbt(this, value)
    }