package top.katton.network

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import top.katton.api.ClientItemRenderMarker
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ServerItemRenderMarkerManager {
    data class StoredMarker(
        val marker: ClientItemRenderMarker,
        val viewers: Set<UUID>,
        var remainingTicks: Int? = marker.lifetimeTicks.takeIf { it > 0 }
    )

    private val markers = ConcurrentHashMap<UUID, StoredMarker>()

    fun track(player: ServerPlayer, marker: ClientItemRenderMarker) {
        val viewerId = player.uuid
        markers.compute(marker.id) { _, existing ->
            val viewers = LinkedHashSet(existing?.viewers ?: emptySet())
            viewers.add(viewerId)
            StoredMarker(marker.copy(stack = marker.stack.copy()), viewers)
        }
    }

    fun trackAll(server: MinecraftServer, marker: ClientItemRenderMarker) {
        val viewers = server.playerList.players.mapTo(LinkedHashSet()) { it.uuid }
        markers[marker.id] = StoredMarker(marker.copy(stack = marker.stack.copy()), viewers)
    }

    fun tick() {
        val iterator = markers.entries.iterator()
        while (iterator.hasNext()) {
            val stored = iterator.next().value
            val remaining = stored.remainingTicks ?: continue
            if (remaining <= 1) {
                iterator.remove()
            } else {
                stored.remainingTicks = remaining - 1
            }
        }
    }

    fun setAnimationPlaying(id: UUID, animationSetId: String, playing: Boolean): StoredMarker? {
        return markers.computeIfPresent(id) { _, existing ->
            val playingAnimationIds = LinkedHashSet(existing.marker.playingAnimationID)
            if (playing) {
                playingAnimationIds.add(animationSetId)
            } else {
                playingAnimationIds.remove(animationSetId)
            }
            existing.copy(
                marker = existing.marker.copy(
                    stack = existing.marker.stack.copy(),
                    playingAnimationID = playingAnimationIds.toList()
                )
            )
        }
    }

    fun remove(id: UUID): StoredMarker? = markers.remove(id)

    fun removeInRange(level: net.minecraft.resources.ResourceKey<Level>, center: Vec3, radius: Double): List<StoredMarker> {
        val radiusSqr = radius * radius
        val removed = ArrayList<StoredMarker>()
        val iterator = markers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val marker = entry.value.marker
            if (marker.level == level && marker.pos.distanceToSqr(center) <= radiusSqr) {
                removed.add(entry.value)
                iterator.remove()
            }
        }
        return removed
    }

    fun clearTracked() {
        markers.clear()
    }
}
