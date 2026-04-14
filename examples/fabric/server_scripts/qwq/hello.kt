package qwq

import net.minecraft.network.chat.Component
import top.katton.api.ServerScriptEntrypoint
import top.katton.api.event.PlayerArg
import top.katton.api.event.ServerPlayerEvent

@ServerScriptEntrypoint
fun helloMain(){
    ServerPlayerEvent.onPlayerJoin += onJoin@
    fun(arg: PlayerArg){
        val player = arg.player
        player.sendSystemMessage(Component.literal("Hello Katton"))
    }
}