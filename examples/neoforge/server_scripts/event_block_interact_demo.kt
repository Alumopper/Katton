import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import top.katton.api.dpcaller.tell
import top.katton.api.event.NeoPlayerInteractBlockArg
import top.katton.api.event.PlayerEvent
import top.katton.api.event.UseItemOnArg

fun eventBlockInteractDemo() {
    PlayerEvent.onBlockInteract += onInteract@ fun(arg: NeoPlayerInteractBlockArg) {
        val serverPlayer = arg.player as? net.minecraft.server.level.ServerPlayer ?: return
        val state = arg.player.level().getBlockState(arg.pos)

        if (state.`is`(Blocks.DIAMOND_BLOCK)) {
            tell(serverPlayer, "[event-demo] You clicked diamond block! Scripts intercepted this interaction.")
        }
    }
}

@Suppress("unused") private val eventBlockInteractDemo = eventBlockInteractDemo()

