package top.katton.api.event

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.UseOnContext
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createReturnIfNot
import top.katton.util.createUnit

object PlayerEvent {
    @JvmField
    val onUseItemOn = createReturnIfNot<UseItemOnArg, InteractionResult>(InteractionResult.PASS)

    @JvmField
    val onUseWithoutItem = createReturnIfNot<UseWithoutItemOnArg, InteractionResult>(InteractionResult.PASS)

    @JvmField
    val onAttackBlock = createReturnIfNot<PlayerAttackBlockArg, InteractionResult>(InteractionResult.PASS)

    @JvmField
    val onAttackEntity = createReturnIfNot<PlayerAttackEntityArg, InteractionResult>(InteractionResult.PASS)

    @JvmField
    val onBlockInteract = createReturnIfNot<PlayerUseBlockArg, InteractionResult>(InteractionResult.PASS)

    @JvmField
    val onEntityInteract = createReturnIfNot<PlayerUseEntityArg, InteractionResult>(InteractionResult.PASS)

    @JvmField
    val onItemInteract = createReturnIfNot<PlayerUseItemArg, InteractionResult>(InteractionResult.PASS)

    @JvmField
    val onDestroyItem = createUnit<PlayerDestroyItemArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onInteract(event: PlayerInteractEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val hand = PaperNmsBridge.toNmsInteractionHand(event.hand)
                val world = PaperNmsBridge.toNmsLevel(event.player.world)

                when (event.action) {
                    Action.RIGHT_CLICK_BLOCK -> {
                        val hitResult = PaperNmsBridge.toBlockHitResult(event) ?: return
                        val block = event.clickedBlock ?: return
                        val pos = PaperNmsBridge.toNmsBlockPos(block.location)
                        val state = world.getBlockState(pos)
                        val item = PaperNmsBridge.toNmsItemStack(event.item)

                        if (item != null && !item.isEmpty) {
                            val useOnResult = onUseItemOn(
                                UseItemOnArg(item, state, world, pos, player, hand, hitResult)
                            )
                                .getOrElse { InteractionResult.PASS }
                            applyInteractionResult(event, useOnResult, applyToBlock = false, applyToItem = true)
                        } else {
                            val withoutItemResult = onUseWithoutItem(
                                UseWithoutItemOnArg(state, world, pos, player, hitResult)
                            ).getOrElse { InteractionResult.PASS }
                            applyInteractionResult(event, withoutItemResult, applyToBlock = true, applyToItem = false)
                        }

                        val blockResult = onBlockInteract(PlayerUseBlockArg(player, world, hand, hitResult))
                            .getOrElse { InteractionResult.PASS }
                        applyInteractionResult(event, blockResult, applyToBlock = true, applyToItem = false)
                    }

                    Action.RIGHT_CLICK_AIR -> {
                        val result = onItemInteract(PlayerUseItemArg(player, world, hand))
                            .getOrElse { InteractionResult.PASS }
                        applyInteractionResult(event, result, applyToBlock = false, applyToItem = true)
                    }

                    Action.LEFT_CLICK_BLOCK -> {
                        val block = event.clickedBlock ?: return
                        val result = onAttackBlock(
                            PlayerAttackBlockArg(
                                player,
                                world,
                                hand,
                                PaperNmsBridge.toNmsBlockPos(block.location),
                                PaperNmsBridge.toNmsDirection(event.blockFace)
                            )
                        ).getOrElse { InteractionResult.PASS }
                        applyInteractionResult(event, result, applyToBlock = true, applyToItem = false)
                    }

                    else -> Unit
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onInteractEntity(event: PlayerInteractAtEntityEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val entity = PaperNmsBridge.toNmsEntity(event.rightClicked)
                val result = onEntityInteract(
                    PlayerUseEntityArg(
                        player,
                        PaperNmsBridge.toNmsLevel(event.player.world),
                        PaperNmsBridge.toNmsInteractionHand(event.hand),
                        entity,
                        PaperNmsBridge.toEntityHitResult(event.rightClicked)
                    )
                ).getOrElse { InteractionResult.PASS }
                if (result != InteractionResult.PASS) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun handleAttackEntity(event: PrePlayerAttackEntityEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val target = PaperNmsBridge.toNmsEntity(event.attacked)
                val result = onAttackEntity(
                    PlayerAttackEntityArg(
                        player,
                        PaperNmsBridge.toNmsLevel(event.player.world),
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        target,
                        PaperNmsBridge.toEntityHitResult(event.attacked)
                    )
                ).getOrElse { InteractionResult.PASS }
                if (result != InteractionResult.PASS) {
                    event.isCancelled = true
                }
            }

            @EventHandler(priority = EventPriority.MONITOR)
            fun onUnknownEntityUse(event: PlayerUseUnknownEntityEvent) {
                if (event.isAttack) {
                    return
                }
                onItemInteract(
                    PlayerUseItemArg(
                        PaperNmsBridge.toNmsPlayer(event.player),
                        PaperNmsBridge.toNmsLevel(event.player.world),
                        PaperNmsBridge.toNmsInteractionHand(event.hand)
                    )
                )
            }

            @EventHandler
            fun onItemBreak(event: PlayerItemBreakEvent) {
                val item = PaperNmsBridge.toNmsItemStack(event.brokenItem) ?: return
                onDestroyItem(PlayerDestroyItemArg(PaperNmsBridge.toNmsPlayer(event.player), item, null))
            }
        }, plugin)
    }

    private fun applyInteractionResult(
        event: PlayerInteractEvent,
        result: InteractionResult,
        applyToBlock: Boolean,
        applyToItem: Boolean
    ) {
        if (result == InteractionResult.PASS) {
            return
        }

        val mapped = if (result == InteractionResult.FAIL) Event.Result.DENY else Event.Result.DENY
        if (applyToBlock) {
            event.setUseInteractedBlock(mapped)
        }
        if (applyToItem) {
            event.setUseItemInHand(mapped)
        }
    }
}
