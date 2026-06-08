package top.katton.api.dpcaller

import net.minecraft.world.Container
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.SlotProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

/**
 * %en
 * Slot provider API for inventory slot operations.
 *
 * This module provides convenient operator syntax for accessing and modifying
 * inventory slots, as well as predefined slot constants for common equipment
 * and inventory positions.
 *
 * %zh
 * 背包槽位操作的 SlotProvider API。
 * 本模块提供便捷的运算符语法，用于访问和修改背包槽位，
 * 并提供常见装备与背包位置的预定义槽位常量。
 */

/**
 * %en
 * Get an item from a container slot.
 *
 * %zh
 * 从容器槽位中获取物品。
 */
operator fun Container.get(slot: Int): ItemStack = this.getItem(slot)

/**
 * %en
 * Set an item into a container slot.
 *
 * %zh
 * 将物品设置到容器槽位中。
 */
operator fun Container.set(slot: Int, itemStack: ItemStack) = this.setItem(slot, itemStack)

/**
 * %en
 * Remove an item from an inventory.
 *
 * %zh
 * 从背包中移除物品。
 */
operator fun Inventory.minusAssign(itemStack: ItemStack) = this.removeItem(itemStack)

/**
 * %en
 * Add an item to an inventory.
 *
 * %zh
 * 向背包添加物品。
 */
operator fun Inventory.plusAssign(itemStack: ItemStack) {
    this.add(itemStack)
}

/**
 * %en
 * Get an item from a slot provider at a KattonItemSlot.
 *
 * %zh
 * 从槽位提供器的 KattonItemSlot 中获取物品。
 */
operator fun SlotProvider.get(slot: KattonItemCollection.KattonItemSlot): ItemStack? =
    getSlot(slot.index)?.get()

/**
 * %en
 * Set an item into a slot provider at a KattonItemSlot.
 *
 * %zh
 * 将物品设置到槽位提供器的 KattonItemSlot 中。
 */
operator fun SlotProvider.set(slot: KattonItemCollection.KattonItemSlot, itemStack: ItemStack) {
    getSlot(slot.index)?.set(itemStack)
}

/**
 * %en
 * Get items from multiple slots in a container.
 *
 * %zh
 * 从容器的多个槽位中获取物品。
 */
operator fun Container.get(slots: List<KattonItemCollection.KattonItemSlot>): List<ItemStack?> =
    slots.map { this[it] }

/**
 * %en
 * Extension property to access a container's slots.
 *
 * %zh
 * 用于访问容器槽位的扩展属性。
 */
val Container.slots: KattonItemCollection
    get() = KattonItemCollection(this)

/**
 * %en
 * Collection of item slots with convenient access patterns.
 *
 * %zh
 * 提供便捷访问方式的物品槽位集合。
 * @property container
 * %en The underlying Container
 * %zh 底层 Container。
 */
class KattonItemCollection(val container: net.minecraft.world.Container) {

    /**
 * %en
 * Get a SlotAccess for a specific slot.
 *
 * %zh
 * 获取指定槽位的 SlotAccess。
 */
    operator fun get(slot: KattonItemSlot): SlotAccess? = container.getSlot(slot.index)

    /**
 * %en
 * Get SlotAccesses for multiple slots.
 *
 * %zh
 * 获取多个槽位的 SlotAccess。
 */
    operator fun get(slots: List<KattonItemSlot>): List<SlotAccess?> = slots.map { this[it] }

    /**
 * %en
 * Represents a single item slot by index.
 *
 * %zh
 * 通过索引表示单个物品槽位。
 */
    open class KattonItemSlot(
        val index: Int
    )

    /**
 * %en
 * Interface for groups of item slots.
 *
 * %zh
 * 物品槽位组接口。
 */
    interface KattonItemSlotGroup {
        val any: List<KattonItemSlot>
    }

