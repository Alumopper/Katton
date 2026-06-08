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
import top.katton.network.ServerItemRenderMarkerManager
import java.util.UUID

/**
 * %en
 * A lightweight client-side item model rendered directly in the world.
 *
 * It has no server entity, collision, selector target, persistence, or vanilla
 * entity sync. The server only sends this marker to clients that should see it.
 *
 * %zh
 * 直接在世界中渲染的轻量级客户端物品模型。
 *
 * 它没有服务端实体、碰撞、选择器目标、持久化或原版实体同步。
 * 服务端只会把这个标记发送给需要看到它的客户端。
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
    val lifetimeTicks: Int = -1,
    val animations: Map<String, ClientItemRenderAnimationSet> = emptyMap(),
    val playingAnimationID: List<String> = emptyList()
){
    fun getPlayingAnimationEntries(): List<Map.Entry<String, ClientItemRenderAnimationSet>> {
        return playingAnimationID.mapNotNull { id -> animations.entries.firstOrNull { it.key == id } }
    }

    fun getPlayingAnimationSet(): List<ClientItemRenderAnimationSet> {
        return getPlayingAnimationEntries().map { it.value }
    }
}

/**
 * %en
 * Build a [ClientItemRenderMarker] with a generated id.
 *
 * %zh
 * 使用生成的 id 构建一个 [ClientItemRenderMarker]。
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
    animations: Map<String, ClientItemRenderAnimationSetBuilder.() -> Unit> = emptyMap(),
    playingAnimationID: List<String> = emptyList(),
    id: UUID = UUID.randomUUID()
): ClientItemRenderMarker {
    val builtAnimations = animations.mapValues { entry ->
        val builder = ClientItemRenderAnimationSetBuilder()
        entry.value(builder)
        builder.build()
    }
    return ClientItemRenderMarker(
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
        lifetimeTicks = lifetimeTicks,
        animations = builtAnimations,
        playingAnimationID = playingAnimationID
    )
}

/**
 * %en
 * Add or update a client-side item marker for every connected player.
 *
 * Returns the marker id so scripts can later remove or update it.
 *
 * %zh
 * 为所有已连接玩家添加或更新一个客户端物品标记。
 *
 * 返回标记 id，方便脚本后续移除或更新它。
 */
fun showItemRenderMarker(marker: ClientItemRenderMarker): UUID {
    val server = Katton.server ?: return marker.id
    val packet = ClientItemRenderMarkerPacket.addOrUpdate(marker.copy(stack = marker.stack.copy()))
    for (player in server.playerList.players) {
        ServerNetworking.sendPlayPacket(player, packet)
    }
    ServerItemRenderMarkerManager.trackAll(server, marker)
    return marker.id
}

/**
 * %en
 * Add or update a client-side item marker for one player.
 *
 * %zh
 * 为单个玩家添加或更新一个客户端物品标记。
 */
fun showItemRenderMarker(player: ServerPlayer, marker: ClientItemRenderMarker): UUID {
    ServerNetworking.sendPlayPacket(player, ClientItemRenderMarkerPacket.addOrUpdate(marker.copy(stack = marker.stack.copy())))
    ServerItemRenderMarkerManager.track(player, marker)
    return marker.id
}

/**
 * %en
 * Convenience overload for one player using raw coordinates.
 *
 * %zh
 * 使用原始坐标为单个玩家创建标记的便捷重载。
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
    animations: Map<String, ClientItemRenderAnimationSetBuilder.() -> Unit> = emptyMap(),
    playingAnimationID: List<String> = emptyList(),
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
        animations = animations,
        playingAnimationID = playingAnimationID,
        id = id
    )
)

/**
 * %en
 * Remove a client-side item marker from every connected player.
 *
 * %zh
 * 从所有已连接玩家的客户端移除一个物品标记。
 */
fun removeItemRenderMarker(id: UUID) {
    val server = Katton.server ?: return
    val removed = ServerItemRenderMarkerManager.remove(id)
    if (removed == null) {
        for (player in server.playerList.players) {
            removeItemRenderMarker(player, id)
        }
        return
    }
    for (player in server.playerList.players) {
        if (player.uuid in removed.viewers) {
            removeItemRenderMarker(player, id)
        }
    }
}

/**
 * %en
 * Remove a client-side item marker from one player.
 *
 * %zh
 * 从单个玩家的客户端移除一个物品标记。
 */
fun removeItemRenderMarker(player: ServerPlayer, id: UUID) {
    ServerNetworking.sendPlayPacket(player, ClientItemRenderMarkerPacket.remove(id))
}

/**
 * %en
 * Clear all client-side item markers from every connected player.
 *
 * %zh
 * 从所有已连接玩家的客户端清除全部物品标记。
 */
