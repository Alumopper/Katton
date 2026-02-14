@file:Suppress("unused")

package top.katton.api

import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.IpBanListEntry
import net.minecraft.server.players.UserBanListEntry
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRule
import java.util.*


/**
 * Execute a command as the provided command source.
 *
 * @param source the command source to run the command as
 * @param command the command string to execute
 */
fun executeCommand(source: CommandSourceStack, command: String) {
    val srv = source.server
    srv.commands.performPrefixedCommand(source, command)
}


/**
 * Execute a command as the server console.
 *
 * @param command the command string to execute
 */
fun executeCommandAsServer(command: String) {
    val srv = requireServer()
    val source = srv.createCommandSourceStack()
    srv.commands.performPrefixedCommand(source, command)
}


fun findPlayer(player: String): ServerPlayer?{
    return requireServer().playerList.getPlayerByName(player)
}


fun findPlayer(uuid: UUID): ServerPlayer?{
    return requireServer().playerList.getPlayer(uuid)
}


fun findEntities(level: ServerLevel, selector: EntitySelector): List<Entity> {
    return selector.findEntities(requireServer().createCommandSourceStack().withLevel(level))
}


fun findEntity(uuid: UUID): Entity?{
    return requireServer().allLevels.firstNotNullOfOrNull { it.getEntity(uuid) }
}


/**
 * Ban a player by adding them to the server ban list and disconnecting them.
 *
 * @param player the ServerPlayer to ban
 */
fun ban(player: ServerPlayer) {
    val userBanList = requireServer().playerList.bans;
    val nameAndId = player.nameAndId()
    if (!userBanList.isBanned(nameAndId)) {
        val userBanListEntry = UserBanListEntry(
            nameAndId, null, "server", null, null
        )
        userBanList.add(userBanListEntry);
        player.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
    }
}


/**
 * Ban an IP address and disconnect matching players.
 *
 * @param ip IP address string to ban
 */
fun banIp(ip: String) {
    val ipBanList = requireServer().playerList.ipBans;
    if (!ipBanList.isBanned(ip)) {
        val list = requireServer().playerList.getPlayersWithAddress(ip);
        val ipBanListEntry =
            IpBanListEntry(ip, null, "server", null, null)
        ipBanList.add(ipBanListEntry)
        for (serverPlayer in list) {
            serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned"));
        }
    }
}


/**
 * De-op a player (remove operator status).
 *
 * @param player ServerPlayer to de-op
 */
fun deop(player: ServerPlayer) {
    requireServer().playerList.deop(player.nameAndId())
}


/**
 * Op a player (grant operator status).
 *
 * @param player ServerPlayer to op
 */
fun op(player: ServerPlayer) {
    requireServer().playerList.op(player.nameAndId())
}


/**
 * Set server difficulty.
 *
 * @param difficulty new Difficulty
 * @param ignoreLock whether to ignore difficulty lock
 */
fun setDifficulty(difficulty: Difficulty, ignoreLock: Boolean = true) {
    requireServer().setDifficulty(difficulty, ignoreLock)
}


/**
 * Get current overworld difficulty.
 *
 * @return current Difficulty
 */
fun getDifficulty(): Difficulty {
    return requireServer().overworld().difficulty
}

// ==================== Effect ====================


/**
 * Run a function (data pack function) with an optional command source.
 *
 * @param id function identifier
 * @param source command source to use (defaults to server)
 */
fun runFunction(id: Identifier, source: CommandSourceStack = requireServer().createCommandSourceStack()) {
    requireServer().functions.get(id).ifPresent {
        requireServer().functions.execute(it, source)
    }
}


/**
 * Set a player's game mode.
 *
 * @param player target ServerPlayer
 * @param gameMode target GameType
 */
fun setGameMode(player: ServerPlayer, gameMode: GameType) {
    player.setGameMode(gameMode)
}


/**
 * Get a player's current GameType.
 *
 * @param player target ServerPlayer
 * @return current GameType
 */
fun getGameMode(player: ServerPlayer): GameType {
    return player.gameMode.gameModeForPlayer
}


/**
 * Set a game rule value on the server overworld.
 *
 * @param key GameRule key
 * @param value value to set
 */
fun <T : Any> setGameRule(key: GameRule<T>, value: T) {
    try{
        requireServer().overworld().gameRules.set(key, value, requireServer())
    }catch (e: IllegalArgumentException) {
        LOGGER.warn("Failed to set game rule $key to $value", e)
    }
}


/**
 * Get a game rule value from the server overworld.
 *
 * @param key GameRule key
 * @return value of the game rule
 */
fun <T : Any> getGameRule(key: GameRule<T>): T {
    return requireServer().overworld().gameRules.get(key)
}


/**
 * Kick a player with an optional reason component.
 *
 * @param player target Player (ServerPlayer required to disconnect)
 * @param reason disconnect reason component
 */
fun kick(player: Player, reason: Component = Component.translatable("multiplayer.disconnect.kicked")) {
    if (player is ServerPlayer) {
        player.connection.disconnect(reason)
    }
}


