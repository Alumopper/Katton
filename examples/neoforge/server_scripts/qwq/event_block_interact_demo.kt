package qwq

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import top.katton.api.ServerScriptEntrypoint
import top.katton.api.dpcaller.tell
import top.katton.api.event.NeoPlayerInteractBlockArg
import top.katton.api.event.PlayerEvent
import top.katton.api.event.UseItemOnArg

@ServerScriptEntrypoint
fun eventBlockInteractDemo() {
    PlayerEvent.onBlockInteract += onBlockInteract@ fun(arg: NeoPlayerInteractBlockArg){
        if (arg.player.level().isClientSide) return

        val serverPlayer = arg.player as? ServerPlayer ?: return
        val state = arg.player.level().getBlockState(arg.pos)

        if (state.`is`(Blocks.DIAMOND_BLOCK)) {
            tell(serverPlayer, "[event-demo] You clicked diamond block! Scripts intercepted this interaction.")
        }
    }
}

