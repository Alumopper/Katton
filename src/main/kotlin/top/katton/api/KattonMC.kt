package top.katton.api

import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import net.minecraft.world.Container
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.phys.AABB
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard
import java.util.UUID
import java.util.function.IntFunction

@Suppress("unused")
object KattonMC {
    val server: MinecraftServer
        get() = requireServer()

    fun execute(command: String) = executeCommandAsServer(command)
    class KattonPlayerList(
        val playerList: PlayerList
    ): List<ServerPlayer> by playerList.players {
        operator fun get(name: String): ServerPlayer? {
            return playerList.getPlayer(name)
        }

        operator fun get(uuid: UUID): ServerPlayer? {
            return playerList.getPlayer(uuid)
        }
    }
    val players: KattonPlayerList
        get() = KattonPlayerList(server.playerList)

    class KattonLevelMap(
        private val server: MinecraftServer
    ): Map<ResourceKey<Level>, ServerLevel> {
        data class LevelEntry(
            override val key: ResourceKey<Level>,
            override val value: ServerLevel
        ) : Map.Entry<ResourceKey<Level>, ServerLevel>
        override val size: Int
            get() = server.levelKeys().size
        override val keys: Set<ResourceKey<Level>>
            get() = server.levelKeys()
        override val values: Collection<ServerLevel>
            get() = server.allLevels.toList()
        override val entries: Set<Map.Entry<ResourceKey<Level>, ServerLevel>>
            get() = server.levelKeys().map { LevelEntry(it, server.getLevel(it)!!) }.toSet()

        override fun isEmpty(): Boolean = server.levelKeys().isEmpty()

        override fun containsKey(key: ResourceKey<Level>): Boolean = server.levelKeys().contains(key)

        override fun containsValue(value: ServerLevel): Boolean = values.contains(value)

        override operator fun get(key: ResourceKey<Level>): ServerLevel? {
            return server.getLevel(key)
        }
        val overworld: ServerLevel
            get() = get(Level.OVERWORLD) ?: error("Overworld not found")
        val nether: ServerLevel
            get() = get(Level.NETHER) ?: error("Nether not found")
        val end: ServerLevel
            get() = get(Level.END) ?: error("End not found")
    }
    val levels: KattonLevelMap
        get() = KattonLevelMap(server)

    class KattonLevelEntityCollection(
        val level: ServerLevel
    ): Iterable<Entity> by level.allEntities {
        operator fun <T : Entity> get(
            entityTypeTest: EntityTypeTest<Entity, T>,
            predicate: (T) -> Boolean = { true }
        ): List<T> = level.getEntities(entityTypeTest, predicate)

        operator fun <T : Entity> get(
            entityTypeTest: EntityTypeTest<Entity, T>,
            aabb: AABB,
            predicate: (T) -> Boolean = { true }
        ): List<T> = level.getEntities(entityTypeTest, aabb, predicate)

        operator fun get(selector: EntitySelector): List<Entity> {
            return findEntities(level, selector)
        }
        operator fun get(uuid: UUID): Entity? {
            return level.getEntity(uuid)
        }
    }
    class KattonLevelPlayerCollection(
        val level: ServerLevel
    ): List<ServerPlayer> by level.players {
        operator fun get(uuid: UUID): Player? {
            return level.getPlayerByUUID(uuid)
        }
    }
    val ServerLevel.players: KattonLevelPlayerCollection
        get() = KattonLevelPlayerCollection(this)

    val ServerLevel.entities: KattonLevelEntityCollection
        get() = KattonLevelEntityCollection(this)

    class KattonServerEntityCollection(
        private val server: MinecraftServer
    ){
        val all
            get() = server.allLevels.flatMap { it.allEntities }

        operator fun get(level: ServerLevel):KattonLevelEntityCollection {
            return KattonLevelEntityCollection(level)
        }

        operator fun get(uuid: UUID): Entity? {
            return server.allLevels.map { it.getEntity(uuid) }.firstOrNull()
        }
    }
    val entities: KattonServerEntityCollection
        get() = KattonServerEntityCollection(server)

    var Entity.nbt: CompoundTag
        get() = getEntityNbt(this)
        set(value) {
            setEntityNbt(this, value)
        }

    var BlockEntity.nbt: CompoundTag
        get() = getBlockNbt(this)
        set(value) {
            setBlockNbt(this, value)
        }
    class KattonLevelBlockEntityCollection(
        val level: Level
    ) {
        operator fun get(blockPos: BlockPos): BlockEntity? {
            return level.getBlockEntity(blockPos)
        }
        operator fun set(blockPos: BlockPos, blockEntity: BlockEntity) {
            if (blockEntity.blockPos == blockPos) {
                level.setBlockEntity(blockEntity)
            }
        }
        fun set(blockEntity: BlockEntity) {
            level.setBlockEntity(blockEntity)
        }
    }
    val ServerLevel.blockEntities: KattonLevelBlockEntityCollection
        get() = KattonLevelBlockEntityCollection(this)

    val storage
        get() = server.commandStorage

    val scoreboard
        get() = server.scoreboard


    operator fun Scoreboard.get(target: ScoreHolder, objective: Objective): Int?
        = scoreboard.getPlayerScoreInfo(target, objective)?.value()

    operator fun Scoreboard.set(target: ScoreHolder, objective: Objective, value: Int)
        = scoreboard.getOrCreatePlayerScore(target, objective).set(value)

    fun fake(name: String) = ScoreHolder.forNameOnly(name)

