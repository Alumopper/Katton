package top.katton.registry

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentInitializers
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import top.katton.api.server
import top.katton.registry.kattonId
import java.util.function.Consumer


class KattonItemProperties(
    override val id: Identifier,
) : Item.Properties(), Identifiable {

    init {
        // Do not call setId here. Setting the internal Item.Properties id will trigger
        // intrusive holder creation when Item(properties) is constructed. We keep the
        // logical identifier in the constructor parameter `id` and apply the actual
        // resource key during registration time instead.
    }


    private var _name: Component? = null
    var name: Component
        get() = _name ?: Component.translatable("item.${id.namespace}.${id.path}")
        set(value) {
            _name = value
        }

    fun setName(name: Component): KattonItemProperties {
        this.name = name
        return this
    }

    private var _model: Identifier? = null
    var model: Identifier
        get() = _model ?: id.withPath("item/${id.path}")
        set(value) {
            _model = value
        }

    fun setModel(model: Identifier): KattonItemProperties {
        this.model = model
        return this
    }

    private fun finalizeInitializer(
        name: Component,
        model: Identifier
    ): DataComponentInitializers.Initializer<Item> {
        return this.componentInitializer.andThen { components, context, key ->
            components
                .set(DataComponents.ITEM_NAME, name)
                .set(DataComponents.ITEM_MODEL, model)
                .addValidator { map ->
                    check(
                        !(map.has(DataComponents.DAMAGE) && map.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1)
                    ) { "Item cannot have both durability and be stackable" }
                }
        }
    }

    fun buildComponent(): DataComponentMap {
        val mapBuilder = DataComponentMap.builder()
        
        // 始终首先设置名称和材质，确保这些基本组件不会丢失
        val actualName = name
        val actualModel = model
        mapBuilder.set(DataComponents.ITEM_NAME, actualName)
        mapBuilder.set(DataComponents.ITEM_MODEL, actualModel)
        
        server?.let {
            val initializer = finalizeInitializer(actualName, actualModel)
            // itemIdOrThrow may rely on properties set by Item.Properties.setId.
            // For dynamically created native items we will call the initializer at registration time
            // with the actual resource key. Here we only attempt initialization if the properties
            // already contain a valid id mapping (e.g. global registrations).
            try {
                val itemId = this@KattonItemProperties.itemIdOrThrow()
                initializer.run(mapBuilder, it.registryAccess(), itemId)
            } catch (_: Throwable) {
                // 忽略异常，名称和材质已经设置
            }
        }
        return mapBuilder.build()
    }

    companion object {
        fun components(id: Identifier) = KattonItemProperties(id)
    }

}


val ItemStack.kattonId: Identifier
    get() = this.get(KattonRegistry.COMPONENTS.KATTON_ID)?.let { id(it) } ?: KattonRegistry.ITEMS.default.id

val ItemStack.kattonItemEntry: KattonRegistry.KattonItemEntry
    get() =  KattonRegistry.ITEMS[kattonId] ?: KattonRegistry.ITEMS.default

val ItemStack.kattonAgentItem: KattonItemInterface
    get() = kattonItemEntry.agentItem
        ?: KattonRegistry.ITEMS.default.agentItem
        ?: error("Katton default item is missing agentItem")

val ItemStack.kattonActualItem: Item
    get() = kattonItemEntry.item

open class KattonItem(
    val properties: KattonItemProperties
) : Item(properties), Identifiable {

    private val components = properties.buildComponent()
    override val id: Identifier
        get() = properties.id


    override fun components(): DataComponentMap {
        return components
    }

    override fun getDefaultInstance(): ItemStack {
        return ItemStack(Holder.direct(KattonRegistry.ITEMS.getOrDefault(id).item), 1).apply {
            try { applyComponents(components()) } catch (_: Throwable) { }
        }
    }

    override fun canDestroyBlock(
        itemStack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        user: LivingEntity
    ): Boolean = itemStack.kattonAgentItem.canDestroyBlock(itemStack, state, level, pos, user)

    override fun onUseTick(level: Level, livingEntity: LivingEntity, itemStack: ItemStack, ticksRemaining: Int) {
        itemStack.kattonAgentItem.onUseTick(level, livingEntity, itemStack, ticksRemaining)
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val stack = context.itemInHand
        return stack.kattonAgentItem.useOn(context)
    }

    override fun getDestroySpeed(itemStack: ItemStack, state: BlockState): Float =
        itemStack.kattonAgentItem.getDestroySpeed(itemStack, state)

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        return stack.kattonAgentItem.use(level, player, hand)
    }

    override fun finishUsingItem(itemStack: ItemStack, level: Level, entity: LivingEntity): ItemStack =
        itemStack.kattonAgentItem.finishUsingItem(itemStack, level, entity)

    override fun isBarVisible(stack: ItemStack): Boolean =
        stack.kattonAgentItem.isBarVisible(stack)

    override fun getBarWidth(stack: ItemStack): Int =
        stack.kattonAgentItem.getBarWidth(stack)

    override fun getBarColor(stack: ItemStack): Int =
        stack.kattonAgentItem.getBarColor(stack)

    override fun hurtEnemy(itemStack: ItemStack, mob: LivingEntity, attacker: LivingEntity) {
        itemStack.kattonAgentItem.hurtEnemy(itemStack, mob, attacker)
    }

    override fun postHurtEnemy(itemStack: ItemStack, mob: LivingEntity, attacker: LivingEntity) {
        itemStack.kattonAgentItem.postHurtEnemy(itemStack, mob, attacker)
    }

    override fun mineBlock(
        itemStack: ItemStack,
        level: Level,
        state: BlockState,
        pos: BlockPos,
        owner: LivingEntity
    ): Boolean = itemStack.kattonAgentItem.mineBlock(itemStack, level, state, pos, owner)

    override fun isCorrectToolForDrops(itemStack: ItemStack, state: BlockState): Boolean =
        itemStack.kattonAgentItem.isCorrectToolForDrops(itemStack, state)

    override fun inventoryTick(itemStack: ItemStack, level: ServerLevel, owner: Entity, slot: EquipmentSlot?) {
        itemStack.kattonAgentItem.inventoryTick(itemStack, level, owner, slot)
    }

    override fun onCraftedBy(itemStack: ItemStack, player: Player) {
        itemStack.kattonAgentItem.onCraftedBy(itemStack, player)
    }

    override fun onCraftedPostProcess(itemStack: ItemStack, level: Level) {
        itemStack.kattonAgentItem.onCraftedPostProcess(itemStack, level)
    }

    override fun getUseAnimation(itemStack: ItemStack): ItemUseAnimation =
        itemStack.kattonAgentItem.getUseAnimation(itemStack)

    override fun getUseDuration(itemStack: ItemStack, user: LivingEntity): Int =
        itemStack.kattonAgentItem.getUseDuration(itemStack, user)

    override fun releaseUsing(itemStack: ItemStack, level: Level, entity: LivingEntity, remainingTime: Int): Boolean =
        itemStack.kattonAgentItem.releaseUsing(itemStack, level, entity, remainingTime)

    @Deprecated("Deprecated in Java")
    override fun appendHoverText(
        itemStack: ItemStack,
        context: Item.TooltipContext,
        display: TooltipDisplay,
        builder: Consumer<Component>,
        tooltipFlag: TooltipFlag
    ) {
        itemStack.kattonAgentItem.appendHoverText(itemStack, context, display, builder, tooltipFlag)
    }

    override fun useOnRelease(itemStack: ItemStack): Boolean =
        itemStack.kattonAgentItem.useOnRelease(itemStack)

}
