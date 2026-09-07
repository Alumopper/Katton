@file:Suppress("unused")

package top.katton.api.scene

import java.util.UUID
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import top.katton.network.ClientScenePacket
import top.katton.network.ServerNetworking
import top.katton.util.ScriptExecutionContext

/**
 * %en
 * Cancellation targets the original recipients; this is not a playback acknowledgement.
 * %zh
 * 取消仅发送给原接收者；此句柄不代表客户端已经播放或播放完成。
 */
class RemoteSceneHandle
internal constructor(val instanceId: UUID, private var recipients: List<ServerPlayer>) {
    fun cancel() {
        val packet = ClientScenePacket(instanceId)
        recipients.forEach { ServerNetworking.sendPlayPacket(it, packet) }
        recipients = emptyList()
    }
}

/**
 * %en
 * Trigger a registered client scene. Call on the server thread.
 * %zh
 * 在服务端线程向指定玩家触发已注册的客户端演出。
 */
fun playPlayerScene(
    player: ServerPlayer,
    id: String,
    context: SceneContext = SceneContext(player.position(), player.uuid),
    revision: String = ScriptExecutionContext.currentScriptRevision() ?: "1",
): RemoteSceneHandle =
    sendScene(
        listOf(player),
        id,
        player.level().dimension().identifier().toString(),
        context,
        revision,
    )

/**
 * %en
 * Select recipients once, in the specified dimension and radius; late arrivals do not receive a replay.
 * %zh
 * 仅选择当前位于指定维度和半径内的玩家；后来进入者不补播。
 */
fun playNearbyScene(
    level: ServerLevel,
    origin: Vec3,
    radius: Double,
    id: String,
    context: SceneContext = SceneContext(origin),
    revision: String = ScriptExecutionContext.currentScriptRevision() ?: "1",
): RemoteSceneHandle {
    require(origin.finite() && radius.isFinite() && radius in 0.0..6e7)
    return sendScene(
        level.players().filter { it.position().distanceToSqr(origin) <= radius * radius },
        id,
        level.dimension().identifier().toString(),
        context,
        revision,
    )
}

private fun sendScene(
    players: List<ServerPlayer>,
    id: String,
    dimension: String,
    context: SceneContext,
    revision: String,
): RemoteSceneHandle {
    require(Identifier.tryParse(id) != null && id.length <= 256 && revision.length in 1..128)
    val instance = UUID.randomUUID()
    val packet =
        ClientScenePacket(instance, ClientScenePacket.Start(id, revision, dimension, context))
    players.forEach { ServerNetworking.sendPlayPacket(it, packet) }
    return RemoteSceneHandle(instance, players.toList())
}