    class KattonScoreHolderScoreCollection(
        val scoreboard: Scoreboard,
        val scoreHolder: ScoreHolder
    ) {
        operator fun get(objective: Objective): Int? {
            return scoreboard.getPlayerScoreInfo(scoreHolder, objective)?.value()
        }
        operator fun set(objective: Objective, value: Int) {
            scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(value)
        }
    }
    val ScoreHolder.scores: KattonScoreHolderScoreCollection
        get() = KattonScoreHolderScoreCollection(scoreboard, this)
    class KattonEntityAttributeValueMap(
        val entity: LivingEntity
    ) {
        fun contains(holder: Holder<Attribute>): Boolean {
            return entity.getAttribute(holder) != null
        }
        operator fun get(holder: Holder<Attribute>): Double? {
            return entity.getAttributeValue(holder)
        }
        fun set(holder: Holder<Attribute>, value: Double, vararg modifiers: AttributeModifier) {
            entity.getAttribute(holder)?.baseValue?.let {
                it != value
            } ?: false
            modifiers.forEach {
                entity.getAttribute(holder)?.addTransientModifier(it)
            }
        }
    }
    val LivingEntity.attributeValues
        get() = KattonEntityAttributeValueMap(this)
    operator fun Inventory.get(slot: Int): ItemStack = this.getItem(slot)
    operator fun Inventory.set(slot: Int, itemStack: ItemStack) = this.setItem(slot, itemStack)
    operator fun Inventory.minusAssign(itemStack: ItemStack) = this.removeItem(itemStack)
    operator fun Inventory.plusAssign(itemStack: ItemStack) {
        this.add(itemStack)
    }

    operator fun Container.get(slot: KattonItemCollection.KattonItemSlot): SlotAccess? =
        getSlot(slot.index)
    operator fun Container.get(slot: List<KattonItemCollection.KattonItemSlot>): List<SlotAccess?> =
        slot.map { this[it] }
    class KattonItemCollection(){

        open class KattonItemSlot(
            val index: Int
        )
        interface KattonItemSlotGroup{
            val any: List<KattonItemSlot>
        }
        open class KattonItemSlotList(
            val offset: Int,
            size: Int
        ): Iterable<KattonItemSlot>, KattonItemSlotGroup {
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
        object Contents: KattonItemSlot(0)
        object Container: KattonItemSlotList(0, 54)
        object Hotbar: KattonItemSlotList(0, 9)
        object Inventory: KattonItemSlotList(9, 27)
        object EnderChest: KattonItemSlotList(200, 27)
        object MobInventory: KattonItemSlotList(300, 8)
        object Horse: KattonItemSlotList(500, 15)
        
        object Weapon: KattonItemSlotGroup {
            object MainHand: KattonItemSlot(EquipmentSlot.MAINHAND.getIndex(98))
            object OffHand: KattonItemSlot(EquipmentSlot.OFFHAND.getIndex(98))
            override val any: List<KattonItemSlot> = listOf(MainHand, OffHand)
        }

        object Armor: KattonItemSlotGroup {
            object Head: KattonItemSlot(EquipmentSlot.HEAD.getIndex(100))
            object Chest: KattonItemSlot(EquipmentSlot.CHEST.getIndex(100))
            object Legs: KattonItemSlot(EquipmentSlot.LEGS.getIndex(100))
            object Feet: KattonItemSlot(EquipmentSlot.FEET.getIndex(100))
            object Body: KattonItemSlot(EquipmentSlot.BODY.getIndex(105))
            override val any: List<KattonItemSlot> = listOf(Head, Chest, Legs, Feet, Body)
        }

        object Saddle: KattonItemSlot(EquipmentSlot.SADDLE.getIndex(106))
        object HorseChest: KattonItemSlot(499)
        object PlayerCursor: KattonItemSlot(499)
        object PlayerCrafting: KattonItemSlotList(500, 4)


    }

    fun Player.addItem(item: Item, amount: Int) = giveItem(this, ItemStack(item, amount))
    // 已经提供了 addItem(itemStack: ItemStack)
    fun Player.hasItem(item: Item) = hasItem(this, item)
    fun Player.removeItem(item: Item, amount: Int) = removeItem(this, item, amount)

    class KattonLevelBlockCollection(
        val level: Level
    ) {
        operator fun get(blockPos: BlockPos): Block {
            return level.getBlockState(blockPos).block
        }
        operator fun set(blockPos: BlockPos, block: Block) {
            setBlock(level, blockPos, block)
        }
        operator fun set(start: BlockPos, end: BlockPos, block: Block) {
            fill(level, start, end, block)
        }
    }
    val Level.blocks: KattonLevelBlockCollection
        get() = KattonLevelBlockCollection(this)

    class KattonLevelBlockStateCollection(
        val level: Level
    ) {
        operator fun get(blockPos: BlockPos): BlockState {
            return level.getBlockState(blockPos)
        }
        operator fun set(blockPos: BlockPos, blockState: BlockState) {
            setBlock(level, blockPos, blockState)
        }
        operator fun set(start: BlockPos, end: BlockPos, blockState: BlockState) {
            fill(level, start, end, blockState)
        }
    }
    val Level.blockStates: KattonLevelBlockStateCollection
        get() = KattonLevelBlockStateCollection(this)

    fun Entity.damage(amount: Float) = damage(this, amount)
    //TODO
    var difficulty: Difficulty
        get() = server.overworld().difficulty
        set(value) {
            setDifficulty(difficulty, true)
        }
}