    /**
 * %en
 * A list of consecutive item slots.
 *
 * %zh
 * 连续物品槽位列表。
 */
    open class KattonItemSlotList(
        val offset: Int,
        size: Int
    ) : Iterable<KattonItemSlot>, KattonItemSlotGroup {
        private val delegate: MutableList<KattonItemSlot> = mutableListOf()
        override val any: List<KattonItemSlot>
            get() = delegate

        init {
            repeat(size) { i ->
                delegate.add(KattonItemSlot(offset + i))
            }
        }

        operator fun get(index: Int): KattonItemSlot = delegate[index % delegate.size]

        override fun iterator(): MutableIterator<KattonItemSlot> = delegate.iterator()

    }

    /**
 * %en
 * The first content slot.
 *
 * %zh
 * 第一个内容槽位。
 */
    object Contents : KattonItemSlot(0)

    /**
 * %en
 * All container slots (0-53).
 *
 * %zh
 * 全部容器槽位（0-53）。
 */
    object Container : KattonItemSlotList(0, 54)

    /**
 * %en
 * Hotbar slots (0-8).
 *
 * %zh
 * 快捷栏槽位（0-8）。
 */
    object Hotbar : KattonItemSlotList(0, 9)

    /**
 * %en
 * Main inventory slots (9-35).
 *
 * %zh
 * 主背包槽位（9-35）。
 */
    object Inventory : KattonItemSlotList(9, 27)

    /**
 * %en
 * Ender chest slots (200-226).
 *
 * %zh
 * 末影箱槽位（200-226）。
 */
    object EnderChest : KattonItemSlotList(200, 27)

    /**
 * %en
 * Mob inventory slots (300-307).
 *
 * %zh
 * 生物背包槽位（300-307）。
 */
    object MobInventory : KattonItemSlotList(300, 8)

    /**
 * %en
 * Horse inventory slots (500-514).
 *
 * %zh
 * 马背包槽位（500-514）。
 */
    object Horse : KattonItemSlotList(500, 15)

    /**
 * %en
 * Weapon slots (main hand and off hand).
 *
 * %zh
 * 武器槽位（主手和副手）。
 */
    object Weapon : KattonItemSlotGroup {
        /**
 * %en
 * Main hand slot.
 *
 * %zh
 * 主手槽位。
 */
        object MainHand : KattonItemSlot(EquipmentSlot.MAINHAND.getIndex(98))

        /**
 * %en
 * Off hand slot.
 *
 * %zh
 * 副手槽位。
 */
        object OffHand : KattonItemSlot(EquipmentSlot.OFFHAND.getIndex(98))

        override val any: List<KattonItemSlot> = listOf(MainHand, OffHand)
    }

    /**
 * %en
 * Armor slots (head, chest, legs, feet).
 *
 * %zh
 * 护甲槽位（头盔、胸甲、护腿、靴子）。
 */
    object Armor : KattonItemSlotGroup {
        /**
 * %en
 * Head armor slot.
 *
 * %zh
 * 头盔槽位。
 */
        object Head : KattonItemSlot(EquipmentSlot.HEAD.getIndex(100))

        /**
 * %en
 * Chest armor slot.
 *
 * %zh
 * 胸甲槽位。
 */
        object Chest : KattonItemSlot(EquipmentSlot.CHEST.getIndex(100))

        /**
 * %en
 * Leg armor slot.
 *
 * %zh
 * 护腿槽位。
 */
        object Legs : KattonItemSlot(EquipmentSlot.LEGS.getIndex(100))

        /**
 * %en
 * Feet armor slot.
 *
 * %zh
 * 靴子槽位。
 */
        object Feet : KattonItemSlot(EquipmentSlot.FEET.getIndex(100))
        object Body : KattonItemSlot(EquipmentSlot.BODY.getIndex(105))

        override val any: List<KattonItemSlot> = listOf(Head, Chest, Legs, Feet, Body)
    }

    object Saddle : KattonItemSlot(EquipmentSlot.SADDLE.getIndex(106))
    object HorseChest : KattonItemSlot(499)
    object PlayerCursor : KattonItemSlot(499)
    object PlayerCrafting : KattonItemSlotList(500, 4)


}
