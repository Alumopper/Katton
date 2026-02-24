package top.katton.registry

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.*
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import java.util.*
import java.util.function.Consumer

/**
 * KattonItemWrapper用于将普通的Item类型包装成KattonItemInterface
 *
 * 这个包装器允许直接使用Minecraft原版的Item或其他Item实现，
 * 而无需继承KattonItem类，同时保持Katton系统的兼容性
 */
open class KattonItemWrapper(
    override val properties: KattonItemProperties,
    val wrappedItem: Item
) : KattonItemInterface {

    override fun components(): DataComponentMap {
        val components = DataComponentMap.builder()
        components.addAll(wrappedItem.components())
        components.addAll(properties.buildComponent())
        return components.build()
    }

    override fun asItem(): Item = wrappedItem

    override fun getDefaultInstance(): ItemStack {
        return ItemStack(Holder.direct(wrappedItem), 1).apply {
            applyComponents(components())
        }
    }

    override fun requiredFeatures(): FeatureFlagSet = wrappedItem.requiredFeatures()

    override fun getDescriptionId(): String = wrappedItem.descriptionId

    override fun onUseTick(
        level: Level,
        livingEntity: LivingEntity,
        itemStack: ItemStack,
        ticksRemaining: Int
    ) = wrappedItem.onUseTick(level, livingEntity, itemStack, ticksRemaining)

    override fun onDestroyed(itemEntity: ItemEntity) =
        wrappedItem.onDestroyed(itemEntity)

    override fun canDestroyBlock(
        itemStack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        user: LivingEntity
    ): Boolean = wrappedItem.canDestroyBlock(itemStack, state, level, pos, user)

    override fun useOn(context: UseOnContext) = wrappedItem.useOn(context)

    override fun getDestroySpeed(
        itemStack: ItemStack,
        state: BlockState
    ): Float = wrappedItem.getDestroySpeed(itemStack, state)

    override fun use(
        level: Level,
        player: Player,
        hand: InteractionHand
    ) = wrappedItem.use(level, player, hand)

    override fun finishUsingItem(
        itemStack: ItemStack,
        level: Level,
        entity: LivingEntity
    ): ItemStack = wrappedItem.finishUsingItem(itemStack, level, entity)

    override fun isBarVisible(stack: ItemStack): Boolean = wrappedItem.isBarVisible(stack)

    override fun getBarWidth(stack: ItemStack): Int = wrappedItem.getBarWidth(stack)

    override fun getBarColor(stack: ItemStack): Int = wrappedItem.getBarColor(stack)

    override fun overrideStackedOnOther(
        self: ItemStack,
        slot: Slot,
        clickAction: ClickAction,
        player: Player
    ): Boolean = wrappedItem.overrideStackedOnOther(self, slot, clickAction, player)

    override fun overrideOtherStackedOnMe(
        self: ItemStack,
        other: ItemStack,
        slot: Slot,
        clickAction: ClickAction,
        player: Player,
        carriedItem: SlotAccess
    ): Boolean = wrappedItem.overrideOtherStackedOnMe(self, other, slot, clickAction, player, carriedItem)

    override fun getAttackDamageBonus(
        victim: Entity,
        damage: Float,
        damageSource: DamageSource
    ): Float = wrappedItem.getAttackDamageBonus(victim, damage, damageSource)

    @Deprecated("Deprecated in Java")
    override fun getItemDamageSource(attacker: LivingEntity): DamageSource? =
        wrappedItem.getItemDamageSource(attacker)

    override fun hurtEnemy(
        itemStack: ItemStack,
        mob: LivingEntity,
        attacker: LivingEntity
    ) = wrappedItem.hurtEnemy(itemStack, mob, attacker)

    override fun postHurtEnemy(
        itemStack: ItemStack,
        mob: LivingEntity,
        attacker: LivingEntity
    ) = wrappedItem.postHurtEnemy(itemStack, mob, attacker)

    override fun mineBlock(
        itemStack: ItemStack,
        level: Level,
        state: BlockState,
        pos: BlockPos,
        owner: LivingEntity
    ): Boolean = wrappedItem.mineBlock(itemStack, level, state, pos, owner)

    override fun isCorrectToolForDrops(
        itemStack: ItemStack,
        state: BlockState
    ): Boolean = wrappedItem.isCorrectToolForDrops(itemStack, state)

    override fun interactLivingEntity(
        itemStack: ItemStack,
        player: Player,
        target: LivingEntity,
        type: InteractionHand
    ) = wrappedItem.interactLivingEntity(itemStack, player, target, type)

    override fun getCraftingRemainder(): ItemStackTemplate? =
        wrappedItem.craftingRemainder

    override fun inventoryTick(
        itemStack: ItemStack,
        level: ServerLevel,
        owner: Entity,
        slot: EquipmentSlot?
    ) = wrappedItem.inventoryTick(itemStack, level, owner, slot)

    override fun onCraftedBy(itemStack: ItemStack, player: Player) =
        wrappedItem.onCraftedBy(itemStack, player)

    override fun onCraftedPostProcess(
        itemStack: ItemStack,
        level: Level
    ) = wrappedItem.onCraftedPostProcess(itemStack, level)

    override fun getUseAnimation(itemStack: ItemStack): ItemUseAnimation =
        wrappedItem.getUseAnimation(itemStack)

    override fun getUseDuration(itemStack: ItemStack, user: LivingEntity): Int =
        wrappedItem.getUseDuration(itemStack, user)

    override fun releaseUsing(
        itemStack: ItemStack,
        level: Level,
        entity: LivingEntity,
        remainingTime: Int
    ): Boolean = wrappedItem.releaseUsing(itemStack, level, entity, remainingTime)

    @Deprecated("Deprecated in Java")
    override fun appendHoverText(
        itemStack: ItemStack,
        context: Item.TooltipContext,
        display: TooltipDisplay,
        builder: Consumer<Component>,
        tooltipFlag: TooltipFlag
    ) = wrappedItem.appendHoverText(itemStack, context, display, builder, tooltipFlag)

    override fun getTooltipImage(itemStack: ItemStack): Optional<TooltipComponent> =
        wrappedItem.getTooltipImage(itemStack)

    override fun getName(itemStack: ItemStack): Component =
        wrappedItem.getName(itemStack)

    override fun isFoil(itemStack: ItemStack): Boolean = wrappedItem.isFoil(itemStack)

    override fun useOnRelease(itemStack: ItemStack): Boolean = wrappedItem.useOnRelease(itemStack)

    override fun canFitInsideContainerItems(): Boolean = wrappedItem.canFitInsideContainerItems()

    override fun shouldPrintOpWarning(stack: ItemStack, player: Player?): Boolean =
        wrappedItem.shouldPrintOpWarning(stack, player)

}
