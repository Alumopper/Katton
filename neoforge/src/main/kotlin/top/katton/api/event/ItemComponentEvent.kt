package top.katton.api.event

import top.katton.bridger.ModifyContext
import top.katton.util.createTriState
import top.katton.util.createUnit

/**
 * Item component and enchantment events for NeoForge platform.
 *
 * This object provides placeholder events for item component modification
 * and enchantment handling. Note: NeoForge doesn't have direct equivalents
 * for all Fabric item component events, so these are provided for API compatibility.
 */
@Suppress("unused")
object ItemComponentEvent {

    /**
     * Argument class for item component modification events.
     *
     * @property context The modification context containing item and registry information
     */
    data class ModifyComponentArg(val context: ModifyContext)

    /**
     * Event triggered to modify default item components for items.
     * Use this to add custom components to items during registration.
     */
    @JvmField
    val onModifyComponent = createUnit<ModifyComponentArg>()

    /**
     * Event triggered to allow or deny an enchantment being applied to an item.
     *
     * @return TriState indicating whether to allow (TRUE), deny (FALSE), or use default (DEFAULT).
     */
    @JvmField
    val onAllowEnchanting = createTriState<AllowEnchantingArg>()

    /**
     * Event triggered when an item's enchantment is being modified.
     * Use this to customize enchantment behavior.
     */
    @JvmField
    val onModifyEnchantment = createTriState<ModifyEnchantmentArg>()
}
