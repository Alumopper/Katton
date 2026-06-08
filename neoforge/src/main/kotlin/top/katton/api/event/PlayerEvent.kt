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
 * %en
 * Player interaction events for NeoForge platform.
 *
 * This object provides events related to player interactions including
 * attacking entities, interacting with blocks/entities, and item destruction.
 *
 * %zh
 * NeoForge 平台的玩家交互事件。
 * 此对象提供与玩家交互相关的事件，包括攻击实体、与方块/实体交互以及物品损坏。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object PlayerEvent {

    @JvmStatic
    @SubscribeEvent
    private fun handleAttackEntity(e: AttackEntityEvent) {
        val arg = NeoPlayerAttackEntityArg(e.entity, e.target)
        onAttackEntity(arg)
        setCancel(onAttackEntity, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleEntityInteract(e: PlayerInteractEvent.EntityInteract) {
        val arg = NeoPlayerInteractEntityArg(e.entity, e.target, e.hand)
        onEntityInteract(arg)
        setCancel(onEntityInteract, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleBlockInteract(e: PlayerInteractEvent.RightClickBlock) {
        val arg = NeoPlayerInteractBlockArg(e.entity, e.pos, e.face, e.hand)
        onBlockInteract(arg)
        setCancel(onBlockInteract, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleItemInteract(e: PlayerInteractEvent.RightClickItem) {
        val arg = NeoPlayerInteractItemArg(e.entity, e.hand)
        onItemInteract(arg)
        setCancel(onItemInteract, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleLeftClickBlock(e: PlayerInteractEvent.LeftClickBlock) {
        val arg = NeoPlayerLeftClickBlockArg(e.entity, e.pos, e.face)
        onLeftClickBlock(arg)
        setCancel(onLeftClickBlock, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleDestroyItem(e: PlayerDestroyItemEvent) {
        val arg = PlayerDestroyItemArg(e.entity, e.original, e.hand)
        onDestroyItem(arg)
    }

    /**
     * %en
     * Event triggered when a player attacks (left-clicks) an entity.
     * Can be cancelled to prevent the attack.
     *
     * %zh
     * 当玩家攻击（左键）实体时触发。
     * 可取消以阻止攻击。
     */
    val onAttackEntity = createCancellableUnit<NeoPlayerAttackEntityArg>()

    /**
     * %en
     * Event triggered when a player interacts (right-clicks) with an entity.
     * Can be cancelled to prevent the interaction.
     *
     * %zh
     * 当玩家与实体交互（右键）时触发。
     * 可取消以阻止交互。
     */
    val onEntityInteract = createCancellableUnit<NeoPlayerInteractEntityArg>()

    /**
     * %en
     * Event triggered when a player interacts (right-clicks) with a block.
     * Can be cancelled to prevent the interaction.
     *
     * %zh
     * 当玩家与方块交互（右键）时触发。
     * 可取消以阻止交互。
     */
    val onBlockInteract = createCancellableUnit<NeoPlayerInteractBlockArg>()

    /**
     * %en
     * Event triggered when a player uses (right-clicks) an item.
     * Can be cancelled to prevent item use.
     *
     * %zh
     * 当玩家使用物品（右键）时触发。
     * 可取消以阻止物品使用。
     */
    val onItemInteract = createCancellableUnit<NeoPlayerInteractItemArg>()

    /**
     * %en
     * Event triggered when a player left-clicks a block.
     * Can be cancelled to prevent the action.
     *
     * %zh
     * 当玩家左键点击方块时触发。
     * 可取消以阻止该操作。
     */
    val onLeftClickBlock = createCancellableUnit<NeoPlayerLeftClickBlockArg>()

    /**
     * %en
     * Event triggered when a player's item is destroyed (e.g., tool breaking).
     *
     * %zh
     * 当玩家的物品损坏或破坏时触发（例如工具损坏）。
     */
    val onDestroyItem = createUnit<PlayerDestroyItemArg>()

}
