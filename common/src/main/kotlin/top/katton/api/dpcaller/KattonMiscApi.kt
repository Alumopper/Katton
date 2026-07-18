@file:Suppress("unused")

package top.katton.api.dpcaller

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.*
import net.minecraft.network.chat.ClickEvent.SuggestCommand
import net.minecraft.network.chat.HoverEvent.ShowText
import net.minecraft.resources.ResourceKey
import net.minecraft.server.commands.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import net.minecraft.util.Mth
import net.minecraft.world.entity.*
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointStyleAsset
import net.minecraft.world.waypoints.WaypointTransmitter
import top.katton.api.LOGGER
import top.katton.api.requireServer
import java.util.*
import java.util.function.Consumer

/*? if mc_26_2 {*/
private fun chatFormattingColor(formatting: ChatFormatting): Int? = TextColor.fromLegacyFormat(formatting)?.value
/*?} else {*/
/*private fun chatFormattingColor(formatting: ChatFormatting): Int? = formatting.color*/
/*?}*/

/**
 * %en
 * Miscellaneous utility API for common operations.
 *
 * This module provides various utility functions including:
 * - Player messaging
 * - Particle spawning
 * - Waypoint management
 * - Entity teleportation
 *
 * %zh
 * 常用操作的杂项工具 API。
 * 本模块提供多种工具函数，包括：
 * - 玩家消息发送
 * - 粒子生成
 * - 路标管理
 * - 实体传送
 */

/**
 * %en
 * Send a system message to a player.
 *
 * %zh
 * 向玩家发送系统消息。
 * @param player
 * %en target ServerPlayer
 * %zh 目标 ServerPlayer。
 * @param message
 * %en plain string message
 * %zh 普通字符串消息。
 */
fun tell(player: ServerPlayer, message: String) {
    player.sendSystemMessage(Component.literal(message))
}

/**
 * %en
 * Send a system message to a player.
 *
 * %zh
 * 向玩家发送系统消息。
 * @param player
 * %en target ServerPlayer
 * %zh 目标 ServerPlayer。
 * @param message
 * %en message component
 * %zh 消息组件。
 */
fun tell(player: ServerPlayer, message: Component) {
    player.sendSystemMessage(message)
}

/**
 * %en
 * Send a system message to a player.
 *
 * %zh
 * 向玩家发送系统消息。
 * @param player
 * %en target ServerPlayer
 * %zh 目标 ServerPlayer。
 * @param message
 * %en message object, converted to string
 * %zh 消息对象，会转换为字符串。
 */
fun tell(player: ServerPlayer, message: Any) {
    if (message is Component) {
        tell(player, message)
        return
    }
    tell(player, message.toString())
}

/**
 * %en
 * Send a system message to multiple players.
 *
 * %zh
 * 向多个玩家发送系统消息。
 * @param players
 * %en target ServerPlayer collection
 * %zh 目标 ServerPlayer 集合。
 * @param message
 * %en plain string message
 * %zh 普通字符串消息。
 */
fun tell(players: Collection<ServerPlayer>, message: String) {
    players.forEach { tell(it, message) }
}


/**
 * %en
 * Send a system message to multiple players.
 *
 * %zh
 * 向多个玩家发送系统消息。
 * @param players
 * %en target ServerPlayer collection
 * %zh 目标 ServerPlayer 集合。
 * @param message
 * %en message component
 * %zh 消息组件。
 */
fun tell(players: Collection<ServerPlayer>, message: Component) {
    players.forEach { tell(it, message) }
}


/**
 * %en
 * Send a system message to multiple players.
 *
 * %zh
 * 向多个玩家发送系统消息。
 * @param players
 * %en target ServerPlayer collection
 * %zh 目标 ServerPlayer 集合。
 * @param message
 * %en message object, converted to string
 * %zh 消息对象，会转换为字符串。
 */
fun tell(players: Collection<ServerPlayer>, message: Any) {
    if (message is Component) {
        tell(players, message)
        return
    }
    players.forEach { tell(it, message) }
}


