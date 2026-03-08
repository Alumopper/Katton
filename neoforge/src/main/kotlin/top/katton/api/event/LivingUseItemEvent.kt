package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
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

    val onUseItemStart = createCancellableUnitEvent<LivingUseItemStartArg>()

    val onUseItemTick = createCancellableUnitEvent<LivingUseItemTickArg>()

    val onUseItemStop = createCancellableUnitEvent<LivingUseItemStopArg>()

    val onUseItemFinish = createUnitEvent<LivingUseItemFinishArg>()

    private fun <T> createUnitEvent() = DelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }

    private fun <T : CancellableEventArg> createCancellableUnitEvent() = CancellableDelegateEvent<T, Unit> { events ->
        { arg -> events.forEach { handler -> handler(arg) } }
    }
}