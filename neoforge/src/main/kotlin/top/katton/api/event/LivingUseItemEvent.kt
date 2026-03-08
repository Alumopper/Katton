package top.katton.api.event

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent
import top.katton.Katton
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit
import top.katton.util.setCancel

@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object LivingUseItemEvent {

    @SubscribeEvent
    private fun onUseItemStart(e: LivingEntityUseItemEvent.Start) {
        val arg = LivingUseItemStartArg(e.entity, e.item, e.hand, e.duration)
        onUseItemStart(arg)
        setCancel(onUseItemStart, e)
    }

    @SubscribeEvent
    private fun onUseItemTick(e: LivingEntityUseItemEvent.Tick) {
        val arg = LivingUseItemTickArg(e.entity, e.item, e.duration)
        onUseItemTick(arg)
        setCancel(onUseItemTick, e)
    }

    @SubscribeEvent
    private fun onUseItemStop(e: LivingEntityUseItemEvent.Stop) {
        val arg = LivingUseItemStopArg(e.entity, e.item, e.duration)
        onUseItemStop(arg)
        setCancel(onUseItemStop, e)
    }

    @SubscribeEvent
    private fun onUseItemFinish(e: LivingEntityUseItemEvent.Finish) {
        val arg = LivingUseItemFinishArg(e.entity, e.item, e.duration, e.resultStack)
        onUseItemFinish(arg)
    }

    val onUseItemStart = createCancellableUnit<LivingUseItemStartArg>()

    val onUseItemTick = createCancellableUnit<LivingUseItemTickArg>()

    val onUseItemStop = createCancellableUnit<LivingUseItemStopArg>()

    val onUseItemFinish = createUnit<LivingUseItemFinishArg>()
}