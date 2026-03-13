import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import top.katton.api.dpcaller.tell
import top.katton.api.event.PlayerEvent
import top.katton.api.event.UseItemOnArg

fun eventBlockInteractDemo() {
    PlayerEvent.onUseItemOn += onUse@ fun(arg: UseItemOnArg): InteractionResult {
        if (arg.world.isClientSide) return InteractionResult.PASS

        val serverPlayer = arg.player as? net.minecraft.server.level.ServerPlayer ?: return InteractionResult.PASS
        val state = arg.world.getBlockState(arg.hitResult.blockPos)

        if (state.`is`(Blocks.DIAMOND_BLOCK)) {
            tell(serverPlayer, "[event-demo] You clicked diamond block! Scripts intercepted this interaction.")
            return InteractionResult.SUCCESS
        }

        return InteractionResult.PASS
    }
}

@Suppress("unused") private val eventBlockInteractDemo = eventBlockInteractDemo()

