@file:Suppress("unused")

package top.katton.api.dpcaller

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
import net.minecraft.world.level.storage.CommandStorage
import net.minecraft.world.scores.Scoreboard
import top.katton.api.LOGGER
import top.katton.api.requireServer
import java.util.*

/**
 * %en
 * Server management API providing access to server-level operations.
 *
 * This module provides functions for server administration including:
 * - Player management (finding, banning, kicking, operator status)
 * - Command execution
 * - Game rules and difficulty
 * - Server storage and scoreboard access
 *
 * %zh
 * 面向服务器级操作的服务器管理 API。
 * 本模块提供用于服务器管理的函数，包括：
 * - 玩家管理（查找、封禁、踢出、设置管理员权限）
 * - 命令执行
 * - 游戏规则和难度
 * - 服务器存储和计分板访问
 */

/**
 * %en
 * Access to all online players.
 *
 * %zh
 * 访问所有在线玩家。
 */
val players: KattonPlayerList
    get() = KattonPlayerList(requireServer().playerList)

/**
 * %en
 * Access to all entities across all levels.
 *
 * %zh
 * 访问所有维度中的全部实体。
 */
val entities: KattonServerEntityCollection
    get() = KattonServerEntityCollection(requireServer())

/**
 * %en
 * Server command storage for persistent data.
 *
 * %zh
 * 用于保存持久数据的服务器命令存储。
 */
val storage: CommandStorage
    get() = requireServer().commandStorage

/**
 * %en
 * Server scoreboard instance.
 *
 * %zh
 * 服务器计分板实例。
 */
val scoreboard: Scoreboard
    get() = requireServer().scoreboard

/**
 * %en
 * Current server difficulty setting.
 *
 * %zh
 * 当前服务器难度设置。
 */
var difficulty: Difficulty
    get() = requireServer().overworld().difficulty
    set(value) {
        setDifficulty(difficulty, true)
    }

/**
 * %en
 * Execute a command string.
 *
 * %zh
 * 执行一条命令字符串。
 * @param command
 * %en the command string to execute
 * %zh 要执行的命令字符串。
 */
fun execute(command: String) = executeCommandAsServer(command)

/**
 * %en
 * Execute a command as the provided command source.
 *
 * %zh
 * 以指定的命令源执行命令。
 * @param source
 * %en the command source to run the command as
 * %zh 用作执行主体的命令源。
 * @param command
 * %en the command string to execute
 * %zh 要执行的命令字符串。
 */
fun executeCommand(source: CommandSourceStack, command: String) {
    val srv = source.server
    srv.commands.performPrefixedCommand(source, command)
}


/**
 * %en
 * Execute a command as the server console.
 *
 * %zh
 * 以服务器控制台身份执行命令。
 * @param command
 * %en the command string to execute
 * %zh 要执行的命令字符串。
 */
fun executeCommandAsServer(command: String) {
    val srv = requireServer()
    val source = srv.createCommandSourceStack()
    srv.commands.performPrefixedCommand(source, command)
}

/**
 * %en
 * Find a player by name.
 *
 * %zh
 * 根据名称查找玩家。
 * @param player
 * %en the player name to search for
 * %zh 要查找的玩家名称。
 * @return
 * %en ServerPlayer if found, null otherwise
 * %zh 找到则返回 ServerPlayer，否则返回 null。
 */
fun findPlayer(player: String): ServerPlayer?{
    return requireServer().playerList.getPlayerByName(player)
}

/**
 * %en
 * Find a player by UUID.
 *
 * %zh
 * 根据 UUID 查找玩家。
 * @param uuid
 * %en the player UUID to search for
 * %zh 要查找的玩家 UUID。
 * @return
 * %en ServerPlayer if found, null otherwise
 * %zh 找到则返回 ServerPlayer，否则返回 null。
 */
fun findPlayer(uuid: UUID): ServerPlayer?{
    return requireServer().playerList.getPlayer(uuid)
}

/**
 * %en
 * Find entities using an entity selector in a level.
 *
 * %zh
 * 在指定维度中使用实体选择器查找实体。
 * @param level
 * %en the ServerLevel to search in
 * %zh 要搜索的 ServerLevel。
 * @param selector
 * %en the EntitySelector to use
 * %zh 要使用的 EntitySelector。
 * @return
 * %en of matching entities
 * %zh 返回匹配的实体列表。
 */
fun findEntities(level: ServerLevel, selector: EntitySelector): List<Entity> {
    return selector.findEntities(requireServer().createCommandSourceStack().withLevel(level))
}

/**
 * %en
 * Find an entity by UUID across all levels.
 *
 * %zh
 * 在所有维度中根据 UUID 查找实体。
 * @param uuid
 * %en the entity UUID to search for
 * %zh 要查找的实体 UUID。
 * @return
 * %en Entity if found, null otherwise
 * %zh 找到则返回 Entity，否则返回 null。
 */
fun findEntity(uuid: UUID): Entity?{
    return requireServer().allLevels.firstNotNullOfOrNull { it.getEntity(uuid) }
}


/**
 * %en
 * Ban a player by adding them to the server ban list and disconnecting them.
 *
 * %zh
 * 将玩家加入服务器封禁列表并断开其连接。
 * @param player
 * %en the ServerPlayer to ban
 * %zh 要封禁的 ServerPlayer。
 */
