package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import top.katton.Katton
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit
import top.katton.util.setCancel

/**
 * Player interaction events for NeoForge platform.
 *
 * This object provides events related to player interactions including
 * attacking entities, interacting with blocks/entities, and item destruction.
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object PlayerEvent {
    @SubscribeEvent
    private fun handleAttackEntity(e: AttackEntityEvent) {
        val arg = NeoPlayerAttackEntityArg(e.entity, e.target)
        onAttackEntity(arg)
        setCancel(onAttackEntity, e)
    }

    @SubscribeEvent
    private fun handleEntityInteract(e: PlayerInteractEvent.EntityInteract) {
        val arg = NeoPlayerInteractEntityArg(e.entity, e.target, e.hand)
        onEntityInteract(arg)
        setCancel(onEntityInteract, e)
    }

    @SubscribeEvent
    private fun handleBlockInteract(e: PlayerInteractEvent.RightClickBlock) {
        val arg = NeoPlayerInteractBlockArg(e.entity, e.pos, e.face, e.hand)
        onBlockInteract(arg)
        setCancel(onBlockInteract, e)
    }

    @SubscribeEvent
    private fun handleItemInteract(e: PlayerInteractEvent.RightClickItem) {
        val arg = NeoPlayerInteractItemArg(e.entity, e.hand)
        onItemInteract(arg)
        setCancel(onItemInteract, e)
    }

    @SubscribeEvent
    private fun handleLeftClickBlock(e: PlayerInteractEvent.LeftClickBlock) {
        val arg = NeoPlayerLeftClickBlockArg(e.entity, e.pos, e.face)
        onLeftClickBlock(arg)
        setCancel(onLeftClickBlock, e)
    }

    @SubscribeEvent
    private fun handleDestroyItem(e: PlayerDestroyItemEvent) {
        val arg = PlayerDestroyItemArg(e.entity, e.original, e.hand)
        onDestroyItem(arg)
    }

    /**
     * Event triggered when a player attacks (left-clicks) an entity.
     * Can be cancelled to prevent the attack.
     */
    val onAttackEntity = createCancellableUnit<NeoPlayerAttackEntityArg>()

    /**
     * Event triggered when a player interacts (right-clicks) with an entity.
     * Can be cancelled to prevent the interaction.
     */
    val onEntityInteract = createCancellableUnit<NeoPlayerInteractEntityArg>()

    /**
     * Event triggered when a player interacts (right-clicks) with a block.
     * Can be cancelled to prevent the interaction.
     */
    val onBlockInteract = createCancellableUnit<NeoPlayerInteractBlockArg>()

    /**
     * Event triggered when a player uses (right-clicks) an item.
     * Can be cancelled to prevent item use.
     */
    val onItemInteract = createCancellableUnit<NeoPlayerInteractItemArg>()

    /**
     * Event triggered when a player left-clicks a block.
     * Can be cancelled to prevent the action.
     */
    val onLeftClickBlock = createCancellableUnit<NeoPlayerLeftClickBlockArg>()

    /**
     * Event triggered when a player's item is destroyed (e.g., tool breaking).
     */
    val onDestroyItem = createUnit<PlayerDestroyItemArg>()

}
