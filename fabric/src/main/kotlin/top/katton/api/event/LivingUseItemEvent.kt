package top.katton.api.event

import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * Living entity use item events (not directly available in Fabric, placeholder for neoforge compatibility).
 */
object LivingUseItemEvent {
    @JvmField
    val onUseItemStart = createCancellableUnit<LivingUseItemStartArg>()

    @JvmField
    val onUseItemTick = createCancellableUnit<LivingUseItemTickArg>()

    @JvmField
    val onUseItemStop = createCancellableUnit<LivingUseItemStopArg>()

    @JvmField
    val onUseItemFinish = createUnit<LivingUseItemFinishArg>()
}
