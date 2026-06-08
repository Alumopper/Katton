package top.katton.api.event

import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

/**
 * %en
 * Living entity use item events for Fabric platform.
 *
 * This object provides events related to living entities using items.
 * Note: These events are placeholders for NeoForge compatibility as
 * Fabric does not have direct equivalents for all item use events.
 *
 * %zh
 * Fabric 平台的生物实体物品使用事件。
 *
 * 此对象提供与生物实体使用物品相关的事件。
 * 注意：这些事件主要用于与 NeoForge 保持兼容，因为 Fabric 并没有所有物品使用事件的直接对应项。
 */
object LivingUseItemEvent {

    /**
 * %en
 * Living entity use item events for Fabric platform.
 *
 * This object provides events related to living entities using items.
 * Note: These events are placeholders for NeoForge compatibility as
 * Fabric does not have direct equivalents for all item use events.
 *
 * %zh
 * Fabric 平台的生物实体物品使用事件。
 *
 * 此对象提供与生物实体使用物品相关的事件。
 * 注意：这些事件主要用于与 NeoForge 保持兼容，因为 Fabric 并没有所有物品使用事件的直接对应项。
 */
    @JvmField
    val onUseItemStart = createCancellableUnit<LivingUseItemStartArg>()

    /**
 * %en
 * Living entity use item events for Fabric platform.
 *
 * This object provides events related to living entities using items.
 * Note: These events are placeholders for NeoForge compatibility as
 * Fabric does not have direct equivalents for all item use events.
 *
 * %zh
 * Fabric 平台的生物实体物品使用事件。
 *
 * 此对象提供与生物实体使用物品相关的事件。
 * 注意：这些事件主要用于与 NeoForge 保持兼容，因为 Fabric 并没有所有物品使用事件的直接对应项。
 */
    @JvmField
    val onUseItemTick = createCancellableUnit<LivingUseItemTickArg>()

    /**
 * %en
 * Living entity use item events for Fabric platform.
 *
 * This object provides events related to living entities using items.
 * Note: These events are placeholders for NeoForge compatibility as
 * Fabric does not have direct equivalents for all item use events.
 *
 * %zh
 * Fabric 平台的生物实体物品使用事件。
 *
 * 此对象提供与生物实体使用物品相关的事件。
 * 注意：这些事件主要用于与 NeoForge 保持兼容，因为 Fabric 并没有所有物品使用事件的直接对应项。
 */
    @JvmField
    val onUseItemStop = createCancellableUnit<LivingUseItemStopArg>()

    /**
 * %en
 * Living entity use item events for Fabric platform.
 *
 * This object provides events related to living entities using items.
 * Note: These events are placeholders for NeoForge compatibility as
 * Fabric does not have direct equivalents for all item use events.
 *
 * %zh
 * Fabric 平台的生物实体物品使用事件。
 *
 * 此对象提供与生物实体使用物品相关的事件。
 * 注意：这些事件主要用于与 NeoForge 保持兼容，因为 Fabric 并没有所有物品使用事件的直接对应项。
 */
    @JvmField
    val onUseItemFinish = createUnit<LivingUseItemFinishArg>()
}
