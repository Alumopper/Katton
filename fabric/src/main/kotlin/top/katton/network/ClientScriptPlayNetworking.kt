package top.katton.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.resources.Identifier
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
        if (!ClientPlayNetworking.canSend(ScriptPayloadPacket.TYPE)) return false
        ClientPlayNetworking.send(ScriptPayloadPacket(channel, data.copyOf()))
        return true
    }

    @JvmStatic
    fun receive(packet: ScriptPayloadPacket) {
        incoming(Incoming(packet.channel, packet.data))
    }
}
