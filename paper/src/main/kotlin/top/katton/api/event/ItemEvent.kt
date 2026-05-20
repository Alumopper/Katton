package top.katton.api.event

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.UseOnContext
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createReturnIfNot

/**
 * Item interaction events for Paper (Bukkit) platform.
 *
 * This object provides events related to item usage on blocks and in air.
 */
@Suppress("unused")
object ItemEvent {
    @JvmField
    val onUseOn = createReturnIfNot<ItemUseOnArg, InteractionResult>(InteractionResult.PASS, null)

    @JvmField
    val onUse = createReturnIfNot<ItemUseArg, InteractionResult>(InteractionResult.PASS, null)

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onInteract(event: PlayerInteractEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val world = PaperNmsBridge.toNmsLevel(event.player.world)
                val hand = PaperNmsBridge.toNmsInteractionHand(event.hand)

                when (event.action) {
                    Action.RIGHT_CLICK_BLOCK -> {
                        val item = PaperNmsBridge.toNmsItemStack(event.item) ?: return
                        if (item.isEmpty) {
                            return
                        }
                        val hitResult = PaperNmsBridge.toBlockHitResult(event) ?: return
                        val result = onUseOn(ItemUseOnArg(UseOnContext(player, hand, hitResult))).getOrNull()
                            ?: return
                        applyInteractionResult(event, result, applyToBlock = false, applyToItem = true)
                    }

                    Action.RIGHT_CLICK_AIR -> {
                        val result = onUse(ItemUseArg(world, player, hand)).getOrNull() ?: return
                        applyInteractionResult(event, result, applyToBlock = false, applyToItem = true)
                    }

                    else -> Unit
                }
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
