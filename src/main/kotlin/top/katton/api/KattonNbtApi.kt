@file:Suppress("unused")

package top.katton.api

import net.minecraft.advancements.criterion.NbtPredicate
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.resources.Identifier
import net.minecraft.server.commands.data.BlockDataAccessor
import net.minecraft.server.commands.data.EntityDataAccessor
import net.minecraft.world.entity.*
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity


/**
 * Parse an NBT string into a CompoundTag.
 *
 * @param nbt NBT string to parse
 * @return parsed CompoundTag
 */
fun parseNbt(nbt: String): CompoundTag = TagParser.parseCompoundFully(nbt)


/**
 * Get the full NBT data of an entity.
 *
 * @param entity the target entity
 * @return CompoundTag representing the entity's data
 */
fun getEntityNbt(entity: Entity): CompoundTag {
    return NbtPredicate.getEntityTagToCompare(entity);
}


/**
 * Replace the NBT data of an entity.
 *
 * @param entity the target entity
 * @param tag the CompoundTag to set on the entity
 */
fun setEntityNbt(entity: Entity, tag: CompoundTag) {
    val accessor = EntityDataAccessor(entity)
    accessor.data = tag
}


/**
 * Get the NBT data of a block entity.
 *
 * @param block the target BlockEntity
 * @return CompoundTag representing the block entity's data
 */
fun getBlockNbt(block: BlockEntity): CompoundTag {
    val accessor = BlockDataAccessor(block, block.blockPos)
    return accessor.data
}


/**
 * Replace the NBT data of a block entity.
 *
 * @param block the target BlockEntity
 * @param tag the CompoundTag to set on the block entity
 */
fun setBlockNbt(block: BlockEntity, tag: CompoundTag) {
    val accessor = BlockDataAccessor(block, block.blockPos)
    accessor.data = tag
}


/**
 * Get the NBT data of a block at a position if it has a block entity.
 *
 * @param level level to query
 * @param pos position of the block
 * @return CompoundTag or null if no block entity exists
 */
fun getBlockNbt(level: Level, pos: BlockPos): CompoundTag? {
    return level.getBlockEntity(pos)?.let { getBlockNbt(it) }
}


/**
 * Set the NBT of a block entity at the given position.
 *
 * @param level level to modify
 * @param pos block position
 * @param tag CompoundTag to set
 * @return true if set succeeded, false if no block entity present
 */
fun setBlockNbt(level: Level, pos: BlockPos, tag: CompoundTag): Boolean {
    return level.getBlockEntity(pos)?.let {
        setBlockNbt(it, tag)
        true
    } ?: false
}


/**
 * Get stored command storage NBT by identifier.
 *
 * @param id storage identifier
 * @return CompoundTag stored at id
 */
fun getStorageNbt(id: Identifier): CompoundTag{
    return requireServer().commandStorage.get(id)
}


/**
 * Set stored command storage NBT by identifier.
 *
 * @param id storage identifier
 * @param tag CompoundTag to store
 */
fun setStorageNbt(id: Identifier, tag: CompoundTag) {
    requireServer().commandStorage.set(id, tag)
}


