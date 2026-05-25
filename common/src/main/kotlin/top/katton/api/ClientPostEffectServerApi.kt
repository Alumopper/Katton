@file:Suppress("unused")

package top.katton.api

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import top.katton.Katton
import top.katton.network.ClientPostEffectPacket
import top.katton.network.ServerNetworking

fun setPlayerPostEffect(player: ServerPlayer, id: Identifier) {
    ServerNetworking.sendPlayPacket(player, ClientPostEffectPacket.set(id))
}

fun setPlayerPostEffect(player: ServerPlayer, id: String): Boolean {
    val effectId = Identifier.tryParse(id) ?: return false
    setPlayerPostEffect(player, effectId)
    return true
}

fun setAllPlayersPostEffect(id: Identifier) {
    val server = Katton.server ?: return
    val packet = ClientPostEffectPacket.set(id)
    for (player in server.playerList.players) {
        ServerNetworking.sendPlayPacket(player, packet)
    }
}

fun setAllPlayersPostEffect(id: String): Boolean {
    val effectId = Identifier.tryParse(id) ?: return false
    setAllPlayersPostEffect(effectId)
    return true
}

fun clearPlayerPostEffect(player: ServerPlayer) {
    ServerNetworking.sendPlayPacket(player, ClientPostEffectPacket.clear())
}

fun clearAllPlayersPostEffect() {
    val server = Katton.server ?: return
    val packet = ClientPostEffectPacket.clear()
    for (player in server.playerList.players) {
        ServerNetworking.sendPlayPacket(player, packet)
    }
}

fun togglePlayerPostEffect(player: ServerPlayer) {
    ServerNetworking.sendPlayPacket(player, ClientPostEffectPacket.toggle())
}

fun toggleAllPlayersPostEffect() {
    val server = Katton.server ?: return
    val packet = ClientPostEffectPacket.toggle()
    for (player in server.playerList.players) {
        ServerNetworking.sendPlayPacket(player, packet)
    }
}
