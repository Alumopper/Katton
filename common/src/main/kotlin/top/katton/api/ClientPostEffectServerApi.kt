@file:Suppress("unused")

package top.katton.api

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import top.katton.Katton
import top.katton.network.ClientPostEffectPacket
import top.katton.network.ServerNetworking

/**
 * %en
 * Ask one Fabric or NeoForge client to activate a registered post effect.
 *
 * The client must register [id] before this packet arrives. Paper has no
 * Katton client, so this function has no visible effect there.
 *
 * %zh
 * 请求一个 Fabric 或 NeoForge 客户端启用已注册的后处理效果。
 *
 * 客户端必须在收到数据包前注册 [id]。Paper 没有 Katton 客户端，因此此函数在
 * Paper 上不会产生可见效果。
 */
fun setPlayerPostEffect(player: ServerPlayer, id: Identifier) {
    ServerNetworking.sendPlayPacket(player, ClientPostEffectPacket.set(id))
}

/**
 * %en
 * String-id overload of [setPlayerPostEffect]. Returns false when [id] is invalid.
 *
 * %zh
 * [setPlayerPostEffect] 的字符串 id 重载。若 [id] 无效则返回 false。
 */
fun setPlayerPostEffect(player: ServerPlayer, id: String): Boolean {
    val effectId = Identifier.tryParse(id) ?: return false
    setPlayerPostEffect(player, effectId)
    return true
}

/**
 * %en
 * Ask every connected Fabric or NeoForge client to activate [id].
 *
 * %zh
 * 请求所有已连接的 Fabric 或 NeoForge 客户端启用 [id]。
 */
fun setAllPlayersPostEffect(id: Identifier) {
    val server = Katton.server ?: return
    val packet = ClientPostEffectPacket.set(id)
    for (player in server.playerList.players) {
        ServerNetworking.sendPlayPacket(player, packet)
    }
}

/**
 * %en
 * String-id overload of [setAllPlayersPostEffect]. Returns false when [id] is invalid.
 *
 * %zh
 * [setAllPlayersPostEffect] 的字符串 id 重载。若 [id] 无效则返回 false。
 */
fun setAllPlayersPostEffect(id: String): Boolean {
    val effectId = Identifier.tryParse(id) ?: return false
    setAllPlayersPostEffect(effectId)
    return true
}

/**
 * %en
 * Ask one client to clear its active Katton post effect.
 *
 * %zh
 * 请求一个客户端清除当前启用的 Katton 后处理效果。
 */
fun clearPlayerPostEffect(player: ServerPlayer) {
    ServerNetworking.sendPlayPacket(player, ClientPostEffectPacket.clear())
}

/**
 * %en
 * Ask every connected client to clear its active Katton post effect.
 *
 * %zh
 * 请求所有已连接客户端清除当前启用的 Katton 后处理效果。
 */
fun clearAllPlayersPostEffect() {
    val server = Katton.server ?: return
    val packet = ClientPostEffectPacket.clear()
    for (player in server.playerList.players) {
        ServerNetworking.sendPlayPacket(player, packet)
    }
}

/**
 * %en
 * Ask one client to toggle its current Katton post effect.
 *
 * %zh
 * 请求一个客户端切换当前 Katton 后处理效果的启用状态。
 */
fun togglePlayerPostEffect(player: ServerPlayer) {
    ServerNetworking.sendPlayPacket(player, ClientPostEffectPacket.toggle())
}

/**
 * %en
 * Ask every connected client to toggle its current Katton post effect.
 *
 * %zh
 * 请求所有已连接客户端切换当前 Katton 后处理效果的启用状态。
 */
fun toggleAllPlayersPostEffect() {
    val server = Katton.server ?: return
    val packet = ClientPostEffectPacket.toggle()
    for (player in server.playerList.players) {
        ServerNetworking.sendPlayPacket(player, packet)
    }
}
