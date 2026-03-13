import top.katton.api.dpcaller.tell
import top.katton.api.event.PlayerArg
import top.katton.api.event.ServerPlayerEvent
import top.katton.api.once

fun eventPlayerLifecycleDemo() {
    ServerPlayerEvent.onPlayerJoin += join@ fun(arg: PlayerArg) {
        val player = arg.player
        val firstSeen = once(key = "welcome:${player.uuid}", namespace = "event_player_lifecycle_demo") {}

        if (firstSeen) {
            tell(player, "[event-demo] 欢迎首次进入服务器，已记录你的生命周期状态")
        } else {
            tell(player, "[event-demo] 欢迎回来，生命周期监听已生效")
        }
    }

    ServerPlayerEvent.onPlayerJoin += leave@ fun(arg: PlayerArg) {
        tell(arg.player, "[event-demo] 你已离开服务器，下次加入将继续触发生命周期事件")
    }
}

@Suppress("unused") private val eventPlayerLifecycleDemo = eventPlayerLifecycleDemo()

