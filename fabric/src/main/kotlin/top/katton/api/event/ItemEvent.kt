package top.katton.api.event

import net.fabricmc.fabric.api.event.player.ItemEvents
import net.minecraft.world.InteractionResult
import top.katton.util.createFirstNotNullOfOrNull

/**
 * Item interaction events for Fabric platform.
 *
 * This object provides events related to item usage and tossing.
 * Events are triggered when players interact with items in the world.
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

    /**
     * Event triggered when an item is used on a block (right-click on block).
     *
     * @return InteractionResult to control the interaction outcome.
     *         Return a non-null value to override default behavior.
     */
    val onUseOn = createFirstNotNullOfOrNull<ItemUseOnArg,InteractionResult>()

    /**
     * Event triggered when an item is used (right-click in air or on entity).
     *
     * @return InteractionResult to control the interaction outcome.
     *         Return a non-null value to override default behavior.
     */
    val onUse = createFirstNotNullOfOrNull<ItemUseArg,InteractionResult>()

}
