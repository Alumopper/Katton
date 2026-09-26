package top.katton.network

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import net.neoforged.neoforge.network.registration.NetworkRegistry
import top.katton.util.createUnit

/**
 * %en Send and receive script Play packets on the client. Register receivers from client entrypoints.
 * %zh 在客户端发送和接收脚本的游戏阶段数据包。请在客户端入口中注册接收器。
 */
object ClientScriptPlayNetworking {
    private data class Incoming(val channel: Identifier, val data: ByteArray)
    private val incoming = createUnit<Incoming>()

    /**
     * %en Register a receiver for a logical channel. Katton removes the script-owned receiver on reload.
     * %zh 为逻辑频道注册接收器。脚本重载时，Katton 会清理脚本持有的接收器。
     */
    @JvmStatic
    fun registerReceiver(channel: Identifier, handler: (ByteArray) -> Unit) {
        incoming += { packet -> if (packet.channel == channel) handler(packet.data) }
    }

    /**
     * %en Send up to 16 KiB to the server. Returns false if the peer cannot receive Katton packets. True does not confirm processing.
     * %zh 向服务端发送最多 16 KiB 的数据。对端无法接收 Katton 数据包时返回 false；true 不代表对端已处理。
     */
    @JvmStatic
    fun sendToServer(channel: Identifier, data: ByteArray): Boolean {
        val listener = Minecraft.getInstance().connection ?: return false
        if (!NetworkRegistry.hasChannel(listener, ScriptPayloadPacket.TYPE.id)) return false
        ClientPacketDistributor.sendToServer(ScriptPayloadPacket(channel, data.copyOf()))
        return true
    }

    /**
     * %en Dispatch a received packet to script handlers. This host entrypoint is not for scripts to call.
     * %zh 将收到的数据包分发给脚本处理器。此宿主入口不供脚本调用。
     */
    @JvmStatic
    fun receive(packet: ScriptPayloadPacket) {
        incoming(Incoming(packet.channel, packet.data))
    }
}
