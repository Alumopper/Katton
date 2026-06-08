package top.katton.api.event

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.BlockEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.InteractionResult
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createUnit

/**
 * %en
 * Player interaction events for Fabric platform.
 *
 * This object provides events related to player interactions including
 * attacking blocks/entities, using items, and interacting with blocks/entities.
 *
 * %zh
 * Fabric 平台的玩家交互事件。
 * 此对象提供与玩家交互相关的事件，包括攻击方块/实体、使用物品，以及与方块/实体交互。
 */
@Suppress("unused")
object PlayerEvent {

    fun initialize(){
        BlockEvents.USE_ITEM_ON.register { a, b, c, d, e, f, g ->
            onUseItemOn(UseItemOnArg(a, b, c, d, e, f, g)).getOrNull()
        }

        BlockEvents.USE_WITHOUT_ITEM.register { a, b, c, d, e ->
            onUseWithoutItem(UseWithoutItemOnArg(a, b, c, d, e)).getOrNull()
        }

        AttackBlockCallback.EVENT.register { a, b, c, d, e ->
            onAttackBlock(PlayerAttackBlockArg(a, b, c, d, e)).getOrElse { InteractionResult.PASS }
        }

        AttackEntityCallback.EVENT.register { a, b, c, d, e ->
            onAttackEntity(PlayerAttackEntityArg(a, b, c, d, e)).getOrElse { InteractionResult.PASS }
        }

         UseBlockCallback.EVENT.register { a, b, c, d ->
             onBlockInteract(PlayerUseBlockArg(a, b, c, d)).getOrElse { InteractionResult.PASS }
         }

        UseEntityCallback.EVENT.register { a, b, c, d, e ->
            onEntityInteract(PlayerUseEntityArg(a, b, c, d, e)).getOrElse { InteractionResult.PASS }
        }

        UseItemCallback.EVENT.register { a, b, c ->
            onItemInteract(PlayerUseItemArg(a, b, c)).getOrElse { InteractionResult.PASS }
        }
    }

/**
 * %en
 * Event triggered when a player uses an item on a block.
 *
 * %zh
 * 当玩家对方块使用物品时触发。
 * @return
 * %en to allow default behavior, or other result to override.
 * %zh 返回值允许默认行为，或使用其他结果覆盖。
 */
    val onUseItemOn = createFirstNotNullOfOrNull<UseItemOnArg, InteractionResult>()

/**
 * %en
 * Event triggered when a player interacts with a block without holding an item.
 *
 * %zh
 * 当玩家空手与方块交互时触发。
 * @return
 * %en to allow default behavior, or other result to override.
 * %zh 返回值允许默认行为，或使用其他结果覆盖。
 */
    val onUseWithoutItem = createFirstNotNullOfOrNull<UseWithoutItemOnArg, InteractionResult>()

/**
 * %en
 * Event triggered when a player attacks (left-clicks) a block.
 *
 * %zh
 * 当玩家攻击（左键）方块时触发。
 * @return
 * %en to allow default behavior, or other result to cancel/override.
 * %zh 返回值允许默认行为，或使用其他结果取消/覆盖。
 */
    val onAttackBlock = createReturnIfNot<PlayerAttackBlockArg, InteractionResult>(InteractionResult.PASS)

/**
 * %en
 * Event triggered when a player attacks (left-clicks) an entity.
 *
 * %zh
 * 当玩家攻击（左键）实体时触发。
 * @return
 * %en to allow default behavior, or other result to cancel/override.
 * %zh 返回值允许默认行为，或使用其他结果取消/覆盖。
 */
    val onAttackEntity = createReturnIfNot<PlayerAttackEntityArg, InteractionResult>(InteractionResult.PASS)

/**
 * %en
 * Event triggered when a player interacts (right-clicks) with a block.
 *
 * %zh
 * 当玩家与方块交互（右键）时触发。
 * @return
 * %en to allow default behavior, or other result to override.
 * %zh 返回值允许默认行为，或使用其他结果覆盖。
 */
    val onBlockInteract = createReturnIfNot<PlayerUseBlockArg, InteractionResult>(InteractionResult.PASS)

/**
 * %en
 * Event triggered when a player interacts (right-clicks) with an entity.
 *
 * %zh
 * 当玩家与实体交互（右键）时触发。
 * @return
 * %en to allow default behavior, or other result to override.
 * %zh 返回值允许默认行为，或使用其他结果覆盖。
 */
    val onEntityInteract = createReturnIfNot<PlayerUseEntityArg, InteractionResult>(InteractionResult.PASS)

/**
 * %en
 * Event triggered when a player uses (right-clicks) an item.
 *
 * %zh
 * 当玩家使用物品（右键）时触发。
 * @return
 * %en to allow default behavior, or other result to override.
 * %zh 返回值允许默认行为，或使用其他结果覆盖。
 */
    val onItemInteract = createReturnIfNot<PlayerUseItemArg, InteractionResult>(InteractionResult.PASS)

/**
 * %en
 * Event triggered when a player's item is destroyed (e.g., tool breaking).
 *
 * %zh
 * 当玩家的物品损坏（例如工具破坏）时触发。
 */
    @JvmField
    val onDestroyItem = createUnit<PlayerDestroyItemArg>()
}
