@file:Suppress("unused")

package top.katton.api.dpcaller

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
import top.katton.api.LOGGER
import top.katton.api.requireServer
import kotlin.math.min

/**
 * %en
 * Loot table API for generating item drops.
 *
 * This module provides functions for working with loot tables including:
 * - Block drop generation
 * - Entity kill drops
 * - Chest loot generation
 * - Loot function application
 *
 * %zh
 * 用于生成物品掉落的战利品表 API。
 * 本模块提供一组处理战利品表的函数，包括：
 * - 生成方块掉落
 * - 生成实体击杀掉落
 * - 生成容器战利品
 * - 应用战利品函数
 */

/**
 * %en
 * Get drops for a block as if it were broken with a tool.
 *
 * %zh
 * 获取方块在使用工具破坏时的掉落物。
 * @param pos
 * %en block position
 * %zh 方块位置。
 * @param tool
 * %en tool ItemStack used to break the block
 * %zh 用于破坏方块的工具 ItemStack。
 * @return
 * %en of ItemStack drops
 * %zh 返回掉落的 ItemStack 列表。
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
 * %en
 * Get drops for an entity as if it were killed.
 *
 * %zh
 * 获取实体在被击杀时会掉落的物品。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 * @param killer
 * %en optional killer entity (may influence drops)
 * %zh 可选的击杀实体，可能影响掉落结果。
 * @return
 * %en of ItemStack drops
 * %zh 返回掉落的 ItemStack 列表。
 */
fun dropKillLoot(entity: Entity, killer: Entity?): List<ItemStack> {
    if(entity.lootTable.isEmpty){
        LOGGER.warn("Entity ${entity.displayName.string} has no loot table")
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
 * %en
 * Generate chest loot from a LootTable.
 *
 * %zh
 * 从 LootTable 生成容器战利品。
 * @param lootTable
 * %en LootTable to roll
 * %zh 要抽取的 LootTable。
 * @return
 * %en of generated ItemStack
 * %zh 返回生成的 ItemStack 列表。
 */
fun dropChestLoot(lootTable: LootTable): List<ItemStack> {
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
    val params = builder.create(LootContextParamSets.CHEST)
    return lootTable.getRandomItems(params)
}


/**
 * %en
 * Generate fishing loot from a LootTable.
 *
 * %zh
 * 从 LootTable 生成钓鱼战利品。
 * @param lootTable
 * %en LootTable to roll
 * %zh 要抽取的 LootTable。
 * @param pos
 * %en origin position for loot context
 * %zh 战利品上下文的原点位置。
 * @param tool
 * %en tool ItemStack used
 * %zh 使用的工具 ItemStack。
 * @return
 * %en of generated ItemStack
 * %zh 返回生成的 ItemStack 列表。
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
 * %en
 * Attempt to deposit item stacks into a container block.
 *
 * %zh
 * 尝试将 ItemStack 放入容器方块。
 * @param block
 * %en container block position
 * %zh 容器方块的位置。
 * @param itemStacks
 * %en list of ItemStack to deposit (may be modified)
 * %zh 要放入的 ItemStack 列表，可能会被修改。
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
 * %en
 * Replace a range of slots in a container block with given item stacks.
 *
 * %zh
 * 使用给定的 ItemStack 替换容器方块中的一段槽位。
 * @param block
 * %en container position
 * %zh 容器位置。
 * @param i
 * %en start slot index
 * %zh 起始槽位索引。
 * @param j
 * %en number of slots to replace
 * %zh 要替换的槽位数量。
 * @param itemStacks
 * %en list of ItemStacks to place (shorter lists fill with empty)
 * %zh 要放置的 ItemStack 列表，较短的列表会以空物品补足。
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
 * %en
 * Give item stacks to players (adds copies to inventory).
 *
 * %zh
 * 将 ItemStack 给予玩家，并把副本加入背包。
 * @param player
 * %en target ServerPlayer
 * %zh 目标 ServerPlayer。
 * @param itemStacks
 * %en list of ItemStack to give
 * %zh 要给予的 ItemStack 列表。
 */
fun dropToPlayer(player: ServerPlayer, itemStacks: List<ItemStack>) {
    for(item in itemStacks){
        player.inventory.add(item.copy())
    }
}


/**
 * %en
 * Set item stacks into entity slots.
 *
 * %zh
 * 将 ItemStack 设置到实体槽位中。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 * @param i
 * %en starting slot index
 * %zh 起始槽位索引。
 * @param j
 * %en number of slots to set
 * %zh 要设置的槽位数量。
 * @param itemStacks
 * %en list of ItemStacks to set
 * %zh 要设置的 ItemStack 列表。
 */
fun dropToEntity(entity: Entity, i: Int, j: Int, itemStacks: List<ItemStack>) {
    for(k in 0 until j){
        val itemStack = if(k < itemStacks.size) itemStacks[k] else ItemStack.EMPTY
        val slotAccess = entity.getSlot(i + k)
        slotAccess?.set(itemStack.copy())
    }
}


/**
 * %en
 * Drop item stacks into the world at a position.
 *
 * %zh
 * 将 ItemStack 掉落到世界中的指定位置。
 * @param level
 * %en world level
 * %zh 世界维度。
 * @param pos
 * %en drop position
 * %zh 掉落位置。
 * @param itemStacks
 * %en list of ItemStack to spawn
 * %zh 要生成的 ItemStack 列表。
 */
fun dropTo(level: Level, pos: Vec3, itemStacks: List<ItemStack>) {
    for(item in itemStacks){
        val itemEntity = ItemEntity(level, pos.x, pos.y, pos.z, item.copy())
        itemEntity.setDefaultPickUpDelay()
        level.addFreshEntity(itemEntity)
    }
}
