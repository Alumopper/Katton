package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import top.katton.Katton
import top.katton.util.CancellableDelegateEvent
import top.katton.util.CancellableEventArg
import top.katton.util.DelegateEvent
import top.katton.util.setCancel

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

    val onAttackEntity = createCancellableUnitEvent<NeoPlayerAttackEntityArg>()

    val onEntityInteract = createCancellableUnitEvent<NeoPlayerInteractEntityArg>()

    val onBlockInteract = createCancellableUnitEvent<NeoPlayerInteractBlockArg>()

    val onItemInteract = createCancellableUnitEvent<NeoPlayerInteractItemArg>()

    val onLeftClickBlock = createCancellableUnitEvent<NeoPlayerLeftClickBlockArg>()

    val onDestroyItem = createUnitEvent<PlayerDestroyItemArg>()

    private fun <T> createUnitEvent() = DelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }

    private fun <T : CancellableEventArg> createCancellableUnitEvent() = CancellableDelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }
}