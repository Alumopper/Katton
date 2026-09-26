package top.katton.network

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.registration.NetworkRegistry
import top.katton.util.createUnit

/**
 * %en Send and receive script Play packets on the server. Katton keeps the packet codec registered across script reloads.
 * %zh 在服务端发送和接收脚本的游戏阶段数据包。脚本重载时，Katton 保持数据包编解码器的注册。
 */
object ScriptPlayNetworking {
    private data class Incoming(val channel: Identifier, val player: ServerPlayer, val data: ByteArray)
    private val incoming = createUnit<Incoming>()

    /**
     * %en Register a receiver for a logical channel. Katton removes the script-owned receiver on reload.
     * %zh 为逻辑频道注册接收器。脚本重载时，Katton 会清理脚本持有的接收器。
     */
    @JvmStatic
    fun registerReceiver(channel: Identifier, handler: (ServerPlayer, ByteArray) -> Unit) {
        incoming += { packet -> if (packet.channel == channel) handler(packet.player, packet.data) }
    }

    /**
     * %en Send up to 16 KiB to a player. Returns false if the peer cannot receive Katton packets. True does not confirm processing.
     * %zh 向玩家发送最多 16 KiB 的数据。对端无法接收 Katton 数据包时返回 false；true 不代表对端已处理。
     */
    @JvmStatic
    fun sendToClient(player: ServerPlayer, channel: Identifier, data: ByteArray): Boolean {
        if (!NetworkRegistry.hasChannel(player.connection, ScriptPayloadPacket.TYPE.id)) return false
        PacketDistributor.sendToPlayer(player, ScriptPayloadPacket(channel, data.copyOf()))
        return true
    }

    /**
     * %en Dispatch a received packet to script handlers. This host entrypoint is not for scripts to call.
     * %zh 将收到的数据包分发给脚本处理器。此宿主入口不供脚本调用。
     */
    @JvmStatic
    fun receive(player: ServerPlayer, packet: ScriptPayloadPacket) {
        incoming(Incoming(packet.channel, player, packet.data))
    }
}
