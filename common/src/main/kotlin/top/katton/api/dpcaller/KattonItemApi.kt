@file:Suppress("unused")

package top.katton.api.dpcaller

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.functions.LootItemFunction
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import top.katton.api.LOGGER
import top.katton.api.requireServer
import java.util.*

/**
 * %en
 * Item management API for ItemStack operations.
 *
 * This module provides functions for working with items including:
 * - NBT data access on ItemStacks
 * - Container slot modification
 * - Entity equipment modification
 * - Loot function application
 *
 * %zh
 * 面向 ItemStack 操作的物品管理 API。
 * 本模块提供一组处理物品的函数，包括：
 * - 访问 ItemStack 的 NBT 数据
 * - 修改容器槽位
 * - 修改实体装备
 * - 应用战利品函数
 */

/**
 * %en
 * Extension property to get/set NBT data on an ItemStack.
 *
 * %zh
 * 用于读取和写入 ItemStack NBT 数据的扩展属性。
 */
var ItemStack.nbt: CompoundTag
    get() = components[DataComponents.CUSTOM_DATA]?.copyTag() ?: CompoundTag()
    set(value) {
        components[DataComponents.CUSTOM_DATA]?.update {
            it.clear()
            it.merge(value)
        }
    }

/**
 * %en
 * Applies a LootItemFunction modifier to a block container slot.
 *
 * %zh
 * 将 LootItemFunction 修饰器应用到方块容器的指定槽位。
 * @param pos
 * %en Block position of the container
 * %zh 容器所在的方块位置。
 * @param slot
 * %en Slot index to modify
 * %zh 要修改的槽位索引。
 * @param modifier
 * %en LootItemFunction to apply
 * %zh 要应用的 LootItemFunction。
 */
fun modifyBlockItem(pos: BlockPos, slot: Int, modifier: LootItemFunction) {
    val container = requireServer().overworld().getBlockEntity(pos)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $pos is not a container")
        return
    }
    if (slot >= 0 && slot < container.containerSize) {
        val itemStack = container.getItem(slot)
        val modifiedItemStack = applyModifier(itemStack, modifier)
        container.setItem(slot, modifiedItemStack)
    } else {
        LOGGER.warn("Slot $slot is out of bounds for container at $pos")
    }
}

/**
 * %en
 * Applies a LootItemFunction to an entity equipment slot.
 *
 * %zh
 * 将 LootItemFunction 应用到实体的装备槽位。
 * @param entity
 * %en Target entity
 * %zh 目标实体。
 * @param slot
 * %en Equipment slot index
 * %zh 装备槽位索引。
 * @param modifier
 * %en LootItemFunction to apply
 * %zh 要应用的 LootItemFunction。
 */
fun modifyEntityItem(entity: Entity, slot: Int, modifier: LootItemFunction) {
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return
    }
    val itemStack = slotAccess.get().copy()
    val modifiedItemStack = applyModifier(itemStack, modifier)
    if (slotAccess.set(modifiedItemStack) && entity is ServerPlayer) {
        entity.containerMenu.broadcastChanges()
    }
}

/**
 * %en
 * Sets an item into a container block slot.
 *
 * %zh
 * 将物品放入容器方块的指定槽位。
 * @param pos
 * %en Block position
 * %zh 方块位置。
 * @param slot
 * %en Slot index
 * %zh 槽位索引。
 * @param itemStack
 * %en ItemStack to set
 * %zh 要设置的 ItemStack。
 */
fun setBlockItem(pos: BlockPos, slot: Int, itemStack: ItemStack) {
    val container = requireServer().overworld().getBlockEntity(pos)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $pos is not a container")
        return
    }
    if (slot >= 0 && slot < container.containerSize) {
        container.setItem(slot, itemStack)
    } else {
        LOGGER.warn("Slot $slot is out of bounds for container at $pos")
    }
}

/**
 * %en
 * Sets an item into an entity slot.
 *
 * %zh
 * 将物品放入实体的指定槽位。
 * @param entity
 * %en Target entity
 * %zh 目标实体。
 * @param slot
 * %en Slot index
 * %zh 槽位索引。
 * @param itemStack
 * %en ItemStack to set
 * %zh 要设置的 ItemStack。
 */
fun setEntityItem(entity: Entity, slot: Int, itemStack: ItemStack) {
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return
    }
    if (slotAccess.set(itemStack) && entity is ServerPlayer) {
        entity.containerMenu.broadcastChanges()
    }
}

/**
 * %en
 * Gets an item from a container block slot.
 *
 * %zh
 * 从容器方块的指定槽位获取物品。
 * @param pos
 * %en Block position
 * %zh 方块位置。
 * @param slot
 * %en Slot index
 * %zh 槽位索引。
 * @return
 * %en or null if invalid
 * %zh 无效时返回 null。
 */
fun getBlockItem(pos: BlockPos, slot: Int): ItemStack? {
    val container = requireServer().overworld().getBlockEntity(pos)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $pos is not a container")
        return null
    }
    return if (slot >= 0 && slot < container.containerSize) {
        container.getItem(slot)
    } else {
        LOGGER.warn("Slot $slot is out of bounds for container at $pos")
        null
    }
}

/**
 * %en
 * Gets an item from an entity slot.
 *
 * %zh
 * 从实体的指定槽位获取物品。
 * @param entity
 * %en Target entity
 * %zh 目标实体。
 * @param slot
 * %en Slot index
 * %zh 槽位索引。
 * @return
 * %en or null if slot missing
 * %zh 槽位不存在时返回 null。
 */
fun getEntityItem(entity: Entity, slot: Int): ItemStack? {
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return null
    }
    return slotAccess.get()
}

/**
 * %en
 * Applies a LootItemFunction to an ItemStack and returns the modified stack.
 *
 * %zh
 * 将 LootItemFunction 应用于 ItemStack，并返回修改后的堆栈。
 * @param itemStack
 * %en Item to modify
 * %zh 要修改的物品。
 * @param modifier
 * %en Function to apply
 * %zh 要应用的函数。
 * @return
 * %en ItemStack (size-limited)
 * %zh 返回受大小限制的 ItemStack。
 */
fun applyModifier(itemStack: ItemStack, modifier: LootItemFunction): ItemStack {
    val params = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
        .create(LootContextParamSets.COMMAND)
    val context = LootContext.Builder(params).create(Optional.empty())
    context.pushVisitedElement(LootContext.createVisitedEntry(modifier))
    val modifiedItemStack = modifier.apply(itemStack, context)
    modifiedItemStack.limitSize(modifiedItemStack.maxStackSize)
    return modifiedItemStack
}