fun ban(player: ServerPlayer) {
    val userBanList = requireServer().playerList.bans
    val nameAndId = player.nameAndId()
    if (!userBanList.isBanned(nameAndId)) {
        val userBanListEntry = UserBanListEntry(
            nameAndId, null, "server", null, null
        )
        userBanList.add(userBanListEntry)
        player.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"))
    }
}


/**
 * %en
 * Ban an IP address and disconnect matching players.
 *
 * %zh
 * 封禁一个 IP 地址，并断开所有匹配玩家的连接。
 * @param ip
 * %en IP address string to ban
 * %zh 要封禁的 IP 地址字符串。
 */
fun banIp(ip: String) {
    val ipBanList = requireServer().playerList.ipBans
    if (!ipBanList.isBanned(ip)) {
        val list = requireServer().playerList.getPlayersWithAddress(ip)
        val ipBanListEntry =
            IpBanListEntry(ip, null, "server", null, null)
        ipBanList.add(ipBanListEntry)
        for (serverPlayer in list) {
            serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned"))
        }
    }
}


/**
 * %en
 * De-op a player (remove operator status).
 *
 * %zh
 * 取消玩家的管理员权限。
 * @param player
 * %en ServerPlayer to de-op
 * %zh 要取消权限的 ServerPlayer。
 */
fun deop(player: ServerPlayer) {
    requireServer().playerList.deop(player.nameAndId())
}


/**
 * %en
 * Op a player (grant operator status).
 *
 * %zh
 * 授予玩家管理员权限。
 * @param player
 * %en ServerPlayer to op
 * %zh 要授予权限的 ServerPlayer。
 */
fun op(player: ServerPlayer) {
    requireServer().playerList.op(player.nameAndId())
}


/**
 * %en
 * Set server difficulty.
 *
 * %zh
 * 设置服务器难度。
 * @param difficulty
 * %en new Difficulty
 * %zh 新的 Difficulty。
 * @param ignoreLock
 * %en whether to ignore difficulty lock
 * %zh 是否忽略难度锁定。
 */
fun setDifficulty(difficulty: Difficulty, ignoreLock: Boolean = true) {
    requireServer().setDifficulty(difficulty, ignoreLock)
}


/**
 * %en
 * Run a function (data pack function) with an optional command source.
 *
 * %zh
 * 使用可选的命令源执行函数（数据包函数）。
 * @param id
 * %en function identifier
 * %zh 函数标识符。
 * @param source
 * %en command source to use (defaults to server)
 * %zh 要使用的命令源（默认使用服务器）。
 */
fun runFunction(id: Identifier, source: CommandSourceStack = requireServer().createCommandSourceStack()) {
    requireServer().functions.get(id).ifPresent {
        requireServer().functions.execute(it, source)
    }
}


/**
 * %en
 * Set a player's game mode.
 *
 * %zh
 * 设置玩家的游戏模式。
 * @param player
 * %en target ServerPlayer
 * %zh 目标 ServerPlayer。
 * @param gameMode
 * %en target GameType
 * %zh 目标 GameType。
 */
fun setGameMode(player: ServerPlayer, gameMode: GameType) {
    player.setGameMode(gameMode)
}


/**
 * %en
 * Get a player's current GameType.
 *
 * %zh
 * 获取玩家当前的 GameType。
 * @param player
 * %en target ServerPlayer
 * %zh 目标 ServerPlayer。
 * @return
 * %en GameType
 * %zh 返回 GameType。
 */
fun getGameMode(player: ServerPlayer): GameType {
    return player.gameMode.gameModeForPlayer
}


/**
 * %en
 * Set a game rule value on the server overworld.
 *
 * %zh
 * 在服务器主世界中设置游戏规则值。
 * @param key
 * %en GameRule key
 * %zh GameRule 键。
 * @param value
 * %en value to set
 * %zh 要设置的值。
 */
fun <T : Any> setGameRule(key: GameRule<T>, value: T) {
    try{
        requireServer().overworld().gameRules.set(key, value, requireServer())
    }catch (e: IllegalArgumentException) {
        LOGGER.warn("Failed to set game rule $key to $value", e)
    }
}


/**
 * %en
 * Get a game rule value from the server overworld.
 *
 * %zh
 * 从服务器主世界获取游戏规则值。
 * @param key
 * %en GameRule key
 * %zh GameRule 键。
 * @return
 * %en of the game rule
 * %zh 返回该游戏规则的值。
 */
fun <T : Any> getGameRule(key: GameRule<T>): T {
    return requireServer().overworld().gameRules.get(key)
}


/**
 * %en
 * Kick a player with an optional reason component.
 *
 * %zh
 * 踢出玩家，并可附带可选的原因组件。
 * @param player
 * %en target Player (ServerPlayer required to disconnect)
 * %zh 目标玩家（需要是 ServerPlayer 才能断开连接）。
 * @param reason
 * %en disconnect reason component
 * %zh 断开连接原因组件。
 */
fun kick(player: Player, reason: Component = Component.translatable("multiplayer.disconnect.kicked")) {
    if (player is ServerPlayer) {
        player.connection.disconnect(reason)
    }
}
