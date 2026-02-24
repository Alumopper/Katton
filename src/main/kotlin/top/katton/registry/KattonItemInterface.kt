package top.katton.registry

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.flag.FeatureElement
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.*
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.function.Consumer

interface KattonItemInterface : ItemLike, FeatureElement, Identifiable  {

    val properties: KattonItemProperties

    override val id: Identifier
        get() = properties.id

    fun components(): DataComponentMap = properties.buildComponent()

    fun getDefaultMaxStackSize(): Int = components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1)

    fun onUseTick(level: Level, livingEntity: LivingEntity, itemStack: ItemStack, ticksRemaining: Int) {}

    fun onDestroyed(itemEntity: ItemEntity) {}

    fun canDestroyBlock(
        itemStack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        user: LivingEntity
    ): Boolean {
        val tool = itemStack.get(DataComponents.TOOL)
        if (tool != null && !tool.canDestroyBlocksInCreative()) {
            return if (user is Player && user.abilities.instabuild) {
                false
            } else {
                true
            }
        }
        return true
    }

    override fun asItem(): Item

    fun useOn(context: UseOnContext): InteractionResult = InteractionResult.PASS

    fun getDestroySpeed(itemStack: ItemStack, state: BlockState): Float {
        val tool = itemStack.get(DataComponents.TOOL)
        return tool?.getMiningSpeed(state) ?: 1.0f
    }

    fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        val consumable = stack.get(DataComponents.CONSUMABLE)
        if (consumable != null) {
            return consumable.startConsuming(player, stack, hand)
        }

        val equippable = stack.get(DataComponents.EQUIPPABLE)
        if (equippable != null && equippable.swappable()) {
            return equippable.swapWithEquipmentSlot(stack, player)
        }

        if (stack.has(DataComponents.BLOCKS_ATTACKS)) {
            player.startUsingItem(hand)
            return InteractionResult.CONSUME
        }

        val kineticWeapon = stack.get(DataComponents.KINETIC_WEAPON)
        if (kineticWeapon != null) {
            player.startUsingItem(hand)
            kineticWeapon.makeSound(player)
            return InteractionResult.CONSUME
        }

        return InteractionResult.PASS
    }

    fun finishUsingItem(itemStack: ItemStack, level: Level, entity: LivingEntity): ItemStack {
        val consumable = itemStack.get(DataComponents.CONSUMABLE)
        return consumable?.onConsume(level, entity, itemStack) ?: itemStack
    }

    fun isBarVisible(stack: ItemStack): Boolean = stack.isDamaged

    fun getBarWidth(stack: ItemStack): Int =
        ((13.0f - stack.damageValue.toFloat() * 13.0f / stack.maxDamage.toFloat()).toInt()).coerceIn(0, 13)

    fun getBarColor(stack: ItemStack): Int {
        val maxDamage = stack.maxDamage
        val healthPercentage = ((maxDamage - stack.damageValue).toFloat() / maxDamage.toFloat()).coerceAtLeast(0.0f)
        return net.minecraft.util.Mth.hsvToRgb(healthPercentage / 3.0f, 1.0f, 1.0f)
    }

    fun overrideStackedOnOther(
        self: ItemStack,
        slot: Slot,
        clickAction: ClickAction,
        player: Player
    ): Boolean = false

    fun overrideOtherStackedOnMe(
        self: ItemStack,
        other: ItemStack,
        slot: Slot,
        clickAction: ClickAction,
        player: Player,
        carriedItem: SlotAccess
    ): Boolean = false

    fun getAttackDamageBonus(victim: Entity, damage: Float, damageSource: DamageSource): Float = 0.0f

    @Deprecated("Deprecated in Java")
    fun getItemDamageSource(attacker: LivingEntity): DamageSource? = null

    fun hurtEnemy(itemStack: ItemStack, mob: LivingEntity, attacker: LivingEntity) {}

    fun postHurtEnemy(itemStack: ItemStack, mob: LivingEntity, attacker: LivingEntity) {}

    fun mineBlock(
        itemStack: ItemStack,
        level: Level,
        state: BlockState,
        pos: BlockPos,
        owner: LivingEntity
    ): Boolean {
        val tool = itemStack.get(DataComponents.TOOL) ?: return false

        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0f && tool.damagePerBlock() > 0) {
            itemStack.hurtAndBreak(tool.damagePerBlock(), owner, EquipmentSlot.MAINHAND)
        }
        return true
    }

    fun isCorrectToolForDrops(itemStack: ItemStack, state: BlockState): Boolean {
        val tool = itemStack.get(DataComponents.TOOL)
        return tool != null && tool.isCorrectForDrops(state)
    }

    fun interactLivingEntity(
        itemStack: ItemStack,
        player: Player,
        target: LivingEntity,
        type: InteractionHand
    ): InteractionResult = InteractionResult.PASS

    fun getCraftingRemainder(): ItemStackTemplate? = null

    fun inventoryTick(itemStack: ItemStack, level: ServerLevel, owner: Entity, slot: EquipmentSlot?) {}

    fun onCraftedBy(itemStack: ItemStack, player: Player) {
        onCraftedPostProcess(itemStack, player.level())
    }

    fun onCraftedPostProcess(itemStack: ItemStack, level: Level) {}

    fun getUseAnimation(itemStack: ItemStack): ItemUseAnimation {
        val consumable = itemStack.get(DataComponents.CONSUMABLE)
        if (consumable != null) {
            return consumable.animation()
        }

        if (itemStack.has(DataComponents.BLOCKS_ATTACKS)) {
            return ItemUseAnimation.BLOCK
        }

        return if (itemStack.has(DataComponents.KINETIC_WEAPON)) {
            ItemUseAnimation.SPEAR
        } else {
            ItemUseAnimation.NONE
        }
    }

    fun getUseDuration(itemStack: ItemStack, user: LivingEntity): Int {
        val consumable = itemStack.get(DataComponents.CONSUMABLE)
        if (consumable != null) {
            return consumable.consumeTicks()
        }

        return if (!itemStack.has(DataComponents.BLOCKS_ATTACKS) && !itemStack.has(DataComponents.KINETIC_WEAPON)) {
            0
        } else {
            72000
        }
    }

    fun releaseUsing(itemStack: ItemStack, level: Level, entity: LivingEntity, remainingTime: Int): Boolean = false

    @Deprecated("Deprecated in Java")
    fun appendHoverText(
        itemStack: ItemStack,
        context: Item.TooltipContext,
        display: TooltipDisplay,
        builder: Consumer<Component>,
        tooltipFlag: TooltipFlag
    ) {}

    fun getTooltipImage(itemStack: ItemStack): Optional<TooltipComponent> = Optional.empty()

    @VisibleForTesting
    fun getDescriptionId(): String

    fun getName(itemStack: ItemStack): Component =
        itemStack.components.getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY)

    fun isFoil(itemStack: ItemStack): Boolean = itemStack.isEnchanted

    fun useOnRelease(itemStack: ItemStack): Boolean = false

    fun getDefaultInstance(): ItemStack = ItemStack(Holder.direct(asItem()), 1).apply { try { applyComponents(components()) } catch (_: Throwable) { } }

    fun canFitInsideContainerItems(): Boolean = true

    override fun requiredFeatures(): FeatureFlagSet

    fun shouldPrintOpWarning(stack: ItemStack, player: Player?): Boolean = false
}