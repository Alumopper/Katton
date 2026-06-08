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
 * %en
 * Living entity use item events for NeoForge platform.
 *
 * This object provides events related to living entities using items including
 * start, tick, stop, and finish of item use.
 *
 * %zh
 * NeoForge 平台的生物实体使用物品事件。
 * 此对象提供与生物实体使用物品相关的事件，包括使用开始、每 tick、停止和完成。
 */
@Suppress("unused")
@EventBusSubscriber(
    modid = Katton.MOD_ID,
    value = [Dist.DEDICATED_SERVER]
)
object LivingUseItemEvent {

    @JvmStatic
    @SubscribeEvent
    private fun handleUseItemStart(e: LivingEntityUseItemEvent.Start) {
        val arg = LivingUseItemStartArg(e.entity, e.item, e.hand, e.duration)
        onUseItemStart(arg)
        setCancel(onUseItemStart, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleUseItemTick(e: LivingEntityUseItemEvent.Tick) {
        val arg = LivingUseItemTickArg(e.entity, e.item, e.duration)
        onUseItemTick(arg)
        setCancel(onUseItemTick, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleUseItemStop(e: LivingEntityUseItemEvent.Stop) {
        val arg = LivingUseItemStopArg(e.entity, e.item, e.duration)
        onUseItemStop(arg)
        setCancel(onUseItemStop, e)
    }

    @JvmStatic
    @SubscribeEvent
    private fun handleUseItemFinish(e: LivingEntityUseItemEvent.Finish) {
        val arg = LivingUseItemFinishArg(e.entity, e.item, e.duration, e.resultStack)
        onUseItemFinish(arg)
    }

    /**
     * %en
     * Event triggered when a living entity starts using an item.
     * Can be cancelled to prevent item use.
     *
     * %zh
     * 当生物实体开始使用物品时触发。
     * 可取消以阻止使用。
     */
    val onUseItemStart = createCancellableUnit<LivingUseItemStartArg>()

    /**
     * %en
     * Event triggered each tick while a living entity is using an item.
     * Can be cancelled to stop item use.
     *
     * %zh
     * 当生物实体使用物品期间每个 tick 触发。
     * 可取消以停止使用。
     */
    val onUseItemTick = createCancellableUnit<LivingUseItemTickArg>()

    /**
     * %en
     * Event triggered when a living entity stops using an item.
     * Can be cancelled to continue item use.
     *
     * %zh
     * 当生物实体停止使用物品时触发。
     * 可取消以继续使用。
     */
    val onUseItemStop = createCancellableUnit<LivingUseItemStopArg>()

    /**
     * %en
     * Event triggered when a living entity finishes using an item.
     *
     * %zh
     * 当生物实体完成使用物品时触发。
     */
    val onUseItemFinish = createUnit<LivingUseItemFinishArg>()
}
