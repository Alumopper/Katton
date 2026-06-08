@file:Suppress("unused")

package top.katton.api.dpcaller

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.crafting.RecipeHolder

/**
 * %en
 * Recipe management API for player recipe operations.
 *
 * This module provides functions for managing player recipe knowledge,
 * including awarding and revoking recipe advancements.
 *
 * %zh
 * 面向玩家配方操作的配方管理 API。
 * 本模块提供用于管理玩家配方知识的函数，
 * 包括授予和撤销配方进度。
 */

/**
 * %en
 * Give recipe advancements to players.
 *
 * %zh
 * 向玩家授予配方进度。
 * @param players
 * %en target players
 * %zh 目标玩家。
 * @param recipes
 * %en collection of recipes to award
 * %zh 要授予的配方集合。
 */
fun giveRecipes(players: Collection<ServerPlayer>, recipes: Collection<RecipeHolder<*>>){
    for(player in players){
        player.awardRecipes(recipes)
    }
}

/**
 * %en
 * Take recipe advancements from players.
 *
 * %zh
 * 从玩家处撤销配方进度。
 * @param players
 * %en target players
 * %zh 目标玩家。
 * @param recipes
 * %en recipes to revoke
 * %zh 要撤销的配方集合。
 */
fun takeRecipes(players: Collection<ServerPlayer>, recipes: Collection<RecipeHolder<*>>){
    for(player in players){
        player.resetRecipes(recipes)
    }
}
