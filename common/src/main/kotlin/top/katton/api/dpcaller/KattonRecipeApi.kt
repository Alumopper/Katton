@file:Suppress("unused")

package top.katton.api.dpcaller

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.crafting.RecipeHolder

/**
 * Recipe management API for player recipe operations.
 *
 * This module provides functions for managing player recipe knowledge,
 * including awarding and revoking recipe advancements.
 */

/**
 * Give recipe advancements to players.
 *
 * @param players target players
 * @param recipes collection of recipes to award
 */
fun giveRecipes(players: Collection<ServerPlayer>, recipes: Collection<RecipeHolder<*>>){
    for(player in players){
        player.awardRecipes(recipes)
    }
}

/**
 * Take recipe advancements from players.
 *
 * @param players target players
 * @param recipes recipes to revoke
 */
fun takeRecipes(players: Collection<ServerPlayer>, recipes: Collection<RecipeHolder<*>>){
    for(player in players){
        player.resetRecipes(recipes)
    }
}