/**
 * %en
 * Send a system message to all players.
 *
 * %zh
 * 向所有玩家发送系统消息。
 * @param message
 * %en message object, converted to string
 * %zh 消息对象，会转换为字符串。
 */
fun tell(message: Any) {
    if (message is Component) {
        tell(requireServer().playerList.players, message)
        return
    }
    requireServer().playerList.players.forEach { tell(it, message) }
}


/**
 * %en
 * Send particles to a collection of players.
 *
 * %zh
 * 向一组玩家发送粒子。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param players
 * %en players to send to
 * %zh 要发送给的玩家。
 * @param particle
 * %en particle options
 * %zh 粒子选项。
 * @param pos
 * %en center position
 * %zh 中心位置。
 * @param delta
 * %en spread vector (default zero)
 * %zh 扩散向量（默认零）。
 * @param speed
 * %en particle speed
 * %zh 粒子速度。
 * @param count
 * %en particle count
 * %zh 粒子数量。
 * @param forced
 * %en whether to force sending and ignore client settings
 * %zh 是否强制发送，忽略客户端设置。
 */
fun particle(level: ServerLevel, players: Collection<ServerPlayer>, particle: ParticleOptions, pos: Vec3, delta: Vec3 = Vec3.ZERO, speed: Double = 1.0, count: Int = 0, forced: Boolean = false) {
    for (player in players) {
        level.sendParticles(
            player,
            particle,
            forced,
            false,
            pos.x, pos.y, pos.z,
            count,
            delta.x, delta.y, delta.z,
            speed
        )
    }
}


/**
 * %en
 * Teleport a collection of entities to another entity's position.
 *
 * %zh
 * 将一组实体传送到另一个实体的位置。
 * @param collection
 * %en entities to teleport
 * %zh 要传送的实体。
 * @param entity
 * %en entity used as the destination position
 * %zh 作为目标位置来源的实体。
 */
fun teleportToEntity(collection: MutableCollection<out Entity>, entity: Entity) {
    for (entity2 in collection) {
        performTeleport(
            entity2,
            entity.level() as ServerLevel,
            entity.position(),
            entity.rotationVector,
            null
        )
    }
}


/**
 * %en
 * Teleport a collection of entities to a position with optional rotation.
 *
 * %zh
 * 将一组实体传送到指定位置，并可选设置旋转。
 * @param collection
 * %en entities to teleport
 * %zh 要传送的实体。
 * @param level
 * %en destination level
 * %zh 目标关卡。
 * @param pos
 * %en destination position
 * %zh 目标位置。
 * @param rot
 * %en optional rotation vector; if null, keep the entity's current rotation
 * %zh 可选旋转向量；为 null 时保持实体当前旋转。
 */
fun teleportToPos(
    collection: MutableCollection<out Entity>,
    serverLevel: ServerLevel,
    pos: Vec3,
    rot: Vec2? = null
) {
    for (entity in collection) {
        if (rot == null) {
            performTeleport(entity, serverLevel, pos, entity.rotationVector, null)
        } else {
            performTeleport(entity, serverLevel, pos, rot, null)
        }
    }
}



/**
 * %en
 * Teleport a collection of entities to a position and make them face another entity.
 *
 * %zh
 * 将一组实体传送到某个位置，并让它们朝向另一个实体。
 * @param collection
 * %en entities to teleport
 * %zh 要传送的实体。
 * @param level
 * %en destination level
 * %zh 目标关卡。
 * @param pos
 * %en destination position
 * %zh 目标位置。
 * @param lookAt
 * %en entity to look at after teleporting
 * %zh 传送后要朝向的实体。
 * @param anchor
 * %en anchor used on the teleported entity
 * %zh 用于目标朝向的锚点。
 * @param lookAtAnchor
 * %en anchor used on the entity being looked at
 * %zh 用于被看向目标的锚点。
 */
