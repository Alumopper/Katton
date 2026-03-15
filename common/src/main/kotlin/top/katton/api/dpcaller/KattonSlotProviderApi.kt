package top.katton.api.dpcaller

import net.minecraft.world.Container
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.SlotProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

/**
 * Slot provider API for inventory slot operations.
 *
 * This module provides convenient operator syntax for accessing and modifying
 * inventory slots, as well as predefined slot constants for common equipment
 * and inventory positions.
 */

/**
 * Get an item from a container slot.
 */
operator fun Container.get(slot: Int): ItemStack = this.getItem(slot)

/**
 * Set an item into a container slot.
 */
operator fun Container.set(slot: Int, itemStack: ItemStack) = this.setItem(slot, itemStack)

/**
 * Remove an item from an inventory.
 */
operator fun Inventory.minusAssign(itemStack: ItemStack) = this.removeItem(itemStack)

/**
 * Add an item to an inventory.
 */
operator fun Inventory.plusAssign(itemStack: ItemStack) {
    this.add(itemStack)
}

/**
 * Get an item from a slot provider at a KattonItemSlot.
 */
operator fun SlotProvider.get(slot: KattonItemCollection.KattonItemSlot): ItemStack? =
    getSlot(slot.index)?.get()

/**
 * Set an item into a slot provider at a KattonItemSlot.
 */
operator fun SlotProvider.set(slot: KattonItemCollection.KattonItemSlot, itemStack: ItemStack) {
    getSlot(slot.index)?.set(itemStack)
}

/**
 * Get items from multiple slots in a container.
 */
operator fun Container.get(slots: List<KattonItemCollection.KattonItemSlot>): List<ItemStack?> =
    slots.map { this[it] }

/**
 * Extension property to access a container's slots.
 */
val Container.slots: KattonItemCollection
    get() = KattonItemCollection(this)

/**
 * Collection of item slots with convenient access patterns.
 *
 * @property container The underlying Container
 */
class KattonItemCollection(val container: net.minecraft.world.Container) {

    /**
     * Get a SlotAccess for a specific slot.
     */
    operator fun get(slot: KattonItemSlot): SlotAccess? = container.getSlot(slot.index)

    /**
     * Get SlotAccesses for multiple slots.
     */
    operator fun get(slots: List<KattonItemSlot>): List<SlotAccess?> = slots.map { this[it] }

    /**
     * Represents a single item slot by index.
     */
    open class KattonItemSlot(
        val index: Int
    )

    /**
     * Interface for groups of item slots.
     */
    interface KattonItemSlotGroup {
        val any: List<KattonItemSlot>
    }

    /**
     * A list of consecutive item slots.
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
     * The first content slot.
     */
    object Contents : KattonItemSlot(0)

    /**
     * All container slots (0-53).
     */
    object Container : KattonItemSlotList(0, 54)

    /**
     * Hotbar slots (0-8).
     */
    object Hotbar : KattonItemSlotList(0, 9)

    /**
     * Main inventory slots (9-35).
     */
    object Inventory : KattonItemSlotList(9, 27)

    /**
     * Ender chest slots (200-226).
     */
    object EnderChest : KattonItemSlotList(200, 27)

    /**
     * Mob inventory slots (300-307).
     */
    object MobInventory : KattonItemSlotList(300, 8)

    /**
     * Horse inventory slots (500-514).
     */
    object Horse : KattonItemSlotList(500, 15)

    /**
     * Weapon slots (main hand and off hand).
     */
    object Weapon : KattonItemSlotGroup {
        /**
         * Main hand slot.
         */
        object MainHand : KattonItemSlot(EquipmentSlot.MAINHAND.getIndex(98))

        /**
         * Off hand slot.
         */
        object OffHand : KattonItemSlot(EquipmentSlot.OFFHAND.getIndex(98))

        override val any: List<KattonItemSlot> = listOf(MainHand, OffHand)
    }

    /**
     * Armor slots (head, chest, legs, feet).
     */
    object Armor : KattonItemSlotGroup {
        /**
         * Head armor slot.
         */
        object Head : KattonItemSlot(EquipmentSlot.HEAD.getIndex(100))

        /**
         * Chest armor slot.
         */
        object Chest : KattonItemSlot(EquipmentSlot.CHEST.getIndex(100))

        /**
         * Leg armor slot.
         */
        object Legs : KattonItemSlot(EquipmentSlot.LEGS.getIndex(100))

        /**
         * Feet armor slot.
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
