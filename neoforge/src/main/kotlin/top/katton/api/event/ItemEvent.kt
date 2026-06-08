package top.katton.api.event

import net.minecraft.world.InteractionResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent
import top.katton.Katton
import top.katton.util.createReturnIfNot

/**
 * %en
 * Item interaction events for NeoForge platform.
 *
 * This object provides events related to item usage including
 * using items on blocks and general item use events.
 *
 * %zh
 * NeoForge 平台的物品交互事件。
 * 此对象提供与物品使用相关的事件，包括对方块使用物品以及一般的物品使用事件。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object ItemEvent {

    @JvmStatic
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

    @JvmStatic
    @SubscribeEvent
    private fun handleUse(e: PlayerInteractEvent.RightClickItem) {
        if (e.level.isClientSide) {
            return
        }

        val result = onUse(ItemUseArg(e.level, e.entity, e.hand)).getOrNull() ?: return
        if (result != InteractionResult.PASS) {
            e.cancellationResult = result
            e.isCanceled = true
        }
    }

    @JvmField
    val onUseOn = createReturnIfNot<ItemUseOnArg, InteractionResult>(InteractionResult.PASS, null)

    @JvmField
    val onUse = createReturnIfNot<ItemUseArg, InteractionResult>(InteractionResult.PASS, null)
}
