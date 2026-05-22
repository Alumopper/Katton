@file:Suppress("unused")

package top.katton.api

import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import top.katton.Katton
import top.katton.network.ClientItemRenderMarkerPacket
import top.katton.network.ServerNetworking
import java.util.UUID

/**
 * A lightweight client-side item model rendered directly in the world.
 *
 * It has no server entity, collision, selector target, persistence, or vanilla
 * entity sync. The server only sends this marker to clients that should see it.
 */
data class ClientItemRenderMarker(
    val id: UUID,
    val level: ResourceKey<Level>,
    val pos: Vec3,
    val stack: ItemStack,
    val displayContext: ItemDisplayContext = ItemDisplayContext.GROUND,
    val scale: Float = 1.0f,
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f,
    val roll: Float = 0.0f,
    val fullBright: Boolean = false,
    val maxDistance: Double = 64.0,
    val lifetimeTicks: Int = -1
)

/**
 * Build a [ClientItemRenderMarker] with a generated id.
 */
fun itemRenderMarker(
    level: ResourceKey<Level>,
    pos: Vec3,
    stack: ItemStack,
    displayContext: ItemDisplayContext = ItemDisplayContext.GROUND,
    scale: Float = 1.0f,
    yaw: Float = 0.0f,
    pitch: Float = 0.0f,
    roll: Float = 0.0f,
    fullBright: Boolean = false,
    maxDistance: Double = 64.0,
    lifetimeTicks: Int = -1,
    id: UUID = UUID.randomUUID()
): ClientItemRenderMarker = ClientItemRenderMarker(
    id = id,
    level = level,
    pos = pos,
    stack = stack.copy(),
    displayContext = displayContext,
    scale = scale,
    yaw = yaw,
    pitch = pitch,
    roll = roll,
    fullBright = fullBright,
    maxDistance = maxDistance,
    lifetimeTicks = lifetimeTicks
)

/**
 * Add or update a client-side item marker for every connected player.
 *
 * Returns the marker id so scripts can later remove or update it.
 */
fun showItemRenderMarker(marker: ClientItemRenderMarker): UUID {
    val server = Katton.server ?: return marker.id
    for (player in server.playerList.players) {
        showItemRenderMarker(player, marker)
    }
    return marker.id
}

/**
 * Add or update a client-side item marker for one player.
 */
fun showItemRenderMarker(player: ServerPlayer, marker: ClientItemRenderMarker): UUID {
    ServerNetworking.sendPlayPacket(player, ClientItemRenderMarkerPacket.addOrUpdate(marker.copy(stack = marker.stack.copy())))
    return marker.id
}

/**
 * Convenience overload for one player using raw coordinates.
 */
fun showItemRenderMarker(
    player: ServerPlayer,
    stack: ItemStack,
    x: Double,
    y: Double,
    z: Double,
    level: ResourceKey<Level> = player.level().dimension(),
    displayContext: ItemDisplayContext = ItemDisplayContext.GROUND,
    scale: Float = 1.0f,
    yaw: Float = 0.0f,
    pitch: Float = 0.0f,
    roll: Float = 0.0f,
    fullBright: Boolean = false,
    maxDistance: Double = 64.0,
    lifetimeTicks: Int = -1,
    id: UUID = UUID.randomUUID()
): UUID = showItemRenderMarker(
    player,
    itemRenderMarker(
        level = level,
        pos = Vec3(x, y, z),
        stack = stack,
        displayContext = displayContext,
        scale = scale,
        yaw = yaw,
        pitch = pitch,
        roll = roll,
        fullBright = fullBright,
        maxDistance = maxDistance,
        lifetimeTicks = lifetimeTicks,
        id = id
    )
)

/**
 * Remove a client-side item marker from every connected player.
 */
fun removeItemRenderMarker(id: UUID) {
    val server = Katton.server ?: return
    for (player in server.playerList.players) {
        removeItemRenderMarker(player, id)
    }
}

/**
 * Remove a client-side item marker from one player.
 */
fun removeItemRenderMarker(player: ServerPlayer, id: UUID) {
    ServerNetworking.sendPlayPacket(player, ClientItemRenderMarkerPacket.remove(id))
}

/**
 * Clear all client-side item markers from every connected player.
 */
fun clearItemRenderMarkers() {
    val server = Katton.server ?: return
    for (player in server.playerList.players) {
        clearItemRenderMarkers(player)
    }
}

/**
 * Clear all client-side item markers from one player.
 */
fun clearItemRenderMarkers(player: ServerPlayer) {
    ServerNetworking.sendPlayPacket(player, ClientItemRenderMarkerPacket.clear())
}
