@file:Suppress("unused")

package top.katton.api

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
import java.util.*
import java.util.function.Consumer


/**
 * Send a system message to a player.
 *
 * @param player target ServerPlayer
 * @param message plain string message
 */
fun tell(player: ServerPlayer, message: String) {
    player.sendSystemMessage(Component.literal(message))
}

/**
 * Send a system message to a player.
 *
 * @param player target ServerPlayer
 * @param message message component
 */
fun tell(player: ServerPlayer, message: Component) {
    player.sendSystemMessage(message)
}


/**
 * Send a system message to a player.
 *
 * @param player target ServerPlayer
 * @param message message object, converted to string
 */
fun tell(player: ServerPlayer, message: Any) {
    if (message is Component) {
        tell(player, message)
        return
    }
    tell(player, message.toString())
}




/**
 * Send a system message to multiple players.
 *
 * @param players target ServerPlayer collection
 * @param message plain string message
 */
fun tell(players: Collection<ServerPlayer>, message: String) {
    players.forEach { tell(it, message) }
}


/**
 * Send a system message to multiple players.
 *
 * @param players target ServerPlayer collection
 * @param message message component
 */
fun tell(players: Collection<ServerPlayer>, message: Component) {
    players.forEach { tell(it, message) }
}


/**
 * Send a system message to multiple players.
 *
 * @param players target ServerPlayer collection
 * @param message message object, converted to string
 */
fun tell(players: Collection<ServerPlayer>, message: Any) {
    if (message is Component) {
        tell(players, message)
        return
    }
    players.forEach { tell(it, message) }
}


/**
 * Send a system message to all players.
 *
 * @param message message object, converted to string
 */
fun tell(message: Any) {
    if (message is Component) {
        tell(requireServer().playerList.players, message)
        return
    }
    requireServer().playerList.players.forEach { tell(it, message) }
}


/**
 * Send particles to a collection of players.
 *
 * @param level server level
 * @param players players to send to
 * @param particle particle options
 * @param pos center position
 * @param delta spread vector (default zero)
 * @param speed particle speed
 * @param count number of particles
 * @param forced whether to force send (ignores client settings)
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
 * Teleport a collection of entities to another entity's position.
 *
 * @param collection entities to teleport
 * @param entity destination entity whose position to use
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
 * Teleport a collection of entities to a given position and optionally set rotation.
 *
 * @param collection entities to teleport
 * @param serverLevel destination level
 * @param pos destination position
 * @param rot optional rotation vector; if null, keeps entity rotation
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
 * Teleport a collection of entities to a position and make them look at an entity.
 *
 * @param collection entities to teleport
 * @param serverLevel destination level
 * @param pos destination position
 * @param lookAt entity to look at after teleport
 * @param anchor anchor used for target orientation
 * @param lookAtAnchor anchor used for lookAt orientation
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
 * Teleport a collection of entities to a position and make them look at a position.
 *
 * @param collection entities to teleport
 * @param serverLevel destination level
 * @param pos destination position
 * @param lookAt position to look at
 * @param anchor anchor used for target orientation
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
 * Internal teleport helper performing checks and applying lookAt behavior.
 *
 * @param entity entity to teleport
 * @param serverLevel destination level
 * @param pos destination position
 * @param rot rotation vector to apply
 * @param lookAt optional LookAt behavior
 * @param anchor optional anchor for LookAt
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
 * Set waypoint style for a waypoint transmitter.
 *
 * @param serverLevel server level
 * @param waypointTransmitter waypoint transmitter to modify
 * @param resourceKey waypoint style asset key
 */
fun setWaypointStyle(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, resourceKey: ResourceKey<WaypointStyleAsset>) {
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.style = resourceKey }
}


/**
 * Set waypoint color using ChatFormatting.
 *
 * @param serverLevel server level
 * @param waypointTransmitter waypoint transmitter
 * @param chatFormatting formatting to convert to color
 */
fun setWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, chatFormatting: ChatFormatting){
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.of<Int>(chatFormatting.color!!) }
}


/**
 * Set waypoint color using an integer color value.
 *
 * @param serverLevel server level
 * @param waypointTransmitter waypoint transmitter
 * @param integer integer color value
 */
fun setWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, integer: Int){
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.of<Int>(integer) }
}


/**
 * Reset waypoint color to default (unset).
 *
 * @param serverLevel server level
 * @param waypointTransmitter waypoint transmitter
 */
fun resetWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter) {
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.empty<Int>() }
}


/**
 * Internal helper to mutate a waypoint icon: untrack, apply consumer, then re-track.
 *
 * @param serverLevel server level
 * @param waypointTransmitter transmitter to mutate
 * @param consumer consumer that updates the icon
 */
private fun mutateIcon(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, consumer: Consumer<Waypoint.Icon>) {
    serverLevel.waypointManager.untrackWaypoint(waypointTransmitter)
    consumer.accept(waypointTransmitter.waypointIcon())
    serverLevel.waypointManager.trackWaypoint(waypointTransmitter)
}

/**
 * Send a team chat message to a list of players with filtering and formatting.
 *
 * @param entity source entity (sender)
 * @param playerTeam team being messaged
 * @param list recipients
 * @param playerChatMessage message content
 * @param commandSourceStack command source used for formatting and filtering
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



