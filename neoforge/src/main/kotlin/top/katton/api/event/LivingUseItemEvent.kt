package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import top.katton.Katton
import top.katton.util.CancellableDelegateEvent
import top.katton.util.CancellableEventArg
import top.katton.util.DelegateEvent
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit
import top.katton.util.setCancel

/**
 * Living entity use item events for NeoForge platform.
 *
 * This object provides events related to living entities using items including
 * start, tick, stop, and finish of item use.
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object LivingUseItemEvent {

    @SubscribeEvent
    private fun handleUseItemStart(e: LivingEntityUseItemEvent.Start) {
        val arg = LivingUseItemStartArg(e.entity, e.item, e.hand, e.duration)
        onUseItemStart(arg)
        setCancel(onUseItemStart, e)
    }

    @SubscribeEvent
    private fun handleUseItemTick(e: LivingEntityUseItemEvent.Tick) {
        val arg = LivingUseItemTickArg(e.entity, e.item, e.duration)
        onUseItemTick(arg)
        setCancel(onUseItemTick, e)
    }

    @SubscribeEvent
    private fun handleUseItemStop(e: LivingEntityUseItemEvent.Stop) {
        val arg = LivingUseItemStopArg(e.entity, e.item, e.duration)
        onUseItemStop(arg)
        setCancel(onUseItemStop, e)
    }

    @SubscribeEvent
    private fun handleUseItemFinish(e: LivingEntityUseItemEvent.Finish) {
        val arg = LivingUseItemFinishArg(e.entity, e.item, e.duration, e.resultStack)
        onUseItemFinish(arg)
    }

    /**
     * Event triggered when a living entity starts using an item.
     * Can be cancelled to prevent item use.
     */
    val onUseItemStart = createCancellableUnit<LivingUseItemStartArg>()

    /**
     * Event triggered each tick while a living entity is using an item.
     * Can be cancelled to stop item use.
     */
    val onUseItemTick = createCancellableUnit<LivingUseItemTickArg>()

    /**
     * Event triggered when a living entity stops using an item.
     * Can be cancelled to continue item use.
     */
    val onUseItemStop = createCancellableUnit<LivingUseItemStopArg>()

    /**
     * Event triggered when a living entity finishes using an item.
     */
    val onUseItemFinish = createUnit<LivingUseItemFinishArg>()
}
