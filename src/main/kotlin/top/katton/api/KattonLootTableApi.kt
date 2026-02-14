@file:Suppress("unused")

package top.katton.api

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import kotlin.math.min


/**
 * Get drops for a block as if it were broken with a tool.
 *
 * @param pos block position
 * @param tool tool ItemStack used to break the block
 * @return list of ItemStack drops
 */
fun dropBlockLoot(pos: BlockPos, tool: ItemStack): List<ItemStack> {
    val blockState = requireServer().overworld().getBlockState(pos)
    val blockEntity = requireServer().overworld().getBlockEntity(pos)
    if(blockState.block.lootTable.isEmpty){
        LOGGER.warn("Block at $pos has no loot table")
        return emptyList()
    }
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.BLOCK_STATE, blockState)
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withParameter(LootContextParams.TOOL, tool)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
        .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
    return blockState.getDrops(builder)
}


/**
 * Get drops for an entity as if it were killed.
 *
 * @param entity target entity
 * @param killer optional killer entity (may influence drops)
 * @return list of ItemStack drops
 */
fun dropKillLoot(entity: Entity, killer: Entity?): List<ItemStack> {
    if(entity.lootTable.isEmpty){
        LOGGER.warn("Entity ${entity.displayName?.string} has no loot table")
        return emptyList()
    }
    val lootTableKey = entity.lootTable.get()
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, entity.position())
        .withParameter(LootContextParams.THIS_ENTITY, entity)
        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
        .withParameter(LootContextParams.DAMAGE_SOURCE, entity.damageSources().magic())
    if(killer is Player) builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
    val params = builder.create(LootContextParamSets.ENTITY)
    val lootTable = requireServer().reloadableRegistries().getLootTable(lootTableKey)
    return lootTable.getRandomItems(params)
}


/**
 * Generate chest loot from a LootTable.
 *
 * @param lootTable LootTable to roll
 * @return list of generated ItemStack
 */
fun dropChestLoot(lootTable: LootTable): List<ItemStack> {
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
    val params = builder.create(LootContextParamSets.CHEST)
    return lootTable.getRandomItems(params)
}


/**
 * Generate fishing loot from a LootTable.
 *
 * @param lootTable LootTable to roll
 * @param pos origin position for loot context
 * @param tool tool ItemStack used
 * @return list of generated ItemStack
 */
fun dropFishingLoot(lootTable: LootTable, pos: BlockPos, tool: ItemStack): List<ItemStack> {
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withParameter(LootContextParams.TOOL, tool)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
    val params = builder.create(LootContextParamSets.FISHING)
    return lootTable.getRandomItems(params)
}


/**
 * Attempt to deposit item stacks into a container block.
 *
 * @param block container block position
 * @param itemStacks list of ItemStack to deposit (may be modified)
 */
fun dropToBlock(block: BlockPos, itemStacks: List<ItemStack>) {
    val container = requireServer().overworld().getBlockEntity(block)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $block is not a container")
        return
    }
    for(item in itemStacks){
        if(item.isEmpty) continue
        var changed = false
        for(i in 0 until container.containerSize){
            val stackInSlot = container.getItem(i)
            if(container.canPlaceItem(i, item)){
                if(stackInSlot.isEmpty){
                    container.setItem(i, item)
                    changed = true
                    break
                }

                if(item.count <= stackInSlot.maxStackSize && ItemStack.isSameItemSameComponents(stackInSlot, item)){
                    val delta = stackInSlot.maxStackSize - stackInSlot.count
                    val toAdd = min(item.count, delta)
                    item.shrink(toAdd)
                    stackInSlot.grow(toAdd)
                    changed = true
                }
            }
            if(changed){
                container.setChanged()
            }
        }
    }
}


/**
 * Replace a range of slots in a container block with given item stacks.
 *
 * @param block container position
 * @param i start slot index
 * @param j number of slots to replace
 * @param itemStacks list of ItemStacks to place (shorter lists fill with empty)
 */
fun dropToBlockReplace(block: BlockPos, i: Int, j: Int, itemStacks: List<ItemStack>) {
    val container = requireServer().overworld().getBlockEntity(block)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $block is not a container")
        return
    }
    if(i >= 0 && i < container.containerSize){
        for(l in 0 until j){
            val m = i + l;
            val itemStack = if(l < itemStacks.size) itemStacks[l] else ItemStack.EMPTY
            if(container.canPlaceItem(l, itemStack)){
                container.setItem(m, itemStack)
            }
        }
    }
}


/**
 * Give item stacks to players (adds copies to inventory).
 *
 * @param player target ServerPlayer
 * @param itemStacks list of ItemStack to give
 */
fun dropToPlayer(player: ServerPlayer, itemStacks: List<ItemStack>) {
    for(item in itemStacks){
        player.inventory.add(item.copy())
    }
}


/**
 * Set item stacks into entity slots.
 *
 * @param entity target entity
 * @param i starting slot index
 * @param j number of slots to set
 * @param itemStacks list of ItemStacks to set
 */
fun dropToEntity(entity: Entity, i: Int, j: Int, itemStacks: List<ItemStack>) {
    for(k in 0 until j){
        val itemStack = if(k < itemStacks.size) itemStacks[k] else ItemStack.EMPTY
        val slotAccess = entity.getSlot(i + k)
        slotAccess?.set(itemStack.copy())
    }
}


/**
 * Drop item stacks into the world at a position.
 *
 * @param level world level
 * @param pos drop position
 * @param itemStacks list of ItemStack to spawn
 */
fun dropTo(level: Level, pos: Vec3, itemStacks: List<ItemStack>) {
    for(item in itemStacks){
        val itemEntity = ItemEntity(level, pos.x, pos.y, pos.z, item.copy())
        itemEntity.setDefaultPickUpDelay()
        level.addFreshEntity(itemEntity)
    }
}

