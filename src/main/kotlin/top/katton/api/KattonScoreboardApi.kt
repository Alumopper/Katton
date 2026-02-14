@file:Suppress("unused")

package top.katton.api

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.scores.*
import net.minecraft.world.scores.criteria.ObjectiveCriteria

operator fun Scoreboard.get(target: ScoreHolder, objective: Objective): Int? =
    scoreboard.getPlayerScoreInfo(target, objective)?.value()

operator fun Scoreboard.set(target: ScoreHolder, objective: Objective, value: Int) =
    scoreboard.getOrCreatePlayerScore(target, objective).set(value)

fun fake(name: String): ScoreHolder = ScoreHolder.forNameOnly(name)

class KattonScoreHolderScoreCollection(
    val scoreboard: Scoreboard,
    val scoreHolder: ScoreHolder
) {
    operator fun get(objective: Objective): Int? {
        return scoreboard.getPlayerScoreInfo(scoreHolder, objective)?.value()
    }

    operator fun set(objective: Objective, value: Int) {
        scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(value)
    }
}

val ScoreHolder.scores: KattonScoreHolderScoreCollection
    get() = KattonScoreHolderScoreCollection(scoreboard, this)


/**
 * Get an objective by name.
 *
 * @param name objective name
 * @return Objective or null if not found
 */
fun getObjective(name: String): Objective? = scoreboard.getObjective(name)


/**
 * Get or create a scoreboard Objective.
 *
 * @param name objective name
 * @param displayName display name component for the objective
 * @param criteria objective criteria
 * @param renderType render type for the objective
 * @param displayAutoUpdate whether the display auto-updates
 * @param numberFormat optional number format
 * @return existing or newly created Objective
 */
fun getOrCreateObjective(
    name: String,
    displayName: Component = Component.literal(name),
    criteria: ObjectiveCriteria = ObjectiveCriteria.DUMMY,
    renderType: ObjectiveCriteria.RenderType = ObjectiveCriteria.RenderType.INTEGER,
    displayAutoUpdate: Boolean = false,
    numberFormat: NumberFormat? = null
): Objective {
    val board = scoreboard
    val existed = board.getObjective(name)
    if (existed != null) return existed
    return board.addObjective(name, criteria, displayName, renderType, displayAutoUpdate, numberFormat)
}


/**
 * Set a score for a target identified by name.
 *
 * @param target target name
 * @param objective objective to set
 * @param value score value to set
 */
fun setScore(target: String, objective: Objective, value: Int) = setScore(ScoreHolder.forNameOnly(target), objective, value)


/**
 * Set a score for an Entity.
 *
 * @param target target Entity
 * @param objective objective to set
 * @param value score value
 */
fun setScore(target: Entity, objective: Objective, value: Int) = setScore(target as ScoreHolder, objective, value)


/**
 * Set a score for a ScoreHolder.
 *
 * @param target target ScoreHolder
 * @param objective objective to set
 * @param value score value
 */
fun setScore(target: ScoreHolder, objective: Objective, value: Int) {
    val score = scoreboard.getOrCreatePlayerScore(target, objective)
    score.set(value)
}


/**
 * Add delta to a target's score by name.
 *
 * @param target target name
 * @param objective objective to modify
 * @param delta amount to add
 */
fun addScore(target: String, objective: Objective, delta: Int) = addScore(ScoreHolder.forNameOnly(target), objective, delta)


/**
 * Add delta to a target Entity's score.
 *
 * @param target target Entity
 * @param objective objective to modify
 * @param delta amount to add
 */
fun addScore(target: Entity, objective: Objective, delta: Int) = addScore(target as ScoreHolder, objective, delta)


/**
 * Add delta to a ScoreHolder's score.
 *
 * @param target target ScoreHolder
 * @param objective objective to modify
 * @param delta amount to add
 */
fun addScore(target: ScoreHolder, objective: Objective, delta: Int) {
    val score = scoreboard.getOrCreatePlayerScore(target, objective)
    score.add(delta)
}


/**
 * Get a score by target name.
 *
 * @param target target name
 * @param objective objective to query
 * @return score value or null if not present
 */
fun getScore(target: String, objective: Objective): Int? = getScore(ScoreHolder.forNameOnly(target), objective)


/**
 * Get a score by Entity.
 *
 * @param target target Entity
 * @param objective objective to query
 * @return score value or null if not present
 */
fun getScore(target: Entity, objective: Objective): Int? = getScore(target as ScoreHolder, objective)


