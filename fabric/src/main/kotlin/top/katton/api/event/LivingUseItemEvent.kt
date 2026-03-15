package top.katton.api.event

import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * Living entity use item events for Fabric platform.
 *
 * This object provides events related to living entities using items.
 * Note: These events are placeholders for NeoForge compatibility as
 * Fabric does not have direct equivalents for all item use events.
 */
object LivingUseItemEvent {

    /**
     * Event triggered when a living entity starts using an item.
     * Can be cancelled to prevent item use.
     */
    @JvmField
    val onUseItemStart = createCancellableUnit<LivingUseItemStartArg>()

    /**
     * Event triggered each tick while a living entity is using an item.
     * Can be cancelled to stop item use.
     */
    @JvmField
    val onUseItemTick = createCancellableUnit<LivingUseItemTickArg>()

    /**
     * Event triggered when a living entity stops using an item.
     * Can be cancelled to continue item use.
     */
    @JvmField
    val onUseItemStop = createCancellableUnit<LivingUseItemStopArg>()

    /**
     * Event triggered when a living entity finishes using an item.
     */
    @JvmField
    val onUseItemFinish = createUnit<LivingUseItemFinishArg>()
}
