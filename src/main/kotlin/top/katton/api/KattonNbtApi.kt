@file:Suppress("unused")

package top.katton.api

import net.minecraft.advancements.criterion.NbtPredicate
import net.minecraft.core.BlockPos
import net.minecraft.nbt.*
import net.minecraft.resources.Identifier
import net.minecraft.server.commands.data.BlockDataAccessor
import net.minecraft.server.commands.data.EntityDataAccessor
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import kotlin.jvm.optionals.getOrNull

fun <T : Number> numericTagOf(value: T) {
    when (value) {
        is Byte -> ByteTag.valueOf(value)
        is Int -> IntTag.valueOf(value)
        is Long -> LongTag.valueOf(value)
        is Float -> FloatTag.valueOf(value)
        is Double -> DoubleTag.valueOf(value)
        else -> error("Unsupported number type: ${value.javaClass.simpleName}")
    }
}

@Suppress("UNCHECKED_CAST")
operator fun <T : Tag> Tag?.invoke(tagType: TagType<T>): T? {
    if (this == null) return null
    if (this.type == tagType) return this as T
    if (tagType == StringTag.TYPE) {
        return StringTag.valueOf(this.toString()) as T
    }
    if (this is NumericTag) {
        return when (tagType) {
            ByteTag.TYPE -> this.asByte().map { ByteTag.valueOf(it) }.getOrNull()
            IntTag.TYPE -> this.asInt().map { IntTag.valueOf(it) }.getOrNull()
            LongTag.TYPE -> this.asLong().map { LongTag.valueOf(it) }.getOrNull()
            FloatTag.TYPE -> this.asFloat().map { FloatTag.valueOf(it) }.getOrNull()
            DoubleTag.TYPE -> this.asDouble().map { DoubleTag.valueOf(it) }.getOrNull()
            else -> null
        } as? T
    }
    if (this is StringTag) {
        val value = this.asString().getOrNull() ?: return null
        val tag = TagParser.create(NbtOps.INSTANCE).parseFully(value)
        return tag.invoke(tagType)
    }
    if (this is CollectionTag) {
        return when (tagType) {
            ListTag.TYPE -> {
                val list = ListTag()
                for (element in this) {
                    list.add(element)
                }
                list
            }

            ByteArrayTag.TYPE -> ByteArrayTag(ByteArray(this.size()) { this[it].asByte().getOrNull() ?: 0 })

            IntArrayTag.TYPE -> IntArrayTag(IntArray(this.size()) { this[it].asInt().getOrNull() ?: 0 })

            LongArrayTag.TYPE -> LongArrayTag(LongArray(this.size()) { this[it].asLong().getOrNull() ?: 0 })

            else -> null
        } as? T
    }
    if (this.type != tagType) return null
    return this as? T
}

@Suppress("UNCHECKED_CAST")
operator fun <V> Tag?.invoke(default: V): V {
    if (this == null) return default
    if (default is String) {
        return this.toString() as V
    }
    if (this is NumericTag) {
        return when (default) {
            is Boolean -> this.asBoolean().orElse(default)
            is Byte -> this.asByte().orElse(default)
            is Short -> this.asShort().orElse(default)
            is Int -> this.asInt().orElse(default)
            is Long -> this.asLong().orElse(default)
            is Float -> this.asFloat().orElse(default)
            is Double -> this.asDouble().orElse(default)
            else -> default
        } as V
    }
    if (this is StringTag) {
        val value = this.asString().getOrNull() ?: return default
        val tag = TagParser.create(NbtOps.INSTANCE).parseFully(value)
        return tag.invoke(default)
    }
    if (this is CollectionTag) {
        return when (default) {
            is List<*> -> {
                val list = ArrayList<Any>()
                for (element in this) {
                    list.add(element)
                }
                list
            }
            is ByteArray -> ByteArray(this.size()) { this[it].asByte().getOrNull() ?: 0 }
            is IntArray -> IntArray(this.size()) { this[it].asInt().getOrNull() ?: 0 }
            is LongArray -> LongArray(this.size()) { this[it].asLong().getOrNull() ?: 0 }
            else -> default
        } as V
    }
    return default
}


fun ByteTag?.toBoolean(): Boolean = this?.asBoolean()?.getOrNull() ?: false

fun CompoundTag.clear() {
    val keySet = keySet()
    for (key in keySet) {
        remove(key)
    }
}

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
    return NbtPredicate.getEntityTagToCompare(entity)
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


