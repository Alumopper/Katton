package top.katton.api.event

import net.fabricmc.fabric.api.event.player.ItemEvents
import net.minecraft.world.InteractionResult
import top.katton.util.createFirstNotNullOfOrNull

/**
 * Item interaction events (use / useOn) and item tossing.
 */
@Suppress("unused")
object ItemEvent {

    fun initialize() {
        ItemEvents.USE_ON.register {
            onUseOn(ItemUseOnArg(it)).getOrNull()
        }

        ItemEvents.USE.register {a, b, c ->
            onUse(ItemUseArg(a,b,c)).getOrNull()
        }
    }
    // === Item Usage Events ===
    val onUseOn = createFirstNotNullOfOrNull<ItemUseOnArg,InteractionResult>()

    val onUse = createFirstNotNullOfOrNull<ItemUseArg,InteractionResult>()

}