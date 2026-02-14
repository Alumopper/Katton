@file:Suppress("unused")

package top.katton.api

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
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.LevelData.RespawnData
import net.minecraft.world.phys.Vec2
import java.util.*

class KattonPlayerList(
    val playerList: PlayerList
) : List<ServerPlayer> by playerList.players {
    operator fun get(name: String): ServerPlayer? {
        return playerList.getPlayer(name)
    }

    operator fun get(uuid: UUID): ServerPlayer? {
        return playerList.getPlayer(uuid)
    }
}


class KattonLevelPlayerCollection(
    val level: ServerLevel
) : List<ServerPlayer> by level.players {
    operator fun get(uuid: UUID): Player? {
        return level.getPlayerByUUID(uuid)
    }
}
fun Player.addItem(item: Item, amount: Int) = giveItem(this, ItemStack(item, amount))

// 已经提供了 addItem(itemStack: ItemStack)
fun Player.hasItem(item: Item) = hasItem(this, item)
fun Player.removeItem(item: Item, amount: Int) = removeItem(this, item, amount)


/**
 * Clear a player's inventory.
 *
 * @param player the Player whose inventory will be cleared
 */
fun clearInventory(player: Player) {
    player.inventory.clearContent()
}


/**
 * Set an item into a player's inventory slot.
 *
 * @param player the Player to modify
 * @param slot inventory slot index
 * @param itemStack item stack to set
 */
fun setItem(player: Player, slot: Int, itemStack: ItemStack) {
    player.inventory.setItem(slot, itemStack)
    if (player is ServerPlayer) {
        player.inventoryMenu.sendAllDataToRemote()
    }
}


/**
 * Get the item from a player's inventory slot.
 *
 * @param player the Player to query
 * @param slot inventory slot index
 * @return ItemStack in the slot
 */
fun getItem(player: Player, slot: Int): ItemStack {
    return player.inventory.getItem(slot)
}


/**
 * Try to give an item stack to a player.
 *
 * @param player the Player to receive the item
 * @param itemStack the ItemStack to give
 * @return true if added to inventory, false if full
 */
fun giveItem(player: Player, itemStack: ItemStack): Boolean {
    return player.inventory.add(itemStack)
}



fun hasItem(player: Player, item: Item): Boolean {
    return player.inventory.hasAnyOf(setOf(item))
}


/**
 * Find the slot index of an item in player's inventory.
 *
 * @param player the Player to search
 * @param item item type to find
 * @return slot index or -1 if not found
 */
fun findItem(player: Player, item: Item): Int {
    return player.inventory.findSlotMatchingItem(ItemStack(item))
}


/**
 * Remove a count of items from player's inventory.
 *
 * @param player the Player to modify
 * @param item item type to remove
 * @param count amount to remove
 * @return true if removal succeeded, false otherwise
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
 * Enchant an ItemStack with an enchantment.
 *
 * @param itemStack target ItemStack
 * @param enchantment enchantment holder to apply
 * @param level enchantment level
 */
fun enchant(itemStack: ItemStack, enchantment: Holder<Enchantment>, level: Int) {
    itemStack.enchant(enchantment, level)
}


/**
 * Enchant the item in an entity's main hand if present.
 *
 * @param entity target LivingEntity
 * @param enchantment enchantment holder to apply
 * @param level enchantment level
 */
fun enchantMainHand(entity: LivingEntity, enchantment: Holder<Enchantment>, level: Int) {
    val stack = entity.mainHandItem
    if (!stack.isEmpty) {
        stack.enchant(enchantment, level)
    }
}


/**
 * Give experience points to a player.
 *
 * @param player target Player
 * @param points experience points to add
 */
fun addXpPoints(player: Player, points: Int) {
    player.giveExperiencePoints(points)
}


/**
 * Give experience levels to a player.
 *
 * @param player target Player
 * @param levels levels to add
 */
fun addXpLevels(player: Player, levels: Int) {
    player.giveExperienceLevels(levels)
}


/**
 * Set a player's experience level.
 *
 * @param player target Player
 * @param level level value to set
 */
fun setXpLevel(player: Player, level: Int) {
    player.experienceLevel = level
}


/**
 * Get a player's experience level.
 *
 * @param player target Player
 * @return current experience level
 */
fun getXpLevel(player: Player): Int {
    return player.experienceLevel
}


/**
 * Get a player's experience progress (fraction).
 *
 * @param player target Player
 * @return experience progress as float (0..1)
 */
fun getXpProgress(player: Player): Float {
    return player.experienceProgress
}


/**
 * Set spawn point for a collection of players.
 *
 * @param player collection of ServerPlayer to set
 * @param level server level for dimension
 * @param pos respawn position
 * @param rot rotation vector (pitch,x / yaw,y)
 */
fun spawnPoint(player: MutableCollection<ServerPlayer>, level: ServerLevel, pos: BlockPos, rot: Vec2){
    val resourceKey = level.dimension()
    val f = Mth.wrapDegrees(rot.y)
    val g = Mth.clamp(rot.x, -90.0f, 90.0f)

    for (serverPlayer in player) {
        serverPlayer.setRespawnPosition(
            RespawnConfig(LevelData.RespawnData.of(resourceKey, pos, f, g), true),
            false
        )
    }
}


/**
 * Set the world spawn and respawn orientation for a level.
 *
 * @param level server level
 * @param blockPos spawn position
 * @param rot rotation vector (pitch,x / yaw,y)
 */
fun setWorldSpawn(level: ServerLevel, blockPos: BlockPos, rot: Vec2) {
    val f = rot.y
    val g = rot.x
    val respawnData = RespawnData.of(level.dimension(), blockPos, f, g)
    level.respawnData = respawnData
}


/**
 * Make a player spectate a target entity.
 *
 * @param player spectator ServerPlayer
 * @param target entity to spectate, or null to stop
 * @return true if spectation succeeded, false otherwise
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

