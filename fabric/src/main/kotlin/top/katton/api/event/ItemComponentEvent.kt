

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
 * Item component and enchantment events (component modification + enchanting).
 */
@Suppress("unused")
object ItemComponentEvent {

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
    val onModifyComponent = createUnit<ModifyComponentArg>()

    // === Enchantment Events ===
    val onAllowEnchanting = createTriState<AllowEnchantingArg>()

    val onModifyEnchantment = createUnit<ModifyEnchantmentArg>()

    private fun fromFabricEnchantingContext(arg: EnchantingContext): top.katton.bridger.EnchantingContext {
        return when(arg){
            EnchantingContext.PRIMARY -> top.katton.bridger.EnchantingContext.PRIMARY
            EnchantingContext.ACCEPTABLE -> top.katton.bridger.EnchantingContext.ACCEPTABLE
        }
    }

    private fun fromFabricEnchantmentSource(arg: EnchantmentSource): top.katton.bridger.EnchantmentSource {
        return when(arg){
            EnchantmentSource.VANILLA -> top.katton.bridger.EnchantmentSource.VANILLA
            EnchantmentSource.MOD -> top.katton.bridger.EnchantmentSource.MOD
            EnchantmentSource.DATA_PACK -> top.katton.bridger.EnchantmentSource.DATA_PACK
        }
    }
}