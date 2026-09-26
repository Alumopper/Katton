package top.katton.network

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.registration.NetworkRegistry
import top.katton.util.createUnit

/** Script-owned channels use the single, host-loaded [ScriptPayloadPacket] codec. */
object ScriptPlayNetworking {
    private data class Incoming(val channel: Identifier, val player: ServerPlayer, val data: ByteArray)
    private val incoming = createUnit<Incoming>()

    @JvmStatic
    fun registerReceiver(channel: Identifier, handler: (ServerPlayer, ByteArray) -> Unit) {
        incoming += { packet -> if (packet.channel == channel) handler(packet.player, packet.data) }
    }

    @JvmStatic
    fun sendToClient(player: ServerPlayer, channel: Identifier, data: ByteArray): Boolean {
        if (!NetworkRegistry.hasChannel(player.connection, ScriptPayloadPacket.TYPE.id)) return false
        PacketDistributor.sendToPlayer(player, ScriptPayloadPacket(channel, data.copyOf()))
        return true
    }

    @JvmStatic
    fun receive(player: ServerPlayer, packet: ScriptPayloadPacket) {
        incoming(Incoming(packet.channel, player, packet.data))
    }
}
