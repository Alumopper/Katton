package top.katton.api

import net.minecraft.world.Container
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.SlotProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

operator fun Container.get(slot: Int): ItemStack = this.getItem(slot)
operator fun Container.set(slot: Int, itemStack: ItemStack) = this.setItem(slot, itemStack)
operator fun Inventory.minusAssign(itemStack: ItemStack) = this.removeItem(itemStack)
operator fun Inventory.plusAssign(itemStack: ItemStack) {
    this.add(itemStack)
}

operator fun SlotProvider.get(slot: KattonItemCollection.KattonItemSlot): ItemStack? =
    getSlot(slot.index)?.get()
operator fun SlotProvider.set(slot: KattonItemCollection.KattonItemSlot, itemStack: ItemStack) {
    getSlot(slot.index)?.set(itemStack)
}
operator fun Container.get(slots: List<KattonItemCollection.KattonItemSlot>): List<ItemStack?> =
    slots.map { this[it] }

val Container.slots: KattonItemCollection
    get() = KattonItemCollection(this)

class KattonItemCollection(val container: net.minecraft.world.Container) {

    operator fun get(slot: KattonItemSlot): SlotAccess? = container.getSlot(slot.index)
    operator fun get(slots: List<KattonItemSlot>): List<SlotAccess?> = slots.map { this[it] }

    open class KattonItemSlot(
        val index: Int
    )

    interface KattonItemSlotGroup {
        val any: List<KattonItemSlot>
    }

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

    object Contents : KattonItemSlot(0)
    object Container : KattonItemSlotList(0, 54)
    object Hotbar : KattonItemSlotList(0, 9)
    object Inventory : KattonItemSlotList(9, 27)
    object EnderChest : KattonItemSlotList(200, 27)
    object MobInventory : KattonItemSlotList(300, 8)
    object Horse : KattonItemSlotList(500, 15)

    object Weapon : KattonItemSlotGroup {
        object MainHand : KattonItemSlot(EquipmentSlot.MAINHAND.getIndex(98))
        object OffHand : KattonItemSlot(EquipmentSlot.OFFHAND.getIndex(98))

        override val any: List<KattonItemSlot> = listOf(MainHand, OffHand)
    }

    object Armor : KattonItemSlotGroup {
        object Head : KattonItemSlot(EquipmentSlot.HEAD.getIndex(100))
        object Chest : KattonItemSlot(EquipmentSlot.CHEST.getIndex(100))
        object Legs : KattonItemSlot(EquipmentSlot.LEGS.getIndex(100))
        object Feet : KattonItemSlot(EquipmentSlot.FEET.getIndex(100))
        object Body : KattonItemSlot(EquipmentSlot.BODY.getIndex(105))

        override val any: List<KattonItemSlot> = listOf(Head, Chest, Legs, Feet, Body)
    }

    object Saddle : KattonItemSlot(EquipmentSlot.SADDLE.getIndex(106))
    object HorseChest : KattonItemSlot(499)
    object PlayerCursor : KattonItemSlot(499)
    object PlayerCrafting : KattonItemSlotList(500, 4)


}
