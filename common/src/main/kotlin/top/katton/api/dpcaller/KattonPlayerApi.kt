@file:Suppress("unused")

package top.katton.api.dpcaller

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerPlayer.RespawnConfig
import net.minecraft.server.players.PlayerList
import net.minecraft.util.Mth
import net.minecraft.world.entity.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.storage.LevelData.RespawnData
import net.minecraft.world.phys.Vec2
import top.katton.api.LOGGER
import java.util.*

/**
 * %en
 * Player management API for player operations.
 *
 * This module provides functions for working with players including:
 * - Player list access
 * - Inventory management
 * - Item operations
 * - Player teleportation
 *
 * %zh
 * 玩家管理 API，用于玩家相关操作。
 * 本模块提供一组处理玩家的函数，包括：
 * - 玩家列表访问
 * - 背包管理
 * - 物品操作
 * - 玩家传送
 */

/**
 * %en
 * List-like access to all online players.
 *
 * %zh
 * 以类似 List 的方式访问所有在线玩家。
 * @property playerList
 * %en The underlying PlayerList
 * %zh 底层 PlayerList。
 */
class KattonPlayerList(
    val playerList: PlayerList
) : List<ServerPlayer> by playerList.players {
    /**
 * %en
 * Find a player by name.
 *
 * %zh
 * 按名称查找玩家。
 */
    operator fun get(name: String): ServerPlayer? {
        return playerList.getPlayer(name)
    }

    /**
 * %en
 * Find a player by UUID.
 *
 * %zh
 * 按 UUID 查找玩家。
 */
    operator fun get(uuid: UUID): ServerPlayer? {
        return playerList.getPlayer(uuid)
    }
}

/**
 * %en
 * Collection of players within a specific level.
 *
 * %zh
 * 指定关卡中的玩家集合。
 * @property level
 * %en The ServerLevel containing the players
 * %zh 包含这些玩家的 ServerLevel。
 */
class KattonLevelPlayerCollection(
    val level: ServerLevel
) : List<ServerPlayer> by level.players {
    /**
 * %en
 * Find a player by UUID in this level.
 *
 * %zh
 * 在当前关卡中按 UUID 查找玩家。
 */
    operator fun get(uuid: UUID): Player? {
        return level.getPlayerByUUID(uuid)
    }
}

/**
 * %en
 * Add an item to a player's inventory.
 *
 * %zh
 * 向玩家背包添加物品。
 * @param player
 * %en the Player to receive the item
 * %zh 接收物品的玩家。
 * @param item
 * %en the Item type to add
 * %zh 要添加的物品类型。
 * @param amount
 * %en the quantity to add
 * %zh 要添加的数量。
 */
fun Player.addItem(item: Item, amount: Int) = giveItem(this, ItemStack(item, amount))

/**
 * %en
 * Clear a player's inventory.
 *
 * %zh
 * 清空玩家背包。
 * @param player
 * %en the Player whose inventory will be cleared
 * %zh 背包将被清空的玩家。
 */
fun clearInventory(player: Player) {
    player.inventory.clearContent()
}


/**
 * %en
 * Set an item into a player's inventory slot.
 *
 * %zh
 * 将物品设置到玩家背包槽位中。
 * @param player
 * %en the Player to modify
 * %zh 要修改的玩家。
 * @param slot
 * %en inventory slot index
 * %zh 背包槽位索引。
 * @param itemStack
 * %en item stack to set
 * %zh 要设置的物品堆栈。
 */
fun setItem(player: Player, slot: Int, itemStack: ItemStack) {
    player.inventory.setItem(slot, itemStack)
    if (player is ServerPlayer) {
        player.inventoryMenu.sendAllDataToRemote()
    }
}


/**
 * %en
 * Get the item from a player's inventory slot.
 *
 * %zh
 * 从玩家背包槽位中获取物品。
 * @param player
 * %en the Player to query
 * %zh 要查询的玩家。
 * @param slot
 * %en inventory slot index
 * %zh 背包槽位索引。
 * @return
 * %en in the slot
 * %zh 返回该槽位中的物品堆。
 */
fun getItem(player: Player, slot: Int): ItemStack {
    return player.inventory.getItem(slot)
}