fun teleportToPos(
    collection: MutableCollection<out Entity>,
    serverLevel: ServerLevel,
    pos: Vec3,
    lookAt: Entity,
    anchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET,
    lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET
) {
    for (entity in collection) {
        performTeleport(entity, serverLevel, pos, entity.rotationVector, LookAt.LookAtEntity(lookAt, lookAtAnchor), anchor)
    }
}


/**
 * %en
 * Teleport a collection of entities to a position and make them face another position.
 *
 * %zh
 * 将一组实体传送到某个位置，并让它们朝向另一个位置。
 * @param collection
 * %en entities to teleport
 * %zh 要传送的实体。
 * @param level
 * %en destination level
 * %zh 目标关卡。
 * @param pos
 * %en destination position
 * %zh 目标位置。
 * @param lookAt
 * %en position to look at
 * %zh 要看向的位置。
 * @param anchor
 * %en anchor used on the teleported entity
 * %zh 用于目标朝向的锚点。
 */
fun teleportToPos(
    collection: MutableCollection<out Entity>,
    serverLevel: ServerLevel,
    pos: Vec3,
    lookAt: Vec3,
    anchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET,
) {
    for (entity in collection) {
        performTeleport(entity, serverLevel, pos, entity.rotationVector, LookAt.LookAtPosition(lookAt), anchor)
    }
}


/**
 * %en
 * Internal teleport helper performing checks and applying lookAt behavior.
 *
 * %zh
 * 内部传送辅助方法，负责检查并应用 lookAt 行为。
 * @param entity
 * %en entity to teleport
 * %zh 要传送的实体。
 * @param level
 * %en destination level
 * %zh 目标关卡。
 * @param pos
 * %en destination position
 * %zh 目标位置。
 * @param rot
 * %en rotation vector to apply
 * %zh 要应用的旋转向量。
 * @param lookAt
 * %en optional LookAt behavior
 * %zh 可选 LookAt 行为。
 * @param anchor
 * %en optional anchor for LookAt
 * %zh LookAt 使用的可选锚点。
 */
private fun performTeleport(
    entity: Entity,
    serverLevel: ServerLevel,
    pos: Vec3,
    rot: Vec2,
    lookAt: LookAt?,
    anchor: EntityAnchorArgument.Anchor? = null
) {
    val blockPos = BlockPos.containing(pos.x, pos.y, pos.z)
    if (!Level.isInSpawnableBounds(blockPos)) {
        LOGGER.error("Invalid position for teleport")
        return
    } else {
        val y = Mth.wrapDegrees(rot.y)
        val x = Mth.wrapDegrees(rot.x)
        if (entity.teleportTo(serverLevel, pos.x, pos.y, pos.z, EnumSet.noneOf(Relative::class.java), y, x, true)) {
            lookAt?.perform(requireServer().createCommandSourceStack().withAnchor(anchor!!), entity)

            if (!(entity is LivingEntity && entity.isFallFlying)) {
                entity.deltaMovement = entity.deltaMovement.multiply(1.0, 0.0, 1.0)
                entity.setOnGround(true)
            }

            if (entity is PathfinderMob) {
                entity.getNavigation().stop()
            }
        }
    }
}


/**
 * %en
 * Set waypoint style for a waypoint transmitter.
 *
 * %zh
 * 为路标发射器设置路标样式。
 * @param serverLevel
 * %en server level
 * %zh 服务端关卡。
 * @param waypointTransmitter
 * %en waypoint transmitter to modify
 * %zh 要修改的路标发射器。
 * @param resourceKey
 * %en waypoint style asset key
 * %zh 路标样式资源键。
 */
fun setWaypointStyle(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, resourceKey: ResourceKey<WaypointStyleAsset>) {
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.style = resourceKey }
}


/**
 * %en
 * Set waypoint color using ChatFormatting.
 *
 * %zh
 * 使用 ChatFormatting 设置路标颜色。
 * @param serverLevel
 * %en server level
 * %zh 服务端关卡。
 * @param waypointTransmitter
 * %en waypoint transmitter
 * %zh 路标发射器。
 * @param chatFormatting
 * %en formatting to convert to color
 * %zh 要转换为颜色的格式化值。
 */
