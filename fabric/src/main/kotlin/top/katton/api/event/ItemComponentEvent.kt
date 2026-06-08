package top.katton.api.event

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents
import net.fabricmc.fabric.api.item.v1.EnchantingContext
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents
import net.fabricmc.fabric.api.item.v1.EnchantmentSource
import net.fabricmc.fabric.api.util.TriState
import top.katton.util.createTriState
import top.katton.util.createUnit
import top.katton.util.toFabric

/**
 * %en
 * Item component and enchantment events for Fabric platform.
 *
 * This object provides events related to default item component modification
 * and enchantment handling (allowing/modifying enchantments).
 *
 * %zh
 * Fabric 平台的物品组件和附魔事件。
 *
 * 此对象提供与默认物品组件修改以及附魔处理（允许或修改附魔）相关的事件。
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
    data class ModifyComponentArg(val context: DefaultItemComponentEvents.ModifyContext)

    fun initialize() {
        DefaultItemComponentEvents.MODIFY.register {
            onModifyComponent(ModifyComponentArg(it))
        }

        EnchantmentEvents.ALLOW_ENCHANTING.register { a, b, c ->
            return@register onAllowEnchanting(AllowEnchantingArg(a, b, fromFabricEnchantingContext(c))).fold(
                onSuccess = { it.toFabric() },
                onFailure = { TriState.DEFAULT }
            )
        }

        EnchantmentEvents.MODIFY.register { a, b, c ->
            onModifyEnchantment(ModifyEnchantmentArg(a, b))
        }
    }

    // === Default Item Component Modification ===

    /**
 * %en
 * Event triggered to modify default item components for items.
 * Use this to add custom components to items during registration.
 *
 * %zh
 * 当需要修改物品的默认组件时触发。
 * 可用于在注册期间为物品添加自定义组件。
 */
    val onModifyComponent = createUnit<ModifyComponentArg>()

    // === Enchantment Events ===

    /**
 * %en
 * Event triggered to allow or deny an enchantment being applied to an item.
 *
 * %zh
 * 当检查是否允许对物品附加附魔时触发。
 * @return
 * %en indicating whether to allow (TRUE), deny (FALSE), or use default (DEFAULT).
 * %zh 返回值表示允许（TRUE）、拒绝（FALSE）或使用默认值（DEFAULT）。
 */
    val onAllowEnchanting = createTriState<AllowEnchantingArg>()

    /**
 * %en
 * Event triggered when an item's enchantment is being modified.
 * Use this to customize enchantment behavior.
 *
 * %zh
 * 当物品的附魔正在被修改时触发。
 * 可用于自定义附魔行为。
 */
    val onModifyEnchantment = createUnit<ModifyEnchantmentArg>()

    /**
 * %en
 * Converts Fabric's EnchantingContext to the common bridger type.
 *
 * %zh
 * 将 Fabric 的 EnchantingContext 转换为通用 bridger 类型。
 *
 * @param arg
 * %en The Fabric enchanting context
 * %zh Fabric 的附魔上下文。
 * @return
 * %en corresponding bridger enchanting context
 * %zh 对应的 bridger 附魔上下文。
 */
    private fun fromFabricEnchantingContext(arg: EnchantingContext): top.katton.bridger.EnchantingContext {
        return when(arg){
            EnchantingContext.PRIMARY -> top.katton.bridger.EnchantingContext.PRIMARY
            EnchantingContext.ACCEPTABLE -> top.katton.bridger.EnchantingContext.ACCEPTABLE
        }
    }

    /**
 * %en
 * Converts Fabric's EnchantmentSource to the common bridger type.
 *
 * %zh
 * 将 Fabric 的 EnchantmentSource 转换为通用 bridger 类型。
 *
 * @param arg
 * %en The Fabric enchantment source
 * %zh Fabric 的附魔来源。
 * @return
 * %en corresponding bridger enchantment source
 * %zh 对应的 bridger 附魔来源。
 */
    private fun fromFabricEnchantmentSource(arg: EnchantmentSource): top.katton.bridger.EnchantmentSource {
        return when(arg){
            EnchantmentSource.VANILLA -> top.katton.bridger.EnchantmentSource.VANILLA
            EnchantmentSource.MOD -> top.katton.bridger.EnchantmentSource.MOD
            EnchantmentSource.DATA_PACK -> top.katton.bridger.EnchantmentSource.DATA_PACK
        }
    }
}