/**
 * %en
 * Try to give an item stack to a player.
 *
 * %zh
 * 尝试给予玩家一个 ItemStack。
 * @param player
 * %en the Player to receive the item
 * %zh 接收物品的玩家。
 * @param itemStack
 * %en the ItemStack to give
 * %zh 要给予的 ItemStack。
 * @return
 * %en if added to inventory, false if full
 * %zh 如果已添加到背包，则返回 true；如果背包已满，则返回 false。
 */
fun giveItem(player: Player, itemStack: ItemStack): Boolean {
    return player.inventory.add(itemStack)
}



/**
 * %en
 * Check if a player has a specific item type in their inventory.
 *
 * %zh
 * 检查玩家背包中是否有指定物品类型。
 * @param player
 * %en the Player to check
 * %zh 要检查的玩家。
 * @param item
 * %en the Item type to search for
 * %zh 要查找的物品类型。
 * @return
 * %en true if the player has the item, false otherwise
 * %zh 如果玩家持有该物品，则返回 true；否则返回 false。
 */
fun hasItem(player: Player, item: Item): Boolean {
    return player.inventory.hasAnyOf(setOf(item))
}


/**
 * %en
 * Find the slot index of an item in player's inventory.
 *
 * %zh
 * 在玩家背包中查找某种物品所在的槽位索引。
 * @param player
 * %en the Player to search
 * %zh 要搜索的玩家。
 * @param item
 * %en item type to find
 * %zh 要查找的物品类型。
 * @return
 * %en index or -1 if not found
 * %zh 返回槽位索引；未找到时返回 -1。
 */
fun findItem(player: Player, item: Item): Int {
    return player.inventory.findSlotMatchingItem(ItemStack(item))
}


/**
 * %en
 * Remove a count of items from player's inventory.
 *
 * %zh
 * 从玩家背包中移除指定数量的物品。
 * @param player
 * %en the Player to modify
 * %zh 要修改的玩家。
 * @param item
 * %en item type to remove
 * %zh 要移除的物品类型。
 * @param count
 * %en amount to remove
 * %zh 要移除的数量。
 * @return
 * %en true if removal succeeded, false otherwise
 * %zh 如果移除成功则返回 true，否则返回 false。
 */
fun removeItem(player: Player, item: Item, count: Int): Boolean {
    val slot = player.inventory.findSlotMatchingItem(ItemStack(item))
    return if (slot >= 0) {
        val stackInSlot = player.inventory.getItem(slot)
        if (stackInSlot.count >= count) {
            stackInSlot.shrink(count)
            if (player is ServerPlayer) {
                player.inventoryMenu.sendAllDataToRemote()
            }
            true
        } else {
            false
        }
    } else {
        false
    }
}


/**
 * %en
 * Enchant an ItemStack with an enchantment.
 *
 * %zh
 * 使用附魔为 ItemStack 添加魔咒。
 * @param itemStack
 * %en target ItemStack
 * %zh 目标 ItemStack。
 * @param enchantment
 * %en enchantment holder to apply
 * %zh 要应用的附魔 Holder。
 * @param level
 * %en enchantment level
 * %zh 附魔等级。
 */
fun enchant(itemStack: ItemStack, enchantment: Holder<Enchantment>, level: Int) {
    itemStack.enchant(enchantment, level)
}


/**
 * %en
 * Enchant the item in an entity's main hand if present.
 *
 * %zh
 * 如果实体主手有物品，则为该物品添加魔咒。
 * @param entity
 * %en target LivingEntity
 * %zh 目标 LivingEntity。
 * @param enchantment
 * %en enchantment holder to apply
 * %zh 要应用的附魔 Holder。
 * @param level
 * %en enchantment level
 * %zh 附魔等级。
 */
fun enchantMainHand(entity: LivingEntity, enchantment: Holder<Enchantment>, level: Int) {
    val stack = entity.mainHandItem
    if (!stack.isEmpty) {
        stack.enchant(enchantment, level)
    }
}


/**
 * %en
 * Give experience points to a player.
 *
 * %zh
 * 给予玩家经验点数。
 * @param player
 * %en target Player
 * %zh 目标 Player。
 * @param points
 * %en experience points to add
 * %zh 要增加的经验点数。
 */
