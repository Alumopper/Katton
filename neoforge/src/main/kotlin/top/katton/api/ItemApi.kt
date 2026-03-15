package top.katton.api

import net.minecraft.core.Holder
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import top.katton.api.event.AllowEnchantingArg
import top.katton.api.event.ItemComponentEvent
import top.katton.bridger.EnchantingContext

fun ItemStack.canBeEnchantedWith(enchantment: Holder<Enchantment>, context: EnchantingContext): Boolean{
    val result = ItemComponentEvent.onAllowEnchanting.invoke(AllowEnchantingArg(enchantment, this, context))
    return if(result.isFailure || result.getOrNull()!!.isDefault){
        this.item.canBeEnchantedWith(this, enchantment, context)
    } else {
        result.getOrNull()!!.isTrue
    }
}

fun Item.canBeEnchantedWith(stack: ItemStack, enchantment: Holder<Enchantment>, context: EnchantingContext ): Boolean {
    return if (context == EnchantingContext.PRIMARY) {
        enchantment.value().isPrimaryItem(stack)
    } else {
        enchantment.value().canEnchant(stack)
    }
}
