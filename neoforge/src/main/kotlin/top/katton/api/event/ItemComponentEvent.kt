package top.katton.api.event

import top.katton.bridger.ModifyContext
import top.katton.util.createTriState
import top.katton.util.createUnit

/**
 * %en
 * Item component and enchantment events for NeoForge platform.
 *
 * This object provides placeholder events for item component modification
 * and enchantment handling. Note: NeoForge doesn't have direct equivalents
 * for all Fabric item component events, so these are provided for API compatibility.
 *
 * %zh
 * NeoForge 平台的物品组件和附魔事件。
 * 这是为了物品组件修改和附魔处理保留的占位事件。NeoForge 没有与所有 Fabric 物品组件事件一一对应的实现，因此这里只为 API 兼容性提供这些事件。
 */
@Suppress("unused")
object ItemComponentEvent {

    /**
     * %en
     * Argument class for item component modification events.
     *
     * %zh
     * 物品组件修改事件的参数类。
     * @property context
     * %en The modification context containing item and registry information
     * %zh 包含物品和注册表信息的修改上下文。
     */
    data class ModifyComponentArg(val context: ModifyContext)

    /**
     * %en
     * Event triggered to modify default item components for items.
     * Use this to add custom components to items during registration.
     *
     * %zh
     * 当需要修改物品的默认组件时触发。
     * 可在注册期间为物品添加自定义组件。
     */
    @JvmField
    val onModifyComponent = createUnit<ModifyComponentArg>()

    /**
     * %en
     * Event triggered to allow or deny an enchantment being applied to an item.
     *
     * %zh
     * 当需要允许或拒绝将附魔应用到物品时触发。
     * @return
     * %en indicating whether to allow (TRUE), deny (FALSE), or use default (DEFAULT).
     * %zh 返回是否允许（TRUE）、拒绝（FALSE）或使用默认值（DEFAULT）。
     */
    @JvmField
    val onAllowEnchanting = createTriState<AllowEnchantingArg>()

    /**
     * %en
     * Event triggered when an item's enchantment is being modified.
     * Use this to customize enchantment behavior.
     *
     * %zh
     * 当物品的附魔内容被修改时触发。
     * 可用于自定义附魔行为。
     */
    @JvmField
    val onModifyEnchantment = createTriState<ModifyEnchantmentArg>()
}
