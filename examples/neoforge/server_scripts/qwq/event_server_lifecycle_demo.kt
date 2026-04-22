package qwq

import top.katton.api.ServerScriptEntrypoint
import top.katton.api.dpcaller.tell
import top.katton.api.event.EndDatapackReloadArg
import top.katton.api.event.ServerArg
import top.katton.api.event.ServerEvent

@ServerScriptEntrypoint
fun eventServerLifecycleDemo() {
    ServerEvent.onServerStarted += started@ fun(arg: ServerArg) {
        tell("[event-demo] 服务器已启动，在线玩家=${arg.server.playerList.players.size}")
    }

    ServerEvent.onEndDatapackReload += reload@ fun(arg: EndDatapackReloadArg) {
        val status = if (arg.success) "成功" else "失败"
        tell("[event-demo] Datapack reload 已完成，状态=$status")
    }
}