fun setWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, chatFormatting: ChatFormatting){
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.of<Int>(chatFormattingColor(chatFormatting)!!) }
}


/**
 * %en
 * Set waypoint color using an integer color value.
 *
 * %zh
 * 使用整数颜色值设置路标颜色。
 * @param serverLevel
 * %en server level
 * %zh 服务端关卡。
 * @param waypointTransmitter
 * %en waypoint transmitter
 * %zh 路标发射器。
 * @param integer
 * %en integer color value
 * %zh 整数颜色值。
 */
fun setWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, integer: Int){
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.of<Int>(integer) }
}


/**
 * %en
 * Reset waypoint color to default (unset).
 *
 * %zh
 * 将路标颜色重置为默认值（未设置）。
 * @param serverLevel
 * %en server level
 * %zh 服务端关卡。
 * @param waypointTransmitter
 * %en waypoint transmitter
 * %zh 路标发射器。
 */
fun resetWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter) {
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.empty<Int>() }
}


/**
 * %en
 * Internal helper to mutate a waypoint icon: untrack, apply consumer, then re-track.
 *
 * %zh
 * 用于修改路标图标的内部辅助函数：先取消跟踪，应用更新函数，再重新跟踪。
 * @param serverLevel
 * %en server level
 * %zh 服务端关卡。
 * @param waypointTransmitter
 * %en transmitter to mutate
 * %zh 要修改的发射器。
 * @param consumer
 * %en consumer that updates the icon
 * %zh 更新图标的函数。
 */
private fun mutateIcon(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, consumer: Consumer<Waypoint.Icon>) {
    serverLevel.waypointManager.untrackWaypoint(waypointTransmitter)
    consumer.accept(waypointTransmitter.waypointIcon())
    serverLevel.waypointManager.trackWaypoint(waypointTransmitter)
}

/**
 * %en
 * Send a team chat message to a list of players with filtering and formatting.
 *
 * %zh
 * 向一组玩家发送带过滤和格式化的团队聊天消息。
 * @param entity
 * %en source entity (sender)
 * %zh 来源实体（发送者）。
 * @param playerTeam
 * %en team being messaged
 * %zh 接收消息的团队。
 * @param list
 * %en recipients
 * %zh 接收者列表。
 * @param playerChatMessage
 * %en message content
 * %zh 消息内容。
 * @param commandSourceStack
 * %en command source used for formatting and filtering
 * %zh 用于格式化和过滤的命令源。
 */
fun teamMsg(
    entity: Entity,
    playerTeam: PlayerTeam,
    list: MutableList<ServerPlayer>,
    playerChatMessage: PlayerChatMessage,
    commandSourceStack: CommandSourceStack = requireServer().createCommandSourceStack(),
) {
    val component: Component = playerTeam.getFormattedDisplayName().withStyle(
        Style.EMPTY
            .withHoverEvent(ShowText(Component.translatable("chat.type.team.hover")))
            .withClickEvent(SuggestCommand("/teammsg ")))
    val bound = ChatType.bind(ChatType.TEAM_MSG_COMMAND_INCOMING, commandSourceStack).withTargetName(component)
    val bound2 = ChatType.bind(ChatType.TEAM_MSG_COMMAND_OUTGOING, commandSourceStack).withTargetName(component)
    val outgoingChatMessage = OutgoingChatMessage.create(playerChatMessage)
    var bl = false

    for (serverPlayer in list) {
        val bound3 = if (serverPlayer === entity) bound2 else bound
        val bl2 = commandSourceStack.shouldFilterMessageTo(serverPlayer)
        serverPlayer.sendChatMessage(outgoingChatMessage, bl2, bound3)
        bl = bl or (bl2 && playerChatMessage.isFullyFiltered)
    }

    if (bl) {
        commandSourceStack.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL)
    }
}
