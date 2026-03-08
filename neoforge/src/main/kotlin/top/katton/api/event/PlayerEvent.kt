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

@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object PlayerEvent {
    @SubscribeEvent
    private fun onAttackEntity(e: AttackEntityEvent) {
        val arg = NeoPlayerAttackEntityArg(e.entity, e.target)
        onAttackEntity(arg)
        setCancel(onAttackEntity, e)
    }

    @SubscribeEvent
    private fun onEntityInteract(e: PlayerInteractEvent.EntityInteract) {
        val arg = NeoPlayerInteractEntityArg(e.entity, e.target, e.hand)
        onEntityInteract(arg)
        setCancel(onEntityInteract, e)
    }

    @SubscribeEvent
    private fun onBlockInteract(e: PlayerInteractEvent.RightClickBlock) {
        val arg = NeoPlayerInteractBlockArg(e.entity, e.pos, e.face, e.hand)
        onBlockInteract(arg)
        setCancel(onBlockInteract, e)
    }

    @SubscribeEvent
    private fun onItemInteract(e: PlayerInteractEvent.RightClickItem) {
        val arg = NeoPlayerInteractItemArg(e.entity, e.hand)
        onItemInteract(arg)
        setCancel(onItemInteract, e)
    }

    @SubscribeEvent
    private fun onLeftClickBlock(e: PlayerInteractEvent.LeftClickBlock) {
        val arg = NeoPlayerLeftClickBlockArg(e.entity, e.pos, e.face)
        onLeftClickBlock(arg)
        setCancel(onLeftClickBlock, e)
    }

    @SubscribeEvent
    private fun onDestroyItem(e: PlayerDestroyItemEvent) {
        val arg = PlayerDestroyItemArg(e.entity, e.original, e.hand)
        onDestroyItem(arg)
    }

    val onAttackEntity = createCancellableUnit<NeoPlayerAttackEntityArg>()

    val onEntityInteract = createCancellableUnit<NeoPlayerInteractEntityArg>()

    val onBlockInteract = createCancellableUnit<NeoPlayerInteractBlockArg>()

    val onItemInteract = createCancellableUnit<NeoPlayerInteractItemArg>()

    val onLeftClickBlock = createCancellableUnit<NeoPlayerLeftClickBlockArg>()

    val onDestroyItem = createUnit<PlayerDestroyItemArg>()
}