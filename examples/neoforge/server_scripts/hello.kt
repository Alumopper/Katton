import net.minecraft.network.chat.Component
import top.katton.api.event.PlayerArg
import top.katton.api.event.ServerPlayerEvent

fun helloMain(){
    ServerPlayerEvent.onPlayerJoin += onJoin@
    fun(arg: PlayerArg){
        val player = arg.player
        player.sendSystemMessage(Component.literal("Hello Katton"))
    }
}

@Suppress("unused")
val entryPoint = helloMain()