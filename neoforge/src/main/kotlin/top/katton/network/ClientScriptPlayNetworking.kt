package top.katton.network

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import net.neoforged.neoforge.network.registration.NetworkRegistry
import top.katton.util.createUnit

/** Client-side counterpart to [ScriptPlayNetworking]. Register handlers from client entrypoints. */
object ClientScriptPlayNetworking {
    private data class Incoming(val channel: Identifier, val data: ByteArray)
    private val incoming = createUnit<Incoming>()

    @JvmStatic
    fun registerReceiver(channel: Identifier, handler: (ByteArray) -> Unit) {
        incoming += { packet -> if (packet.channel == channel) handler(packet.data) }
    }

    @JvmStatic
    fun sendToServer(channel: Identifier, data: ByteArray): Boolean {
        val listener = Minecraft.getInstance().connection ?: return false
        if (!NetworkRegistry.hasChannel(listener, ScriptPayloadPacket.TYPE.id)) return false
        ClientPacketDistributor.sendToServer(ScriptPayloadPacket(channel, data.copyOf()))
        return true
    }

    @JvmStatic
    fun receive(packet: ScriptPayloadPacket) {
        incoming(Incoming(packet.channel, packet.data))
    }
}
