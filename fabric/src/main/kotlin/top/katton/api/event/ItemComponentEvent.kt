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
 * Item component and enchantment events for Fabric platform.
 *
 * This object provides events related to default item component modification
 * and enchantment handling (allowing/modifying enchantments).
 */
@Suppress("unused")
object ItemComponentEvent {

    /**
     * Argument class for item component modification events.
     *
     * @property context The modification context containing item and registry information
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
     * Event triggered to modify default item components for items.
     * Use this to add custom components to items during registration.
     */
    val onModifyComponent = createUnit<ModifyComponentArg>()

    // === Enchantment Events ===

    /**
     * Event triggered to allow or deny an enchantment being applied to an item.
     *
     * @return TriState indicating whether to allow (TRUE), deny (FALSE), or use default (DEFAULT).
     */
    val onAllowEnchanting = createTriState<AllowEnchantingArg>()

    /**
     * Event triggered when an item's enchantment is being modified.
     * Use this to customize enchantment behavior.
     */
    val onModifyEnchantment = createUnit<ModifyEnchantmentArg>()

    /**
     * Converts Fabric's EnchantingContext to the common bridger type.
     *
     * @param arg The Fabric enchanting context
     * @return The corresponding bridger enchanting context
     */
    private fun fromFabricEnchantingContext(arg: EnchantingContext): top.katton.bridger.EnchantingContext {
        return when(arg){
            EnchantingContext.PRIMARY -> top.katton.bridger.EnchantingContext.PRIMARY
            EnchantingContext.ACCEPTABLE -> top.katton.bridger.EnchantingContext.ACCEPTABLE
        }
    }

    /**
     * Converts Fabric's EnchantmentSource to the common bridger type.
     *
     * @param arg The Fabric enchantment source
     * @return The corresponding bridger enchantment source
     */
    private fun fromFabricEnchantmentSource(arg: EnchantmentSource): top.katton.bridger.EnchantmentSource {
        return when(arg){
            EnchantmentSource.VANILLA -> top.katton.bridger.EnchantmentSource.VANILLA
            EnchantmentSource.MOD -> top.katton.bridger.EnchantmentSource.MOD
            EnchantmentSource.DATA_PACK -> top.katton.bridger.EnchantmentSource.DATA_PACK
        }
    }
}
