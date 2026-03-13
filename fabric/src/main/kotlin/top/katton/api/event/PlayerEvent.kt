package top.katton.api.event

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.BlockEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.InteractionResult
import top.katton.util.createReturnIfNot
import top.katton.util.createUnit

/**
 * Player interaction events (attack/use interactions).
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

    val onUseItemOn = createReturnIfNot<UseItemOnArg, InteractionResult>(InteractionResult.PASS)

    val onUseWithoutItem = createReturnIfNot<UseWithoutItemOnArg, InteractionResult>(InteractionResult.PASS)

    val onAttackBlock = createReturnIfNot<PlayerAttackBlockArg, InteractionResult>(InteractionResult.PASS)

    val onAttackEntity = createReturnIfNot<PlayerAttackEntityArg, InteractionResult>(InteractionResult.PASS)

    val onBlockInteract = createReturnIfNot<PlayerUseBlockArg, InteractionResult>(InteractionResult.PASS)

    val onEntityInteract = createReturnIfNot<PlayerUseEntityArg, InteractionResult>(InteractionResult.PASS)

    val onItemInteract = createReturnIfNot<PlayerUseItemArg, InteractionResult>(InteractionResult.PASS)

    @JvmField
    val onDestroyItem = createUnit<PlayerDestroyItemArg>()
}