package top.katton.api.event

import top.katton.bridger.ModifyContext
import top.katton.util.createTriState
import top.katton.util.createUnit

/**
 * Item component and enchantment events (NeoForge placeholder).
 * Note: NeoForge doesn't have direct equivalents for these fabric events.
 */
@Suppress("unused")
object ItemComponentEvent {


    data class ModifyComponentArg(val context: ModifyContext)

    @JvmField
    val onModifyComponent = createUnit<ModifyComponentArg>()

    @JvmField
    val onAllowEnchanting = createTriState<AllowEnchantingArg>()

    @JvmField
    val onModifyEnchantment = createTriState<ModifyEnchantmentArg>()
}
