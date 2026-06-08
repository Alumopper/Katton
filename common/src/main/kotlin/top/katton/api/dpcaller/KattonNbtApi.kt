@file:Suppress("unused")

package top.katton.api.dpcaller

import net.minecraft.advancements.criterion.NbtPredicate
import net.minecraft.core.BlockPos
import net.minecraft.nbt.*
import net.minecraft.resources.Identifier
import net.minecraft.server.commands.data.BlockDataAccessor
import net.minecraft.server.commands.data.EntityDataAccessor
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import top.katton.api.requireServer
import kotlin.jvm.optionals.getOrNull

/**
 * %en
 * NBT data manipulation API.
 *
 * This module provides utilities for working with Minecraft's NBT (Named Binary Tag)
 * data format, including:
 * - Tag type conversion
 * - Safe tag access with defaults
 * - Entity and block entity NBT access
 *
 * %zh
 * NBT 数据操作 API。
 * 本模块提供一组用于处理 Minecraft NBT（Named Binary Tag）数据格式的工具，包括：
 * - 标签类型转换
 * - 带默认值的安全标签读取
 * - 实体与方块实体的 NBT 访问
 */

/**
 * %en
 * Create a numeric Tag from a Number value.
 *
 * %zh
 * 根据 Number 值创建数值类型的 Tag。
 * @param value
 * %en The numeric value to convert
 * %zh 要转换的数值。
 * @return
 * %en appropriate NumericTag subtype
 * %zh 返回对应的 NumericTag 子类型。
 */
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


/**
 * %en
 * Safely cast a Tag to a specific TagType.
 *
 * Performs automatic type conversion where possible:
 * - Numeric tags can be converted between numeric types
 * - String tags can be parsed into other types
 * - Collection tags can be converted between array types
 *
 * %zh
 * 将 Tag 安全转换为指定的 TagType。
 * 在可能的情况下会自动进行类型转换：
 * - 数值标签可在不同数值类型之间转换
 * - 字符串标签可解析为其他类型
 * - 集合标签可转换为数组类型
 * @param tagType
 * %en The target TagType
 * %zh 目标 TagType。
 * @return
 * %en casted Tag or null if conversion is not possible
 * %zh 转换成功则返回对应的 Tag，无法转换则返回 null。
 */
fun <T : Tag> Tag?.getValue(tagType: TagType<T>): T? {
    return this(tagType)
}

/**
 * %en
 * Safely cast a Tag to a specific TagType.
 *
 * Performs automatic type conversion where possible:
 * - Numeric tags can be converted between numeric types
 * - String tags can be parsed into other types
 * - Collection tags can be converted between array types
 *
 * %zh
 * 将 Tag 安全转换为指定的 TagType。
 * 在可能的情况下会自动进行类型转换：
 * - 数值标签可在不同数值类型之间转换
 * - 字符串标签可解析为其他类型
 * - 集合标签可转换为数组类型
 * @param tagType
 * %en The target TagType
 * %zh 目标 TagType。
 * @return
 * %en casted Tag or null if conversion is not possible
 * %zh 转换成功则返回对应的 Tag，无法转换则返回 null。
 */
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


/**
 * %en
 * Safely get a value from a Tag with a default fallback.
 *
 * Performs automatic type conversion where possible:
 * - Numeric tags return numeric values
 * - String tags return string values
 * - Collection tags return lists
 *
 * %zh
 * 从 Tag 中安全读取值，并在读取失败时回退到默认值。
 * 在可能的情况下会自动进行类型转换：
 * - 数值标签返回数值
 * - 字符串标签返回字符串
 * - 集合标签返回列表
 * @param default
 * %en The default value to return if conversion fails
 * %zh 转换失败时返回的默认值。
 * @return
 * %en converted value or the default
 * %zh 返回转换后的值，或默认值。
 */
fun <V> Tag?.getOrValue(default: V): V {
    return this(default)
}

/**
 * %en
 * Safely get a value from a Tag with a default fallback.
 *
 * Performs automatic type conversion where possible:
 * - Numeric tags return numeric values
 * - String tags return string values
 * - Collection tags return lists
 *
 * %zh
 * 从 Tag 中安全读取值，并在读取失败时回退到默认值。
 * 在可能的情况下会自动进行类型转换：
 * - 数值标签返回数值
 * - 字符串标签返回字符串
 * - 集合标签返回列表
 * @param default
 * %en The default value to return if conversion fails
 * %zh 转换失败时返回的默认值。
 * @return
 * %en converted value or the default
 * %zh 返回转换后的值，或默认值。
 */
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

/**
 * %en
 * Convert a ByteTag to a Boolean value.
 *
 * %zh
 * 将 ByteTag 转换为布尔值。
 */
fun ByteTag?.toBoolean(): Boolean = this?.asBoolean()?.getOrNull() ?: false