/**
 * Get a score for a ScoreHolder.
 *
 * @param target target ScoreHolder
 * @param objective objective to query
 * @return score value or null if not present
 */
fun getScore(target: ScoreHolder, objective: Objective): Int? {
    val readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(target, objective)
    return readOnlyScoreInfo?.value()
}


/**
 * Reset a target's score by name.
 *
 * @param target target name
 * @param objective objective to reset
 */
fun resetScore(target: String, objective: Objective) = resetScore(ScoreHolder.forNameOnly(target), objective)


/**
 * Reset a target's score by Entity.
 *
 * @param target target Entity
 * @param objective objective to reset
 */
fun resetScore(target: Entity, objective: Objective) = resetScore(target as ScoreHolder, objective)


/**
 * Reset a ScoreHolder's score.
 *
 * @param target target ScoreHolder
 * @param objective objective to reset
 */
fun resetScore(target: ScoreHolder, objective: Objective) {
    scoreboard.resetSinglePlayerScore(target, objective)
}


/**
 * Remove a collection of players from any teams.
 *
 * @param members collection of ScoreHolder members to remove from teams
 */
fun leaveTeam(members: Collection<ScoreHolder>){
    val scoreboard = requireServer().scoreboard
    for(member in members){
        scoreboard.removePlayerFromTeam(member.scoreboardName)
    }
}


/**
 * Add members to a PlayerTeam.
 *
 * @param team PlayerTeam to join
 * @param members collection of ScoreHolder to add
 */
fun joinTeam(team: PlayerTeam, members: Collection<ScoreHolder>){
    val scoreboard = requireServer().scoreboard
    for(member in members){
        scoreboard.addPlayerToTeam(member.scoreboardName, team)
    }
}


/**
 * Empty a player team of all members.
 *
 * @param team PlayerTeam to empty
 */
fun emptyTeam(team: PlayerTeam) {
    val scoreboard = requireServer().scoreboard
    for(member in team.players){
        scoreboard.removePlayerFromTeam(member, team)
    }
}


/**
 * Delete a player team from the scoreboard.
 *
 * @param team team to delete
 */
fun deleteTeam(team: PlayerTeam) {
    val scoreboard = requireServer().scoreboard
    scoreboard.removePlayerTeam(team)
}


/**
 * Create a team if it does not exist.
 *
 * @param name team name
 * @param displayName display name component for the team
 */
fun createTeam(name: String, displayName: Component = Component.literal(name)){
    val scoreboard = requireServer().scoreboard
    if(scoreboard.getPlayerTeam(name) != null) return
    val team = scoreboard.addPlayerTeam(name)
    team.displayName = displayName
}


/**
 * Get a team by name.
 *
 * @param name team name
 * @return PlayerTeam or null if not found
 */
fun getTeam(name: String): PlayerTeam? {
    return requireServer().scoreboard.getPlayerTeam(name)
}


/**
 * Add a trigger score value for a player on a trigger objective.
 *
 * @param serverPlayer player to modify
 * @param objective trigger objective
 * @param i amount to add
 */
fun addTriggerValue(serverPlayer: ServerPlayer, objective: Objective, i: Int){
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.add(i)
}


/**
 * Set a trigger score value for a player on a trigger objective.
 *
 * @param serverPlayer player to modify
 * @param objective trigger objective
 * @param i value to set
 */
fun setTriggerValue(serverPlayer: ServerPlayer, objective: Objective, i: Int){
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.set(i)
}


/**
 * Simple trigger: increment a trigger objective for a player by 1.
 *
 * @param serverPlayer player to trigger
 * @param objective trigger objective
 */
fun simpleTrigger(serverPlayer: ServerPlayer, objective: Objective) {
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.add(1)
}


/**
 * Internal helper to get and lock a trigger score.
 *
 * @param scoreboard scoreboard instance
 * @param scoreHolder target score holder
 * @param objective trigger objective
 * @return ScoreAccess if available and locked, null otherwise
 */
private fun getTriggerScore(scoreboard: Scoreboard, scoreHolder: ScoreHolder, objective: Objective): ScoreAccess? {
    if (objective.criteria !== ObjectiveCriteria.TRIGGER) {
        LOGGER.error("You can only trigger objectives that are 'trigger' type")
        return null
    } else {
        val readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective)
        if (readOnlyScoreInfo != null && !readOnlyScoreInfo.isLocked) {
            val scoreAccess = scoreboard.getOrCreatePlayerScore(scoreHolder, objective)
            scoreAccess.lock()
            return scoreAccess
        } else {
            LOGGER.error("You cannot trigger this objective yet")
            return null
        }
    }
}


