package top.katton.api.event

import net.fabricmc.fabric.api.event.player.ItemEvents
import net.minecraft.world.InteractionResult
import top.katton.util.createFirstNotNullOfOrNull

/**
 * %en
 * Item interaction events for Fabric platform.
 *
 * This object provides events related to item usage and tossing.
 * Events are triggered when players interact with items in the world.
 *
 * %zh
 * Fabric 平台的物品交互事件。
 * 此对象提供与物品使用和投掷相关的事件。
 * 当玩家在世界中与物品交互时触发。
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
 * %en
 * Event triggered when an item is used on a block (right-click on block).
 *
 * %zh
 * 当物品在方块上使用（右键方块）时触发。
 * @return
 * %en to control the interaction outcome.
 * %zh 返回值用于控制交互结果。
 * %en
 *         Return a non-null value to override default behavior.
 *
 * %zh
 * 返回非 null 值可覆盖默认行为。
 */
    val onUseOn = createFirstNotNullOfOrNull<ItemUseOnArg,InteractionResult>()

    /**
 * %en
 * Event triggered when an item is used (right-click in air or on entity).
 *
 * %zh
 * 当物品使用（空中右键或对实体使用）时触发。
 * @return
 * %en to control the interaction outcome.
 * %zh 返回值用于控制交互结果。
 * %en
 *         Return a non-null value to override default behavior.
 *
 * %zh
 * 返回非 null 值可覆盖默认行为。
 */
    val onUse = createFirstNotNullOfOrNull<ItemUseArg,InteractionResult>()

}