fun CompoundTag.clear() {
    val keySet = keySet()
    for (key in keySet) {
        remove(key)
    }
}

/**
 * %en
 * Parse an NBT string into a CompoundTag.
 *
 * %zh
 * 将 NBT 字符串解析为 CompoundTag。
 * @param nbt
 * %en NBT string to parse
 * %zh 要解析的 NBT 字符串。
 * @return
 * %en CompoundTag
 * %zh 返回 CompoundTag。
 */
fun parseNbt(nbt: String): CompoundTag = TagParser.parseCompoundFully(nbt)


/**
 * %en
 * Get the full NBT data of an entity.
 *
 * %zh
 * 获取实体的完整 NBT 数据。
 * @param entity
 * %en the target entity
 * %zh 目标实体。
 * @return
 * %en representing the entity's data
 * %zh 返回表示该实体数据的 CompoundTag。
 */
fun getEntityNbt(entity: Entity): CompoundTag {
    return NbtPredicate.getEntityTagToCompare(entity)
}


/**
 * %en
 * Replace the NBT data of an entity.
 *
 * %zh
 * 替换实体的 NBT 数据。
 * @param entity
 * %en the target entity
 * %zh 目标实体。
 * @param tag
 * %en the CompoundTag to set on the entity
 * %zh 要设置到实体上的 CompoundTag。
 */
fun setEntityNbt(entity: Entity, tag: CompoundTag) {
    val accessor = EntityDataAccessor(entity)
    accessor.data = tag
}


/**
 * %en
 * Get the NBT data of a block entity.
 *
 * %zh
 * 获取方块实体的 NBT 数据。
 * @param block
 * %en the target BlockEntity
 * %zh 目标方块实体。
 * @return
 * %en representing the block entity's data
 * %zh 返回表示该方块实体数据的 CompoundTag。
 */
fun getBlockNbt(block: BlockEntity): CompoundTag {
    val accessor = BlockDataAccessor(block, block.blockPos)
    return accessor.data
}


/**
 * %en
 * Replace the NBT data of a block entity.
 *
 * %zh
 * 替换方块实体的 NBT 数据。
 * @param block
 * %en the target BlockEntity
 * %zh 目标方块实体。
 * @param tag
 * %en the CompoundTag to set on the block entity
 * %zh 要设置到方块实体上的 CompoundTag。
 */
fun setBlockNbt(block: BlockEntity, tag: CompoundTag) {
    val accessor = BlockDataAccessor(block, block.blockPos)
    accessor.data = tag
}


/**
 * %en
 * Get the NBT data of a block at a position if it has a block entity.
 *
 * %zh
 * 获取指定位置方块实体的 NBT 数据，如果该方块没有方块实体则返回空。
 * @param level
 * %en level to query
 * %zh 要查询的维度。
 * @param pos
 * %en position of the block
 * %zh 方块位置。
 * @return
 * %en or null if no block entity exists
 * %zh 如果不存在方块实体，则返回 null。
 */
fun getBlockNbt(level: Level, pos: BlockPos): CompoundTag? {
    return level.getBlockEntity(pos)?.let { getBlockNbt(it) }
}


/**
 * %en
 * Set the NBT of a block entity at the given position.
 *
 * %zh
 * 设置指定位置方块实体的 NBT。
 * @param level
 * %en level to modify
 * %zh 要修改的维度。
 * @param pos
 * %en block position
 * %zh 方块位置。
 * @param tag
 * %en CompoundTag to set
 * %zh 要设置的 CompoundTag。
 * @return
 * %en if set succeeded, false if no block entity present
 * %zh 设置成功返回 true；如果没有方块实体则返回 false。
 */
fun setBlockNbt(level: Level, pos: BlockPos, tag: CompoundTag): Boolean {
    return level.getBlockEntity(pos)?.let {
        setBlockNbt(it, tag)
        true
    } ?: false
}


/**
 * %en
 * Get stored command storage NBT by identifier.
 *
 * %zh
 * 通过标识符获取已存储的命令存储 NBT。
 * @param id
 * %en storage identifier
 * %zh 存储标识符。
 * @return
 * %en stored at id
 * %zh 返回该标识符下存储的 NBT。
 */
fun getStorageNbt(id: Identifier): CompoundTag{
    return requireServer().commandStorage.get(id)
}


/**
 * %en
 * Set stored command storage NBT by identifier.
 *
 * %zh
 * 通过标识符设置命令存储中的 NBT。
 * @param id
 * %en storage identifier
 * %zh 存储标识符。
 * @param tag
 * %en CompoundTag to store
 * %zh 要存储的 CompoundTag。
 */
fun setStorageNbt(id: Identifier, tag: CompoundTag) {
    requireServer().commandStorage.set(id, tag)
}