fun addXpPoints(player: Player, points: Int) {
    player.giveExperiencePoints(points)
}


/**
 * %en
 * Give experience levels to a player.
 *
 * %zh
 * 给予玩家经验等级。
 * @param player
 * %en target Player
 * %zh 目标 Player。
 * @param levels
 * %en levels to add
 * %zh 要增加的等级数。
 */
fun addXpLevels(player: Player, levels: Int) {
    player.giveExperienceLevels(levels)
}


/**
 * %en
 * Set a player's experience level.
 *
 * %zh
 * 设置玩家的经验等级。
 * @param player
 * %en target Player
 * %zh 目标 Player。
 * @param level
 * %en level value to set
 * %zh 要设置的等级值。
 */
fun setXpLevel(player: Player, level: Int) {
    player.experienceLevel = level
}


/**
 * %en
 * Get a player's experience level.
 *
 * %zh
 * 获取玩家的经验等级。
 * @param player
 * %en target Player
 * %zh 目标 Player。
 * @return
 * %en experience level
 * %zh 返回经验等级。
 */
fun getXpLevel(player: Player): Int {
    return player.experienceLevel
}


/**
 * %en
 * Get a player's experience progress (fraction).
 *
 * %zh
 * 获取玩家经验进度（小数）。
 * @param player
 * %en target Player
 * %zh 目标 Player。
 * @return
 * %en progress as float (0..1)
 * %zh 返回经验进度，范围为 0 到 1。
 */
fun getXpProgress(player: Player): Float {
    return player.experienceProgress
}


/**
 * %en
 * Set spawn point for a collection of players.
 *
 * %zh
 * 为一组玩家设置重生点。
 * @param player
 * %en collection of ServerPlayer to set
 * %zh 要设置的 ServerPlayer 集合。
 * @param level
 * %en server level providing the dimension
 * %zh 提供维度信息的服务端关卡。
 * @param pos
 * %en respawn position
 * %zh 重生位置。
 * @param rot
 * %en rotation vector (pitch,x / yaw,y)
 * %zh 旋转向量（x 为 pitch，y 为 yaw）。
 */
fun spawnPoint(player: MutableCollection<ServerPlayer>, level: ServerLevel, pos: BlockPos, rot: Vec2){
    val resourceKey = level.dimension()
    val f = Mth.wrapDegrees(rot.y)
    val g = Mth.clamp(rot.x, -90.0f, 90.0f)

    for (serverPlayer in player) {
        serverPlayer.setRespawnPosition(
            RespawnConfig(RespawnData.of(resourceKey, pos, f, g), true),
            false
        )
    }
}


/**
 * %en
 * Set the world spawn and respawn orientation for a level.
 *
 * %zh
 * 设置关卡的世界出生点和重生朝向。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param blockPos
 * %en spawn position
 * %zh 生成位置。
 * @param rot
 * %en rotation vector (pitch,x / yaw,y)
 * %zh 旋转向量（x 为 pitch，y 为 yaw）。
 */
fun setWorldSpawn(level: ServerLevel, blockPos: BlockPos, rot: Vec2) {
    val f = rot.y
    val g = rot.x
    val respawnData = RespawnData.of(level.dimension(), blockPos, f, g)
    level.respawnData = respawnData
}


/**
 * %en
 * Make a player spectate a target entity.
 *
 * %zh
 * 让玩家观战目标实体。
 * @param player
 * %en spectator ServerPlayer
 * %zh 作为观战者的 ServerPlayer。
 * @param target
 * %en entity to spectate, or null to stop
 * %zh 要观战的实体；传入 null 表示停止观战。
 * @return
 * %en true if spectating succeeded, false otherwise
 * %zh 如果观战成功则返回 true，否则返回 false。
 */
fun spectate(player: ServerPlayer, target: Entity?): Boolean {
    if(player == target){
        LOGGER.error("${player.displayName} can't spectate itself")
        return false
    }else if(!player.isSpectator){
        LOGGER.error("${player.displayName} is not in spectator mode")
        return false
    }else if(target != null && target.type.clientTrackingRange() == 0){
        LOGGER.error("${target.displayName} cannot be spectated")
        return false
    }else {
        player.setCamera(target)
        return true
    }
}
