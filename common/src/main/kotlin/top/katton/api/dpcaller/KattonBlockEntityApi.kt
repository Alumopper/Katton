package top.katton.api.dpcaller

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * %en
 * Block entity management API for block entity operations.
 *
 * This module provides convenient access to block entities within a level
 * using operator syntax for getting and setting block entities.
 *
 * %zh
 * 方块实体管理 API，用于方块实体相关操作。
 * 本模块提供对关卡中方块实体的便捷访问，
 * 支持通过运算符语法读取和设置方块实体。
 */

/**
 * %en
 * Map-like access to block entities in a level by position.
 *
 * %zh
 * 以类似 Map 的方式按位置访问关卡中的方块实体。
 * @property level
 * %en The Level containing the block entities
 * %zh 包含这些方块实体的 Level。
 */
class KattonLevelBlockEntityCollection(
    val level: Level
) {
    /**
 * %en
 * Get the BlockEntity at a position.
 *
 * %zh
 * 获取方块Entity at a 位置。
 */
    operator fun get(blockPos: BlockPos): BlockEntity? {
        return level.getBlockEntity(blockPos)
    }

    /**
 * %en
 * Set a BlockEntity at a specific position.
 *
 * The block entity's position must match the target position.
 *
 * %zh
 * 在指定位置设置一个方块实体。
 * 方块实体自身的位置必须与目标位置一致。
 */
    operator fun set(blockPos: BlockPos, blockEntity: BlockEntity) {
        if (blockEntity.blockPos == blockPos) {
            level.setBlockEntity(blockEntity)
        }
    }

    /**
 * %en
 * Set a BlockEntity in the level at its own position.
 *
 * %zh
 * 在关卡中按方块实体自身的位置设置它。
 */
    fun set(blockEntity: BlockEntity) {
        level.setBlockEntity(blockEntity)
    }
}

/**
 * %en
 * Extension property to get/set NBT data on a BlockEntity.
 *
 * %zh
 * 用于读取或设置方块实体 NBT 数据的扩展属性。
 */
var BlockEntity.nbt: CompoundTag
    get() = getBlockNbt(this)
    set(value) {
        setBlockNbt(this, value)
    }