fun clearItemRenderMarkers() {
    val server = Katton.server ?: return
    for (player in server.playerList.players) {
        clearItemRenderMarkers(player)
    }
    ServerItemRenderMarkerManager.clearTracked()
}

/**
 * %en
 * Clear all client-side item markers from one player.
 *
 * %zh
 * 从单个玩家的客户端清除全部物品标记。
 */
fun clearItemRenderMarkers(player: ServerPlayer) {
    ServerNetworking.sendPlayPacket(player, ClientItemRenderMarkerPacket.clear())
}

/**
 * %en
 * Start playing one animation set on a tracked marker for every viewer that
 * originally received it. Playback starts from the beginning on the client.
 *
 * %zh
 * 在最初收到该标记的所有查看者客户端上播放一个动画集。
 * 播放会从客户端的起点重新开始。
 */
fun playItemRenderAnimationSet(id: UUID, animationSetId: String) {
    val server = Katton.server ?: return
    val stored = ServerItemRenderMarkerManager.setAnimationPlaying(id, animationSetId, true)
    val packet = ClientItemRenderMarkerPacket.playAnimation(id, animationSetId)
    if (stored == null) {
        for (player in server.playerList.players) {
            ServerNetworking.sendPlayPacket(player, packet)
        }
        return
    }
    for (player in server.playerList.players) {
        if (player.uuid in stored.viewers) {
            ServerNetworking.sendPlayPacket(player, packet)
        }
    }
}

fun startItemRenderAnimationSet(id: UUID, animationSetId: String) {
    playItemRenderAnimationSet(id, animationSetId)
}

/**
 * %en
 * Stop playing one animation set on a tracked marker for every viewer that
 * originally received it.
 *
 * %zh
 * 在最初收到该标记的所有查看者客户端上停止播放一个动画集。
 */
fun stopItemRenderAnimationSet(id: UUID, animationSetId: String) {
    val server = Katton.server ?: return
    val stored = ServerItemRenderMarkerManager.setAnimationPlaying(id, animationSetId, false)
    val packet = ClientItemRenderMarkerPacket.stopAnimation(id, animationSetId)
    if (stored == null) {
        for (player in server.playerList.players) {
            ServerNetworking.sendPlayPacket(player, packet)
        }
        return
    }
    for (player in server.playerList.players) {
        if (player.uuid in stored.viewers) {
            ServerNetworking.sendPlayPacket(player, packet)
        }
    }
}

/**
 * %en
 * Start playing one animation set for a single player. This is intentionally
 * per-viewer and does not mutate the server-side tracked marker state.
 *
 * %zh
 * 只为单个玩家播放一个动画集。此操作按查看者单独生效，
 * 不会修改服务端记录的标记状态。
 */
fun playItemRenderAnimationSet(player: ServerPlayer, id: UUID, animationSetId: String) {
    ServerNetworking.sendPlayPacket(player, ClientItemRenderMarkerPacket.playAnimation(id, animationSetId))
}

fun startItemRenderAnimationSet(player: ServerPlayer, id: UUID, animationSetId: String) {
    playItemRenderAnimationSet(player, id, animationSetId)
}

/**
 * %en
 * Stop playing one animation set for a single player. This is intentionally
 * per-viewer and does not mutate the server-side tracked marker state.
 *
 * %zh
 * 只为单个玩家停止播放一个动画集。此操作按查看者单独生效，
 * 不会修改服务端记录的标记状态。
 */
fun stopItemRenderAnimationSet(player: ServerPlayer, id: UUID, animationSetId: String) {
    ServerNetworking.sendPlayPacket(player, ClientItemRenderMarkerPacket.stopAnimation(id, animationSetId))
}

/**
 * %en
 * Clear tracked item markers in [radius] blocks around [center] and notify
 * the clients that were originally sent those markers.
 *
 * %zh
 * 清除 [center] 周围 [radius] 个方块内记录的物品标记，
 * 并通知最初收到这些标记的客户端。
 */
fun clearItemRenderMarkersInRange(level: ResourceKey<Level>, center: Vec3, radius: Double): Int {
    val server = Katton.server ?: return 0
    val removed = ServerItemRenderMarkerManager.removeInRange(level, center, radius.coerceAtLeast(0.0))
    if (removed.isEmpty()) {
        return 0
    }
    for (stored in removed) {
        val packet = ClientItemRenderMarkerPacket.remove(stored.marker.id)
        for (player in server.playerList.players) {
            if (player.uuid in stored.viewers) {
                ServerNetworking.sendPlayPacket(player, packet)
            }
        }
    }
    return removed.size
}
