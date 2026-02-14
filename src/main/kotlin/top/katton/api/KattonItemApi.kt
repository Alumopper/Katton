@file:Suppress("unused")

package top.katton.api

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
import java.util.*

var ItemStack.nbt: CompoundTag
    get() = components[DataComponents.CUSTOM_DATA]?.copyTag() ?: CompoundTag()
    set(value) {
        components[DataComponents.CUSTOM_DATA]?.update {
            it.clear()
            it.merge(value)
        }
    }

/**
 * Apply a LootItemFunction modifier to a block container slot.
 *
 * @param pos block position of the container
 * @param slot slot index to modify
 * @param modifier LootItemFunction to apply
 */
fun modifyBlockItem(pos: BlockPos, slot: Int, modifier: LootItemFunction){
    val container = requireServer().overworld().getBlockEntity(pos)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $pos is not a container")
        return
    }
    if(slot >= 0 && slot < container.containerSize){
        val itemStack = container.getItem(slot)
        val modifiedItemStack = applyModifier(itemStack, modifier)
        container.setItem(slot, modifiedItemStack)
    }else{
        LOGGER.warn("Slot $slot is out of bounds for container at $pos")
    }
}


/**
 * Apply a LootItemFunction to an entity equipment slot.
 *
 * @param entity target entity
 * @param slot equipment slot index
 * @param modifier LootItemFunction to apply
 */
fun modifyEntityItem(entity: Entity, slot: Int, modifier: LootItemFunction){
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return
    }
    val itemStack = slotAccess.get().copy()
    val modifiedItemStack = applyModifier(itemStack, modifier)
    if(slotAccess.set(modifiedItemStack) && entity is ServerPlayer){
        entity.containerMenu.broadcastChanges()
    }
}


/**
 * Set an item into a container block slot.
 *
 * @param pos block position
 * @param slot slot index
 * @param itemStack ItemStack to set
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
 * Set an item into an entity slot.
 *
 * @param entity target entity
 * @param slot slot index
 * @param itemStack ItemStack to set
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
 * Get an item from a container block slot.
 *
 * @param pos block position
 * @param slot slot index
 * @return ItemStack or null if invalid
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
 * Get an item from an entity slot.
 *
 * @param entity target entity
 * @param slot slot index
 * @return ItemStack or null if slot missing
 */
fun getEntityItem(entity: Entity, slot: Int): ItemStack? {
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return null
    }
    return slotAccess.get()
}


/**
 * Apply a LootItemFunction to an ItemStack and return the modified stack.
 *
 * @param itemStack item to modify
 * @param modifier function to apply
 * @return modified ItemStack (size-limited)
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
