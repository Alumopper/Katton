package top.katton.api.event

import net.minecraft.world.InteractionResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent
import top.katton.Katton
import top.katton.util.DelegateEvent

/**
 * Item interaction events (use / useOn)
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ItemEvent {
    @SubscribeEvent
    private fun handleUseOn(e: UseItemOnBlockEvent) {
        if (e.level.isClientSide || e.usePhase != UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK) {
            return
        }

        val result = onUseOn(ItemUseOnArg(e.useOnContext)).getOrNull() ?: return
        if (result != InteractionResult.PASS) {
            e.cancelWithResult(result)
        }
    }

    @SubscribeEvent
    private fun handleUse(e: PlayerInteractEvent.RightClickItem) {
        if (e.level.isClientSide) {
            return
        }

        val result = onUse(ItemUseArg(e.level, e.entity, e.hand)).getOrNull() ?: return
        if (result != InteractionResult.PASS) {
            e.setCancellationResult(result)
            e.setCanceled(true)
        }
    }

    @JvmField
    val onUseOn = createReturnIfNotEvent<ItemUseOnArg, InteractionResult>(InteractionResult.PASS, null)

    @JvmField
    val onUse = createReturnIfNotEvent<ItemUseArg, InteractionResult>(InteractionResult.PASS, null)

    private fun <T, R> createReturnIfNotEvent(unexpectedValue: R, returnValue: R?) =
        DelegateEvent<T, R?> { events ->
            { arg ->
                events.firstNotNullOfOrNull { handler ->
                    val result = handler(arg)
                    if (result != unexpectedValue) result else null
                } ?: returnValue
            }
        }
}
