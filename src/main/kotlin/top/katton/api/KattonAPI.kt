@file:Suppress("unused")

package top.katton.api

import com.mojang.brigadier.StringReader
import com.mojang.datafixers.util.Pair
import com.mojang.logging.LogUtils
import net.minecraft.ChatFormatting
import net.minecraft.advancements.criterion.NbtPredicate
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.network.chat.*
import net.minecraft.network.chat.ClickEvent.SuggestCommand
import net.minecraft.network.chat.HoverEvent.ShowText
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.bossevents.CustomBossEvent
import net.minecraft.server.commands.*
import net.minecraft.server.commands.data.BlockDataAccessor
import net.minecraft.server.commands.data.EntityDataAccessor
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerPlayer.RespawnConfig
import net.minecraft.server.players.IpBanListEntry
import net.minecraft.server.players.PlayerList
import net.minecraft.server.players.UserBanListEntry
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.world.Container
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.LevelData.RespawnData
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.functions.LootItemFunction
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.*
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointStyleAsset
import net.minecraft.world.waypoints.WaypointTransmitter
import java.util.*
import java.util.function.Consumer
import kotlin.math.min
import kotlin.math.sqrt

private val LOGGER = LogUtils.getLogger()

/**
 * Current minecraft server instance. Maybe null during client-side execution.
 */
var server: MinecraftServer? = null


private fun requireServer(): MinecraftServer =
    server ?: error("MinecraftServer is not available (client-side or not started)")


/**
 * Execute a command as the provided command source.
 *
 * @param source the command source to run the command as
 * @param command the command string to execute
 */
fun executeCommand(source: CommandSourceStack, command: String) {
    val srv = source.server
    srv.commands.performPrefixedCommand(source, command)
}

/**
 * Execute a command as the server console.
 *
 * @param command the command string to execute
 */
fun executeCommandAsServer(command: String) {
    val srv = requireServer()
    val source = srv.createCommandSourceStack()
    srv.commands.performPrefixedCommand(source, command)
}

fun findPlayer(player: String): ServerPlayer?{
    return requireServer().playerList.getPlayerByName(player)
}

fun findPlayer(uuid: UUID): ServerPlayer?{
    return requireServer().playerList.getPlayer(uuid)
}

fun findEntities(level: ServerLevel, selector: EntitySelector): List<Entity> {
    return selector.findEntities(requireServer().createCommandSourceStack().withLevel(level))
}

fun findEntity(uuid: UUID): Entity?{
    return requireServer().allLevels.firstNotNullOfOrNull { it.getEntity(uuid) }
}

/**
 * Parse an NBT string into a CompoundTag.
 *
 * @param nbt NBT string to parse
 * @return parsed CompoundTag
 */
fun parseNbt(nbt: String): CompoundTag = TagParser.parseCompoundFully(nbt)

/**
 * Get the full NBT data of an entity.
 *
 * @param entity the target entity
 * @return CompoundTag representing the entity's data
 */
fun getEntityNbt(entity: Entity): CompoundTag {
    return NbtPredicate.getEntityTagToCompare(entity);
}

/**
 * Replace the NBT data of an entity.
 *
 * @param entity the target entity
 * @param tag the CompoundTag to set on the entity
 */
fun setEntityNbt(entity: Entity, tag: CompoundTag) {
    val accessor = EntityDataAccessor(entity)
    accessor.data = tag
}

/**
 * Get the NBT data of a block entity.
 *
 * @param block the target BlockEntity
 * @return CompoundTag representing the block entity's data
 */
fun getBlockNbt(block: BlockEntity): CompoundTag {
    val accessor = BlockDataAccessor(block, block.blockPos)
    return accessor.data
}

/**
 * Replace the NBT data of a block entity.
 *
 * @param block the target BlockEntity
 * @param tag the CompoundTag to set on the block entity
 */
fun setBlockNbt(block: BlockEntity, tag: CompoundTag) {
    val accessor = BlockDataAccessor(block, block.blockPos)
    accessor.data = tag
}

/**
 * Get the NBT data of a block at a position if it has a block entity.
 *
 * @param level level to query
 * @param pos position of the block
 * @return CompoundTag or null if no block entity exists
 */
fun getBlockNbt(level: Level, pos: BlockPos): CompoundTag? {
    return level.getBlockEntity(pos)?.let { getBlockNbt(it) }
}

/**
 * Set the NBT of a block entity at the given position.
 *
 * @param level level to modify
 * @param pos block position
 * @param tag CompoundTag to set
 * @return true if set succeeded, false if no block entity present
 */
fun setBlockNbt(level: Level, pos: BlockPos, tag: CompoundTag): Boolean {
    return level.getBlockEntity(pos)?.let {
        setBlockNbt(it, tag)
        true
    } ?: false
}

/**
 * Get stored command storage NBT by identifier.
 *
 * @param id storage identifier
 * @return CompoundTag stored at id
 */
fun getStorageNbt(id: Identifier): CompoundTag{
    return requireServer().commandStorage.get(id)
}

/**
 * Set stored command storage NBT by identifier.
 *
 * @param id storage identifier
 * @param tag CompoundTag to store
 */
fun setStorageNbt(id: Identifier, tag: CompoundTag) {
    requireServer().commandStorage.set(id, tag)
}

/**
 * Internal helper to get the server scoreboard.
 *
 * @return server Scoreboard instance
 */
private fun scoreboard(): Scoreboard = requireServer().scoreboard

/**
 * Get an objective by name.
 *
 * @param name objective name
 * @return Objective or null if not found
 */
fun getObjective(name: String): Objective? = scoreboard().getObjective(name)

/**
 * Get or create a scoreboard Objective.
 *
 * @param name objective name
 * @param displayName display name component for the objective
 * @param criteria objective criteria
 * @param renderType render type for the objective
 * @param displayAutoUpdate whether the display auto-updates
 * @param numberFormat optional number format
 * @return existing or newly created Objective
 */
fun getOrCreateObjective(
    name: String,
    displayName: Component = Component.literal(name),
    criteria: ObjectiveCriteria = ObjectiveCriteria.DUMMY,
    renderType: ObjectiveCriteria.RenderType = ObjectiveCriteria.RenderType.INTEGER,
    displayAutoUpdate: Boolean = false,
    numberFormat: NumberFormat? = null
): Objective {
    val board = scoreboard()
    val existed = board.getObjective(name)
    if (existed != null) return existed
    return board.addObjective(name, criteria, displayName, renderType, displayAutoUpdate, numberFormat)
}

/**
 * Set a score for a target identified by name.
 *
 * @param target target name
 * @param objective objective to set
 * @param value score value to set
 */
fun setScore(target: String, objective: Objective, value: Int) = setScore(ScoreHolder.forNameOnly(target), objective, value)

/**
 * Set a score for an Entity.
 *
 * @param target target Entity
 * @param objective objective to set
 * @param value score value
 */
fun setScore(target: Entity, objective: Objective, value: Int) = setScore(target as ScoreHolder, objective, value)

/**
 * Set a score for a ScoreHolder.
 *
 * @param target target ScoreHolder
 * @param objective objective to set
 * @param value score value
 */
fun setScore(target: ScoreHolder, objective: Objective, value: Int) {
    val score = scoreboard().getOrCreatePlayerScore(target, objective)
    score.set(value)
}

/**
 * Add delta to a target's score by name.
 *
 * @param target target name
 * @param objective objective to modify
 * @param delta amount to add
 */
fun addScore(target: String, objective: Objective, delta: Int) = addScore(ScoreHolder.forNameOnly(target), objective, delta)

/**
 * Add delta to a target Entity's score.
 *
 * @param target target Entity
 * @param objective objective to modify
 * @param delta amount to add
 */
fun addScore(target: Entity, objective: Objective, delta: Int) = addScore(target as ScoreHolder, objective, delta)

/**
 * Add delta to a ScoreHolder's score.
 *
 * @param target target ScoreHolder
 * @param objective objective to modify
 * @param delta amount to add
 */
fun addScore(target: ScoreHolder, objective: Objective, delta: Int) {
    val score = scoreboard().getOrCreatePlayerScore(target, objective)
    score.add(delta)
}

/**
 * Get a score by target name.
 *
 * @param target target name
 * @param objective objective to query
 * @return score value or null if not present
 */
fun getScore(target: String, objective: Objective): Int? = getScore(ScoreHolder.forNameOnly(target), objective)

/**
 * Get a score by Entity.
 *
 * @param target target Entity
 * @param objective objective to query
 * @return score value or null if not present
 */
fun getScore(target: Entity, objective: Objective): Int? = getScore(target as ScoreHolder, objective)

/**
 * Get a score for a ScoreHolder.
 *
 * @param target target ScoreHolder
 * @param objective objective to query
 * @return score value or null if not present
 */
fun getScore(target: ScoreHolder, objective: Objective): Int? {
    val readOnlyScoreInfo = scoreboard().getPlayerScoreInfo(target, objective)
    return readOnlyScoreInfo?.value()
}

/**
 * Reset a target's score by name.
 *
 * @param target target name
 * @param objective objective to reset
 */
fun resetScore(target: String, objective: Objective) = resetScore(ScoreHolder.forNameOnly(target), objective)

/**
 * Reset a target's score by Entity.
 *
 * @param target target Entity
 * @param objective objective to reset
 */
fun resetScore(target: Entity, objective: Objective) = resetScore(target as ScoreHolder, objective)

/**
 * Reset a ScoreHolder's score.
 *
 * @param target target ScoreHolder
 * @param objective objective to reset
 */
fun resetScore(target: ScoreHolder, objective: Objective) {
    scoreboard().resetSinglePlayerScore(target, objective)
}

/**
 * Get an attribute value from a LivingEntity.
 *
 * @param entity the entity
 * @param attribute attribute holder to read
 * @return current attribute value
 */
fun getAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Double {
    return entity.getAttributeValue(attribute)
}

/**
 * Check if a LivingEntity has a given attribute.
 *
 * @param entity the entity
 * @param attribute attribute holder to check
 * @return true if attribute present
 */
fun hasAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Boolean {
    return entity.getAttribute(attribute) != null
}

/**
 * Get base attribute value from a LivingEntity.
 *
 * @param entity the entity
 * @param attribute attribute holder to read
 * @return base value or null if attribute missing
 */
fun getBaseAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Double? {
    return entity.getAttribute(attribute)?.baseValue
}

/**
 * Set the base attribute value for a LivingEntity.
 *
 * @param entity the entity
 * @param attribute attribute holder to set
 * @param value new base value
 * @return true if changed, false otherwise
 */
fun setBaseAttribute(entity: LivingEntity, attribute: Holder<Attribute>, value: Double): Boolean {
    return entity.getAttribute(attribute)?.baseValue?.let {
        it != value
    } ?: false
}

/**
 * Add a transient attribute modifier to an entity.
 *
 * @param entity the entity
 * @param attribute attribute holder to modify
 * @param modifier AttributeModifier to add
 */
fun addAttributeModify(entity: LivingEntity, attribute: Holder<Attribute>, modifier: AttributeModifier) {
    entity.getAttribute(attribute)?.addTransientModifier(modifier)
}

/**
 * Remove an attribute modifier from an entity.
 *
 * @param entity the entity
 * @param attribute attribute holder to modify
 * @param modifier AttributeModifier to remove
 */
fun removeAttributeModify(entity: LivingEntity, attribute: Holder<Attribute>, modifier: AttributeModifier) {
    entity.getAttribute(attribute)?.removeModifier(modifier)
}

/**
 * Ban a player by adding them to the server ban list and disconnecting them.
 *
 * @param player the ServerPlayer to ban
 */
fun ban(player: ServerPlayer) {
    val userBanList = requireServer().playerList.bans;
    val nameAndId = player.nameAndId()
    if (!userBanList.isBanned(nameAndId)) {
        val userBanListEntry = UserBanListEntry(
            nameAndId, null, "server", null, null
        )
        userBanList.add(userBanListEntry);
        player.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
    }
}

/**
 * Ban an IP address and disconnect matching players.
 *
 * @param ip IP address string to ban
 */
fun banIp(ip: String) {
    val ipBanList = requireServer().playerList.ipBans;
    if (!ipBanList.isBanned(ip)) {
        val list = requireServer().playerList.getPlayersWithAddress(ip);
        val ipBanListEntry =
            IpBanListEntry(ip, null, "server", null, null)
        ipBanList.add(ipBanListEntry)
        for (serverPlayer in list) {
            serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned"));
        }
    }
}

/**
 * Get or create a boss bar by identifier.
 *
 * @param identifier boss bar identifier
 * @param title title component for the boss bar
 * @return CustomBossEvent instance
 */
fun getOrCreateBossBar(identifier: Identifier, title: Component): CustomBossEvent {
    val qwq = requireServer().customBossEvents
    if (qwq.get(identifier) != null) return qwq.get(identifier)!!
    return qwq.create(identifier,title)
}

/**
 * Clear a player's inventory.
 *
 * @param player the Player whose inventory will be cleared
 */
fun clearInventory(player: Player) {
    player.inventory.clearContent()
}

/**
 * Set an item into a player's inventory slot.
 *
 * @param player the Player to modify
 * @param slot inventory slot index
 * @param itemStack item stack to set
 */
fun setItem(player: Player, slot: Int, itemStack: ItemStack) {
    player.inventory.setItem(slot, itemStack)
    if (player is ServerPlayer) {
        player.inventoryMenu.sendAllDataToRemote()
    }
}

/**
 * Get the item from a player's inventory slot.
 *
 * @param player the Player to query
 * @param slot inventory slot index
 * @return ItemStack in the slot
 */
fun getItem(player: Player, slot: Int): ItemStack {
    return player.inventory.getItem(slot)
}

/**
 * Try to give an item stack to a player.
 *
 * @param player the Player to receive the item
 * @param itemStack the ItemStack to give
 * @return true if added to inventory, false if full
 */
fun giveItem(player: Player, itemStack: ItemStack): Boolean {
    return player.inventory.add(itemStack)
}

/**
 * Find the slot index of an item in player's inventory.
 *
 * @param player the Player to search
 * @param item item type to find
 * @return slot index or -1 if not found
 */
fun hasItem(player: Player, item: Item): Int {
    return player.inventory.findSlotMatchingItem(ItemStack(item))
}

/**
 * Remove a count of items from player's inventory.
 *
 * @param player the Player to modify
 * @param item item type to remove
 * @param count amount to remove
 * @return true if removal succeeded, false otherwise
 */
fun removeItem(player: Player, item: Item, count: Int): Boolean {
    val slot = player.inventory.findSlotMatchingItem(ItemStack(item))
    return if (slot >= 0) {
        val stackInSlot = player.inventory.getItem(slot)
        if (stackInSlot.count >= count) {
            stackInSlot.shrink(count)
            if (player is ServerPlayer) {
                player.inventoryMenu.sendAllDataToRemote()
            }
            true
        } else {
            false
        }
    } else {
        false
    }
}

/**
 * Set a block at position to a specific BlockState.
 *
 * @param level level to modify
 * @param pos block position
 * @param state BlockState to set
 */
fun setBlock(level: Level, pos: BlockPos, state: BlockState) {
    level.setBlock(pos, state, 3)
}

/**
 * Set a block at position using a Block type's default state.
 *
 * @param level level to modify
 * @param pos block position
 * @param block block type to set
 */
fun setBlock(level: Level, pos: BlockPos, block: Block) {
    setBlock(level, pos, block.defaultBlockState())
}

/**
 * Fill a region with a given BlockState.
 *
 * @param level level to modify
 * @param start start position (inclusive)
 * @param end end position (inclusive)
 * @param state BlockState to place
 */
fun fill(level: Level, start: BlockPos, end: BlockPos, state: BlockState) {
    BlockPos.betweenClosed(start, end).forEach { pos ->
        level.setBlock(pos, state, 3)
    }
}

/**
 * Fill a region with a given Block type using its default state.
 *
 * @param level level to modify
 * @param start start position (inclusive)
 * @param end end position (inclusive)
 * @param block block type to place
 */
fun fill(level: Level, start: BlockPos, end: BlockPos, block: Block) {
    fill(level, start, end, block.defaultBlockState())
}

/**
 * Damage an entity by an amount using generic damage.
 *
 * @param entity target entity
 * @param amount damage amount
 */
fun damage(entity: Entity, amount: Float) {
    entity.hurtServer(
        requireServer().overworld(),
        requireServer().overworld().damageSources().source(DamageTypes.GENERIC),
        amount
    )
}

/**
 * Damage a target entity from an attacker using a damage type key.
 *
 * @param target the entity to damage
 * @param amount damage amount
 * @param attacker the source entity causing damage
 * @param damageType resource key of the DamageType (default GENERIC)
 */
fun damage(target: Entity, amount: Float, attacker: Entity, damageType: ResourceKey<DamageType> = DamageTypes.GENERIC) {
    val type = requireServer().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(damageType)
    if(type.isEmpty) {
        LOGGER.warn("DamageType $damageType not found")
        return
    }
    val damageType = type.get()
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(damageType, attacker, attacker),
        amount
    )
}

/**
 * Damage a target entity from an attacker using a DamageType instance.
 *
 * @param target the entity to damage
 * @param amount damage amount
 * @param attacker the source entity causing damage
 * @param damageType DamageType instance to apply
 */
fun damage(target: Entity, amount: Float, attacker: Entity, damageType: DamageType) {
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(Holder.direct(damageType), attacker, attacker),
        amount
    )
}


/**
 * Damage a target entity from a position using a damage type key.
 *
 * @param target entity to damage
 * @param amount damage amount
 * @param pos position of damage source
 * @param damageType resource key of the DamageType (default GENERIC)
 */
fun damage(target: Entity, amount: Float, pos: Vec3, damageType: ResourceKey<DamageType> = DamageTypes.GENERIC) {
    val type = requireServer().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(damageType)
    if(type.isEmpty) {
        LOGGER.warn("DamageType $damageType not found")
        return
    }
    val damageType = type.get()
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(damageType, pos),
        amount
    )
}

/**
 * Damage a target entity from a position using a DamageType instance.
 *
 * @param target entity to damage
 * @param amount damage amount
 * @param pos position of damage source
 * @param damageType DamageType instance to apply
 */
fun damage(target: Entity, amount: Float, pos: Vec3, damageType: DamageType) {
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(Holder.direct(damageType), pos),
        amount
    )
}

/**
 * De-op a player (remove operator status).
 *
 * @param player ServerPlayer to de-op
 */
fun deop(player: ServerPlayer) {
    requireServer().playerList.deop(player.nameAndId())
}

/**
 * Op a player (grant operator status).
 *
 * @param player ServerPlayer to op
 */
fun op(player: ServerPlayer) {
    requireServer().playerList.op(player.nameAndId())
}

/**
 * Set server difficulty.
 *
 * @param difficulty new Difficulty
 * @param ignoreLock whether to ignore difficulty lock
 */
fun setDifficulty(difficulty: Difficulty, ignoreLock: Boolean = true) {
    requireServer().setDifficulty(difficulty, ignoreLock)
}

/**
 * Get current overworld difficulty.
 *
 * @return current Difficulty
 */
fun getDifficulty(): Difficulty {
    return requireServer().overworld().difficulty
}

// ==================== Effect ====================

/**
 * Add a mob effect to a LivingEntity.
 *
 * @param entity target entity
 * @param effect holder of the MobEffect to apply
 * @param duration effect duration in ticks (default 600)
 * @param amplifier effect amplifier level (default 0)
 * @param showParticles whether to show particles
 * @param ambient whether effect is ambient
 */
fun addEffect(entity: LivingEntity, effect: Holder<MobEffect>, duration: Int = 600, amplifier: Int = 0, showParticles: Boolean = true, ambient: Boolean = false) {
    entity.addEffect(MobEffectInstance(effect, duration, amplifier, ambient, showParticles))
}

/**
 * Remove a specific effect from a LivingEntity.
 *
 * @param entity target entity
 * @param effect holder of the MobEffect to remove
 */
fun removeEffect(entity: LivingEntity, effect: Holder<MobEffect>) {
    entity.removeEffect(effect)
}

/**
 * Clear all effects from a LivingEntity.
 *
 * @param entity target entity
 */
fun clearEffects(entity: LivingEntity) {
    entity.removeAllEffects()
}

/**
 * Enchant an ItemStack with an enchantment.
 *
 * @param itemStack target ItemStack
 * @param enchantment enchantment holder to apply
 * @param level enchantment level
 */
fun enchant(itemStack: ItemStack, enchantment: Holder<Enchantment>, level: Int) {
    itemStack.enchant(enchantment, level)
}

/**
 * Enchant the item in an entity's main hand if present.
 *
 * @param entity target LivingEntity
 * @param enchantment enchantment holder to apply
 * @param level enchantment level
 */
fun enchantMainHand(entity: LivingEntity, enchantment: Holder<Enchantment>, level: Int) {
    val stack = entity.mainHandItem
    if (!stack.isEmpty) {
        stack.enchant(enchantment, level)
    }
}

/**
 * Give experience points to a player.
 *
 * @param player target Player
 * @param points experience points to add
 */
fun addXpPoints(player: Player, points: Int) {
    player.giveExperiencePoints(points)
}

/**
 * Give experience levels to a player.
 *
 * @param player target Player
 * @param levels levels to add
 */
fun addXpLevels(player: Player, levels: Int) {
    player.giveExperienceLevels(levels)
}

/**
 * Set a player's experience level.
 *
 * @param player target Player
 * @param level level value to set
 */
fun setXpLevel(player: Player, level: Int) {
    player.experienceLevel = level
}

/**
 * Get a player's experience level.
 *
 * @param player target Player
 * @return current experience level
 */
fun getXpLevel(player: Player): Int {
    return player.experienceLevel
}

/**
 * Get a player's experience progress (fraction).
 *
 * @param player target Player
 * @return experience progress as float (0..1)
 */
fun getXpProgress(player: Player): Float {
    return player.experienceProgress
}

// ==================== FillBiome ====================

/**
 * Fill a region's biome using a predicate on biome holder.
 *
 * @param level server-level to modify
 * @param start start position
 * @param end end position
 * @param biome biome identifier to apply
 * @param biomePredicate predicate to further filter biome application
 */
fun fillBiome(level: Level, start: BlockPos, end: BlockPos, biome: Identifier, biomePredicate: (Holder<Biome>) -> Boolean) {
    if (level !is net.minecraft.server.level.ServerLevel) return
    val b = requireServer().registryAccess().lookupOrThrow(Registries.BIOME).get(biome)
    if(b.isEmpty) {
        LOGGER.warn("Biome $biome not found")
        return
    }
    val biome = b.get()
    val result = FillBiomeCommand.fill(level, start, end, biome, biomePredicate) {}
    if(result.right().isPresent){
        LOGGER.warn("Failed to fill biome: ${result.right().get()}")
    }
}

/**
 * Run a function (data pack function) with an optional command source.
 *
 * @param id function identifier
 * @param source command source to use (defaults to server)
 */
fun runFunction(id: Identifier, source: CommandSourceStack = requireServer().createCommandSourceStack()) {
    requireServer().functions.get(id).ifPresent {
        requireServer().functions.execute(it, source)
    }
}

/**
 * Set a player's game mode.
 *
 * @param player target ServerPlayer
 * @param gameMode target GameType
 */
fun setGameMode(player: ServerPlayer, gameMode: GameType) {
    player.setGameMode(gameMode)
}

/**
 * Get a player's current GameType.
 *
 * @param player target ServerPlayer
 * @return current GameType
 */
fun getGameMode(player: ServerPlayer): GameType {
    return player.gameMode.gameModeForPlayer
}

/**
 * Set a game rule value on the server overworld.
 *
 * @param key GameRule key
 * @param value value to set
 */
fun <T : Any> setGameRule(key: GameRule<T>, value: T) {
    try{
        requireServer().overworld().gameRules.set(key, value, requireServer())
    }catch (e: IllegalArgumentException) {
        LOGGER.warn("Failed to set game rule $key to $value", e)
    }
}

/**
 * Get a game rule value from the server overworld.
 *
 * @param key GameRule key
 * @return value of the game rule
 */
fun <T : Any> getGameRule(key: GameRule<T>): T {
    return requireServer().overworld().gameRules.get(key)
}

/**
 * Apply a LootItemFunction modifier to a block container slot.
 *
 * @param pos block position of the container
 * @param slot slot index to modify
 * @param modifier LootItemFunction to apply
 */
fun modifyBlockItem(pos: BlockPos, slot: Int, modifier: LootItemFunction){
    val container = requireServer().overworld().getBlockEntity(pos)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $pos is not a container")
        return
    }
    if(slot >= 0 && slot < container.containerSize){
        val itemStack = container.getItem(slot)
        val modifiedItemStack = applyModifier(itemStack, modifier)
        container.setItem(slot, modifiedItemStack)
    }else{
        LOGGER.warn("Slot $slot is out of bounds for container at $pos")
    }
}

/**
 * Apply a LootItemFunction to an entity equipment slot.
 *
 * @param entity target entity
 * @param slot equipment slot index
 * @param modifier LootItemFunction to apply
 */
fun modifyEntityItem(entity: Entity, slot: Int, modifier: LootItemFunction){
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return
    }
    val itemStack = slotAccess.get().copy()
    val modifiedItemStack = applyModifier(itemStack, modifier)
    if(slotAccess.set(modifiedItemStack) && entity is ServerPlayer){
        entity.containerMenu.broadcastChanges()
    }
}

/**
 * Set an item into a container block slot.
 *
 * @param pos block position
 * @param slot slot index
 * @param itemStack ItemStack to set
 */
fun setBlockItem(pos: BlockPos, slot: Int, itemStack: ItemStack) {
    val container = requireServer().overworld().getBlockEntity(pos)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $pos is not a container")
        return
    }
    if (slot >= 0 && slot < container.containerSize) {
        container.setItem(slot, itemStack)
    } else {
        LOGGER.warn("Slot $slot is out of bounds for container at $pos")
    }
}

/**
 * Set an item into an entity slot.
 *
 * @param entity target entity
 * @param slot slot index
 * @param itemStack ItemStack to set
 */
fun setEntityItem(entity: Entity, slot: Int, itemStack: ItemStack) {
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return
    }
    if (slotAccess.set(itemStack) && entity is ServerPlayer) {
        entity.containerMenu.broadcastChanges()
    }
}

/**
 * Get an item from a container block slot.
 *
 * @param pos block position
 * @param slot slot index
 * @return ItemStack or null if invalid
 */
fun getBlockItem(pos: BlockPos, slot: Int): ItemStack? {
    val container = requireServer().overworld().getBlockEntity(pos)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $pos is not a container")
        return null
    }
    return if (slot >= 0 && slot < container.containerSize) {
        container.getItem(slot)
    } else {
        LOGGER.warn("Slot $slot is out of bounds for container at $pos")
        null
    }
}

/**
 * Get an item from an entity slot.
 *
 * @param entity target entity
 * @param slot slot index
 * @return ItemStack or null if slot missing
 */
fun getEntityItem(entity: Entity, slot: Int): ItemStack? {
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return null
    }
    return slotAccess.get()
}

/**
 * Apply a LootItemFunction to an ItemStack and return the modified stack.
 *
 * @param itemStack item to modify
 * @param modifier function to apply
 * @return modified ItemStack (size-limited)
 */
fun applyModifier(itemStack: ItemStack, modifier: LootItemFunction): ItemStack {
    val params = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
        .create(LootContextParamSets.COMMAND)
    val context = LootContext.Builder(params).create(Optional.empty())
    context.pushVisitedElement(LootContext.createVisitedEntry(modifier))
    val modifiedItemStack = modifier.apply(itemStack, context)
    modifiedItemStack.limitSize(modifiedItemStack.maxStackSize)
    return modifiedItemStack
}

/**
 * Kick a player with an optional reason component.
 *
 * @param player target Player (ServerPlayer required to disconnect)
 * @param reason disconnect reason component
 */
fun kick(player: Player, reason: Component = Component.translatable("multiplayer.disconnect.kicked")) {
    if (player is ServerPlayer) {
        player.connection.disconnect(reason)
    }
}

/**
 * Get drops for a block as if it were broken with a tool.
 *
 * @param pos block position
 * @param tool tool ItemStack used to break the block
 * @return list of ItemStack drops
 */
fun dropBlockLoot(pos: BlockPos, tool: ItemStack): List<ItemStack> {
    val blockState = requireServer().overworld().getBlockState(pos)
    val blockEntity = requireServer().overworld().getBlockEntity(pos)
    if(blockState.block.lootTable.isEmpty){
        LOGGER.warn("Block at $pos has no loot table")
        return emptyList()
    }
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.BLOCK_STATE, blockState)
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withParameter(LootContextParams.TOOL, tool)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
        .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
    return blockState.getDrops(builder)
}

/**
 * Get drops for an entity as if it were killed.
 *
 * @param entity target entity
 * @param killer optional killer entity (may influence drops)
 * @return list of ItemStack drops
 */
fun dropKillLoot(entity: Entity, killer: Entity?): List<ItemStack> {
    if(entity.lootTable.isEmpty){
        LOGGER.warn("Entity ${entity.displayName.string} has no loot table")
        return emptyList()
    }
    val lootTableKey = entity.lootTable.get()
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, entity.position())
        .withParameter(LootContextParams.THIS_ENTITY, entity)
        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
        .withParameter(LootContextParams.DAMAGE_SOURCE, entity.damageSources().magic())
    if(killer is Player) builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
    val params = builder.create(LootContextParamSets.ENTITY)
    val lootTable = requireServer().reloadableRegistries().getLootTable(lootTableKey)
    return lootTable.getRandomItems(params)
}

/**
 * Generate chest loot from a LootTable.
 *
 * @param lootTable LootTable to roll
 * @return list of generated ItemStack
 */
fun dropChestLoot(lootTable: LootTable): List<ItemStack> {
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
    val params = builder.create(LootContextParamSets.CHEST)
    return lootTable.getRandomItems(params)
}

/**
 * Generate fishing loot from a LootTable.
 *
 * @param lootTable LootTable to roll
 * @param pos origin position for loot context
 * @param tool tool ItemStack used
 * @return list of generated ItemStack
 */
fun dropFishingLoot(lootTable: LootTable, pos: BlockPos, tool: ItemStack): List<ItemStack> {
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withParameter(LootContextParams.TOOL, tool)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
    val params = builder.create(LootContextParamSets.FISHING)
    return lootTable.getRandomItems(params)
}

/**
 * Attempt to deposit item stacks into a container block.
 *
 * @param block container block position
 * @param itemStacks list of ItemStack to deposit (may be modified)
 */
fun dropToBlock(block: BlockPos, itemStacks: List<ItemStack>) {
    val container = requireServer().overworld().getBlockEntity(block)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $block is not a container")
        return
    }
    for(item in itemStacks){
        if(item.isEmpty) continue
        var changed = false
        for(i in 0 until container.containerSize){
            val stackInSlot = container.getItem(i)
            if(container.canPlaceItem(i, item)){
                if(stackInSlot.isEmpty){
                    container.setItem(i, item)
                    changed = true
                    break
                }

                if(item.count <= stackInSlot.maxStackSize && ItemStack.isSameItemSameComponents(stackInSlot, item)){
                    val delta = stackInSlot.maxStackSize - stackInSlot.count
                    val toAdd = min(item.count, delta)
                    item.shrink(toAdd)
                    stackInSlot.grow(toAdd)
                    changed = true
                }
            }
            if(changed){
                container.setChanged()
            }
        }
    }
}

/**
 * Replace a range of slots in a container block with given item stacks.
 *
 * @param block container position
 * @param i start slot index
 * @param j number of slots to replace
 * @param itemStacks list of ItemStacks to place (shorter lists fill with empty)
 */
fun dropToBlockReplace(block: BlockPos, i: Int, j: Int, itemStacks: List<ItemStack>) {
    val container = requireServer().overworld().getBlockEntity(block)?.let { it as? Container } ?: run {
        LOGGER.warn("Block at $block is not a container")
        return
    }
    if(i >= 0 && i < container.containerSize){
        for(l in 0 until j){
            val m = i + l;
            val itemStack = if(l < itemStacks.size) itemStacks[l] else ItemStack.EMPTY
            if(container.canPlaceItem(l, itemStack)){
                container.setItem(m, itemStack)
            }
        }
    }
}

/**
 * Give item stacks to players (adds copies to inventory).
 *
 * @param player target ServerPlayer
 * @param itemStacks list of ItemStack to give
 */
fun dropToPlayer(player: ServerPlayer, itemStacks: List<ItemStack>) {
    for(item in itemStacks){
        player.inventory.add(item.copy())
    }
}

/**
 * Set item stacks into entity slots.
 *
 * @param entity target entity
 * @param i starting slot index
 * @param j number of slots to set
 * @param itemStacks list of ItemStacks to set
 */
fun dropToEntity(entity: Entity, i: Int, j: Int, itemStacks: List<ItemStack>) {
    for(k in 0 until j){
        val itemStack = if(k < itemStacks.size) itemStacks[k] else ItemStack.EMPTY
        val slotAccess = entity.getSlot(i + k)
        slotAccess?.set(itemStack.copy())
    }
}

/**
 * Drop item stacks into the world at a position.
 *
 * @param level world level
 * @param pos drop position
 * @param itemStacks list of ItemStack to spawn
 */
fun dropTo(level: Level, pos: Vec3, itemStacks: List<ItemStack>) {
    for(item in itemStacks){
        val itemEntity = ItemEntity(level, pos.x, pos.y, pos.z, item.copy())
        itemEntity.setDefaultPickUpDelay()
        level.addFreshEntity(itemEntity)
    }
}

/**
 * Send a system message to a player.
 *
 * @param player target ServerPlayer
 * @param message plain string message
 */
fun tell(player: ServerPlayer, message: String) {
    player.sendSystemMessage(Component.literal(message))
}

/**
 * Send particles to a collection of players.
 *
 * @param level server level
 * @param players players to send to
 * @param particle particle options
 * @param pos center position
 * @param delta spread vector (default zero)
 * @param speed particle speed
 * @param count number of particles
 * @param forced whether to force send (ignores client settings)
 */
fun particle(level: ServerLevel, players: Collection<ServerPlayer>, particle: ParticleOptions, pos: Vec3, delta: Vec3 = Vec3.ZERO, speed: Double = 1.0, count: Int = 0, forced: Boolean = false) {
    for (player in players) {
        level.sendParticles(
            player,
            particle,
            forced,
            false,
            pos.x, pos.y, pos.z,
            count,
            delta.x, delta.y, delta.z,
            speed
        )
    }
}

/**
 * Locate a structure by ResourceKey.
 *
 * @param structureKey structure resource key
 * @param level server level to search in
 * @param startPos starting position for search
 * @return nearest BlockPos of the structure or null if not found
 */
fun locateStructure(structureKey: ResourceKey<Structure>, level: ServerLevel, startPos: BlockPos = BlockPos.ZERO): BlockPos? {
    val structure = requireServer().overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structureKey)
    if(structure.isEmpty){
        LOGGER.warn("Structure $structureKey not found")
        return null
    }
    val holderSet = HolderSet.direct(structure.get())
    val result = level.chunkSource.generator.findNearestMapStructure(level, holderSet, startPos, 100, false)
    return result?.first
}


/**
 * Locate a structure by TagKey.
 *
 * @param structureKey structure tag key
 * @param level server level to search in
 * @param startPos starting position
 * @return nearest BlockPos or null if not found
 */
fun locateStructure(structureKey: TagKey<Structure>, level: ServerLevel, startPos: BlockPos = BlockPos.ZERO): BlockPos? {
    val holderSet = requireServer().overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structureKey)
    if(holderSet.isEmpty){
        LOGGER.warn("Structure $structureKey not found")
        return null
    }
    val result = level.chunkSource.generator.findNearestMapStructure(level, holderSet.get(), startPos, 100, false)
    return result?.first
}

/**
 * Find closest biome by resource key.
 *
 * @param biomeKey biome resource key
 * @param level server level to search
 * @param startPos starting position
 * @return Pair of BlockPos and biome Holder, or null if not found
 */
fun locateBiome(biomeKey: ResourceKey<Biome>, level: ServerLevel, startPos: BlockPos = BlockPos.ZERO): Pair<BlockPos, Holder<Biome>>? {
    val biome = requireServer().overworld().registryAccess().lookupOrThrow(Registries.BIOME).get(biomeKey)
    if(biome.isEmpty){
        LOGGER.warn("Biome $biomeKey not found")
        return null
    }
    val holderSet = HolderSet.direct(biome.get())
    return level.findClosestBiome3d({it == biome}, startPos, 6400, 32, 64)
}


/**
 * Find closest biome by tag key.
 *
 * @param biomeKey biome tag key
 * @param level server level to search
 * @param startPos starting position
 * @return Pair of BlockPos and biome Holder, or null if not found
 */
fun locateBiome(biomeKey: TagKey<Biome>, level: ServerLevel, startPos: BlockPos = BlockPos.ZERO): Pair<BlockPos, Holder<Biome>>? {
    val holderSet = requireServer().overworld().registryAccess().lookupOrThrow(Registries.BIOME).get(biomeKey)
    if(holderSet.isEmpty){
        LOGGER.warn("Biome $biomeKey not found")
        return null
    }
    return level.findClosestBiome3d({holderSet.get().contains(it)}, startPos, 6400, 32, 64)
}

/**
 * Place a configured feature at a position.
 *
 * @param feature feature identifier
 * @param pos position to place
 */
fun placeFeature(feature: Identifier, pos: BlockPos){
    val f = requireServer().registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(feature)
    if(f.isEmpty){
        LOGGER.warn("Feature $feature not found")
        return
    }
    PlaceCommand.placeFeature(requireServer().createCommandSourceStack(), f.get(), pos)
}

/**
 * Place a jigsaw structure from a template pool.
 *
 * @param templatePool pool identifier
 * @param start starting template identifier
 * @param depth placement depth
 * @param pos starting position
 */
fun placeJigsaw(templatePool: Identifier, start: Identifier, depth: Int, pos: BlockPos){
    val templatePoolHolder = requireServer().overworld().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).get(templatePool)
    if(templatePoolHolder.isEmpty){
        LOGGER.warn("Jigsaw pool $templatePool not found")
        return
    }
    PlaceCommand.placeJigsaw(requireServer().createCommandSourceStack(), templatePoolHolder.get(), start, depth, pos)
}

/**
 * Place a structure by identifier at a position.
 *
 * @param structure structure identifier
 * @param pos position to place
 */
fun placeStructure(structure: Identifier, pos: BlockPos){
    val structureHolder = requireServer().overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structure)
    if(structureHolder.isEmpty){
        LOGGER.warn("Structure $structure not found")
        return
    }
    PlaceCommand.placeStructure(requireServer().createCommandSourceStack(), structureHolder.get(), pos)
}

/**
 * Play a sound to a set of players, performing distance attenuation and minimum volume handling.
 *
 * @param level server level
 * @param players players to send sound to
 * @param sound sound identifier
 * @param soundSource sound category/source
 * @param pos sound origin position
 * @param volume base volume
 * @param pitch playback pitch
 * @param minVolume minimum audible volume when out of range
 */
fun playSound(level: ServerLevel, players: Collection<ServerPlayer>, sound: Identifier, soundSource: SoundSource, pos: Vec3, volume: Float = 1.0f, pitch: Float = 1.0f, minVolume: Float = 0.0f) {
    val soundEvent = Holder.direct(SoundEvent.createVariableRangeEvent(sound))
    val maxDistance = Mth.square(soundEvent.value().getRange(volume))
    val l = level.random.nextLong()
    for(player in players){
        if(player.level() == level){
            val x = pos.x - player.x
            val y = pos.y - player.y
            val z = pos.z - player.z
            val distance = x * x + y * y + z * z
            var qwq = pos
            var finalVolume = volume
            if(distance > maxDistance){
                if(minVolume <= 0.0f){
                    continue
                }
                val d = sqrt(distance)
                qwq = Vec3(player.x + x / d * 2.0, player.y + y / d * 2.0, player.z + z / d * 2.0)
                finalVolume = minVolume
            }

            player.connection.send(ClientboundSoundPacket(soundEvent, soundSource, qwq.x, qwq.y, qwq.z, finalVolume, pitch, l))
        }
    }
}

/**
 * Sample a random integer in [min, max] using optional random sequence.
 *
 * @param randomSequence optional identifier for random sequence
 * @param broadcast whether to broadcast the result to players
 * @param min inclusive minimum
 * @param max inclusive maximum
 * @return sampled integer or null on invalid range
 */
fun randomSample(randomSequence: Identifier? = null, broadcast: Boolean = false, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int?{
    val randomSource = randomSequence?.let { requireServer().overworld().getRandomSequence(it) } ?: requireServer().overworld().random
    val l = max.toLong() - min;
    if(l <= 0L) {
        LOGGER.error("Invalid range: min=$min, max=$max")
        return null
    }else if(l >= 2147483647L){
        LOGGER.error("Range is too large: min=$min, max=$max")
        return null
    }else{
        val result = Mth.randomBetweenInclusive(randomSource, min, max)
        if(broadcast){
            requireServer().playerList.broadcastSystemMessage(Component.translatable("commands.random.roll", "system", result, min, max), false)
        }
        return result
    }
}

/**
 * Reset a named random sequence for a level to its default seed.
 *
 * @param level server level
 * @param randomSequence sequence identifier
 */
fun resetSequence(level: ServerLevel, randomSequence: Identifier){
    level.randomSequences.reset(randomSequence, level.seed)
}

/**
 * Reset a named random sequence with a specific seed and behavior flags.
 *
 * @param level server level
 * @param randomSequence sequence identifier
 * @param seed integer seed
 * @param includeWorldSeed whether to include world seed
 * @param includeSequenceID whether to include sequence id
 */
fun resetSequence(level: ServerLevel, randomSequence: Identifier, seed: Int, includeWorldSeed: Boolean = true, includeSequenceID: Boolean = true){
    requireServer().overworld().randomSequences.reset(randomSequence, level.seed, seed, includeWorldSeed, includeSequenceID)
}

/**
 * Clear all random sequences for a level.
 *
 * @param level server level
 */
fun resetAllSequences(level: ServerLevel){
    level.randomSequences.clear()
}

/**
 * Reset all sequences and set new defaults.
 *
 * @param level server level
 * @param seed seed to set as default
 * @param includeWorldSeed whether to include world seed
 * @param includeSequenceID whether to include sequence id
 */
fun resetAllSequencesAndSetNewDefaults(level: ServerLevel, seed: Int, includeWorldSeed: Boolean = true, includeSequenceID: Boolean = true){
    level.randomSequences.setSeedDefaults(seed, includeWorldSeed, includeSequenceID)
}

/**
 * Give recipe advancements to players.
 *
 * @param players target players
 * @param recipes collection of recipes to award
 */
fun giveRecipes(players: Collection<ServerPlayer>, recipes: Collection<RecipeHolder<*>>){
    for(player in players){
        player.awardRecipes(recipes)
    }
}

/**
 * Take recipe advancements from players.
 *
 * @param players target players
 * @param recipes recipes to revoke
 */
fun takeRecipes(players: Collection<ServerPlayer>, recipes: Collection<RecipeHolder<*>>){
    for(player in players){
        player.resetRecipes(recipes)
    }
}

/**
 * Mount a passenger on a vehicle entity.
 *
 * @param passenger entity to mount
 * @param vehicle entity to be ridden
 * @return true if mounting succeeded, false otherwise
 */
fun mount(passenger: Entity, vehicle: Entity): Boolean {
    val exisingVehicle = passenger.vehicle
    if(exisingVehicle != null){
        LOGGER.error("${passenger.displayName} is already riding in ${exisingVehicle.displayName}")
        return false
    }else if(vehicle.type == EntityType.PLAYER){
        LOGGER.error("Players can't be ridden")
    }else if(passenger.selfAndPassengers.anyMatch { it == vehicle }) {
        LOGGER.error("Can't mount entity on itself or any of its passengers")
        return false
    }else if(passenger.level() != vehicle.level()){
        LOGGER.error("Can't mount entity in different dimension")
        return false
    }else if(!passenger.startRiding(vehicle, true, true)){
        LOGGER.error("${passenger.displayName} couldn't start riding ${vehicle.displayName}")
        return false
    }
    return true
}

/**
 * Dismount a passenger from its vehicle.
 *
 * @param passenger entity to dismount
 * @return true if dismounted, false if not riding
 */
fun dismount(passenger: Entity): Boolean {
    if(passenger.vehicle == null){
        LOGGER.error("${passenger.displayName} is not riding any vehicle")
        return false
    }
    passenger.stopRiding()
    return true
}

/**
 * Rotate an entity by a Vec2 (pitch, yaw).
 *
 * @param target target entity
 * @param rot rotation vector (x=pitch, y=yaw)
 * @param relative whether rotation is relative
 */
fun rotate(target: Entity, rot: Vec2, relative: Boolean = false){
    target.forceSetRotation(rot.y, relative, rot.x, relative)
}

/**
 * Rotate an entity to look at another entity.
 *
 * @param target entity to rotate
 * @param lookAt entity to look at
 * @param targetAnchor anchor point on the target
 * @param lookAtAnchor anchor point on lookAt entity
 */
fun rotate(target: Entity, lookAt: Entity, targetAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET, lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET){
    if(target is ServerPlayer){
        target.lookAt(targetAnchor, lookAt, lookAtAnchor)
    }else {
        target.lookAt(targetAnchor, lookAtAnchor.apply(lookAt))
    }
}

/**
 * Rotate an entity to look at a position.
 *
 * @param target entity to rotate
 * @param lookAt position to look at
 * @param targetAnchor anchor on target entity
 */
fun rotate(target: Entity, lookAt: Vec3, targetAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET, lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET){
    target.lookAt(targetAnchor, lookAt)
}

/**
 * Set spawn point for a collection of players.
 *
 * @param player collection of ServerPlayer to set
 * @param level server level for dimension
 * @param pos respawn position
 * @param rot rotation vector (pitch,x / yaw,y)
 */
fun spawnPoint(player: MutableCollection<ServerPlayer>, level: ServerLevel, pos: BlockPos, rot: Vec2){
    val resourceKey = level.dimension()
    val f = Mth.wrapDegrees(rot.y)
    val g = Mth.clamp(rot.x, -90.0f, 90.0f)

    for (serverPlayer in player) {
        serverPlayer.setRespawnPosition(
            RespawnConfig(LevelData.RespawnData.of(resourceKey, pos, f, g), true),
            false
        )
    }
}

/**
 * Set the world spawn and respawn orientation for a level.
 *
 * @param level server level
 * @param blockPos spawn position
 * @param rot rotation vector (pitch,x / yaw,y)
 */
fun setWorldSpawn(level: ServerLevel, blockPos: BlockPos, rot: Vec2) {
    val f = rot.y
    val g = rot.x
    val respawnData = RespawnData.of(level.dimension(), blockPos, f, g)
    level.respawnData = respawnData
}

/**
 * Make a player spectate a target entity.
 *
 * @param player spectator ServerPlayer
 * @param target entity to spectate, or null to stop
 * @return true if spectation succeeded, false otherwise
 */
fun spectate(player: ServerPlayer, target: Entity?): Boolean {
    if(player == target){
        LOGGER.error("${player.displayName} can't spectate itself")
        return false
    }else if(!player.isSpectator){
        LOGGER.error("${player.displayName} is not in spectator mode")
        return false
    }else if(target != null && target.type.clientTrackingRange() == 0){
        LOGGER.error("${target.displayName} cannot be spectated")
        return false
    }else {
        player.setCamera(target)
        return true
    }
}

/**
 * Spread players around a center point.
 *
 * @param level server level used for context
 * @param center center position vector (x=z, y ignored)
 * @param spreadDistance minimum distance between players
 * @param maxRange max spread radius
 * @param maxHeight maximum height difference
 * @param respectTeams whether to keep teams together
 * @param targets collection of entities to spread
 */
fun spreadPlayers(
    level: ServerLevel,
    center: Vec2,
    spreadDistance: Float,
    maxRange: Float,
    maxHeight: Int,
    respectTeams: Boolean,
    targets: Collection<Entity>
) {
    if (targets.isEmpty()) return
    val source = requireServer().createCommandSourceStack().withLevel(level)
    SpreadPlayersCommand.spreadPlayers(source, center, spreadDistance, maxRange, maxHeight, respectTeams, targets)
}

/**
 * Summon an entity of a given type at a position with optional NBT.
 *
 * @param level server level to spawn in
 * @param reference reference to the EntityType to summon
 * @param vec3 spawn position
 * @param entityData optional NBT override for the entity
 * @return spawned Entity or null on failure
 */
fun summon(
    level: ServerLevel,
    reference: Holder.Reference<EntityType<*>>,
    vec3: Vec3,
    entityData: CompoundTag? = null
): Entity? {
    val blockPos = BlockPos.containing(vec3)
    if (!Level.isInSpawnableBounds(blockPos)) {
        LOGGER.error("Invalid postion for summon")
        return null
    } else if (level.difficulty == Difficulty.PEACEFUL && !reference.value().isAllowedInPeaceful) {
        LOGGER.error("Monsters cannot be summoned in Peaceful difficulty")
        return null
    } else {
        var bl = false
        val compoundTag2 = entityData?.copy() ?: run {
            bl = true
            CompoundTag()
        }
        compoundTag2.putString("id", reference.key().identifier().toString())
        val entity: Entity? = EntityType.loadEntityRecursive(
            compoundTag2,
            level,
            EntitySpawnReason.COMMAND
        ) { e: Entity? ->
            e?.snapTo(vec3.x, vec3.y, vec3.z, e.yRot, e.xRot)
            e
        }
        if (entity == null) {
            LOGGER.error("Unable to summon entity")
            return null
        } else {
            if (bl && entity is Mob) {
                entity.finalizeSpawn(
                    level,
                    level.getCurrentDifficultyAt(entity.blockPosition()),
                    EntitySpawnReason.COMMAND,
                    null
                )
            }

            if (!level.tryAddFreshEntityWithPassengers(entity)) {
                LOGGER.error("Unable to summon entity due to duplicate UUID")
                return null
            } else {
                return entity
            }
        }
    }
}


/**
 * Get tags attached to an entity.
 *
 * @param entity target entity
 * @return mutable collection of tag strings
 */
fun getTags(entity: Entity): MutableCollection<String> {
    return entity.tags
}

/**
 * Add a tag to an entity.
 *
 * @param entity target entity
 * @param string tag to add
 * @return true if tag was added, false if already present
 */
fun addTag(entity: Entity, string: String): Boolean {
    return entity.addTag(string)
}

/**
 * Remove a tag from an entity.
 *
 * @param entity target entity
 * @param string tag to remove
 * @return true if the tag was removed
 */
fun removeTag(entity: Entity, string: String): Boolean {
    return entity.removeTag(string)
}

/**
 * Remove a collection of players from any teams.
 *
 * @param members collection of ScoreHolder members to remove from teams
 */
fun leaveTeam(members: Collection<ScoreHolder>){
    val scoreboard = requireServer().scoreboard
    for(member in members){
        scoreboard.removePlayerFromTeam(member.scoreboardName)
    }
}

/**
 * Add members to a PlayerTeam.
 *
 * @param team PlayerTeam to join
 * @param members collection of ScoreHolder to add
 */
fun joinTeam(team: PlayerTeam, members: Collection<ScoreHolder>){
    val scoreboard = requireServer().scoreboard
    for(member in members){
        scoreboard.addPlayerToTeam(member.scoreboardName, team)
    }
}

/**
 * Empty a player team of all members.
 *
 * @param team PlayerTeam to empty
 */
fun emptyTeam(team: PlayerTeam) {
    val scoreboard = requireServer().scoreboard
    for(member in team.players){
        scoreboard.removePlayerFromTeam(member, team)
    }
}

/**
 * Delete a player team from the scoreboard.
 *
 * @param team team to delete
 */
fun deleteTeam(team: PlayerTeam) {
    val scoreboard = requireServer().scoreboard
    scoreboard.removePlayerTeam(team)
}

/**
 * Create a team if it does not exist.
 *
 * @param name team name
 * @param displayName display name component for the team
 */
fun createTeam(name: String, displayName: Component = Component.literal(name)){
    val scoreboard = requireServer().scoreboard
    if(scoreboard.getPlayerTeam(name) != null) return
    val team = scoreboard.addPlayerTeam(name)
    team.displayName = displayName
}

/**
 * Get a team by name.
 *
 * @param name team name
 * @return PlayerTeam or null if not found
 */
fun getTeam(name: String): PlayerTeam? {
    return requireServer().scoreboard.getPlayerTeam(name)
}

/**
 * Send a team chat message to a list of players with filtering and formatting.
 *
 * @param entity source entity (sender)
 * @param playerTeam team being messaged
 * @param list recipients
 * @param playerChatMessage message content
 * @param commandSourceStack command source used for formatting and filtering
 */
fun teamMsg(
    entity: Entity,
    playerTeam: PlayerTeam,
    list: MutableList<ServerPlayer>,
    playerChatMessage: PlayerChatMessage,
    commandSourceStack: CommandSourceStack = requireServer().createCommandSourceStack(),
) {
    val component: Component = playerTeam.getFormattedDisplayName().withStyle(
        Style.EMPTY
            .withHoverEvent(ShowText(Component.translatable("chat.type.team.hover")))
            .withClickEvent(SuggestCommand("/teammsg ")))
    val bound = ChatType.bind(ChatType.TEAM_MSG_COMMAND_INCOMING, commandSourceStack).withTargetName(component)
    val bound2 = ChatType.bind(ChatType.TEAM_MSG_COMMAND_OUTGOING, commandSourceStack).withTargetName(component)
    val outgoingChatMessage = OutgoingChatMessage.create(playerChatMessage)
    var bl = false

    for (serverPlayer in list) {
        val bound3 = if (serverPlayer === entity) bound2 else bound
        val bl2 = commandSourceStack.shouldFilterMessageTo(serverPlayer)
        serverPlayer.sendChatMessage(outgoingChatMessage, bl2, bound3)
        bl = bl or (bl2 && playerChatMessage.isFullyFiltered)
    }

    if (bl) {
        commandSourceStack.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL)
    }
}


/**
 * Set the server ticking rate.
 *
 * @param f new tick rate (ticks per second)
 */
fun setTickingRate(f: Float) {
    requireServer().tickRateManager().setTickRate(f)
}

/**
 * Query current tickrate.
 *
 * @return current tickrate value
 */
fun tickQuery(): Float {
    return requireServer().tickRateManager().tickrate()
}

/**
 * Request the server to sprint tick advancement by given ticks.
 *
 * @param i number of ticks to sprint
 */
fun tickSprint(i: Int){
    requireServer().tickRateManager().requestGameToSprint(i)
}

/**
 * Freeze or unfreeze server tick processing.
 *
 * @param freeze true to freeze, false to unfreeze
 */
fun setTickFreeze(freeze: Boolean){
    val serverTickRateManager = requireServer().tickRateManager()
    if (freeze) {
        if (serverTickRateManager.isSprinting) {
            serverTickRateManager.stopSprinting()
        }

        if (serverTickRateManager.isSteppingForward) {
            serverTickRateManager.stopStepping()
        }
    }
    serverTickRateManager.setFrozen(freeze)
}

/**
 * Step the server forward a number of ticks while paused.
 *
 * @param i number of ticks to step
 */
fun tickStep(i: Int) {
    val serverTickRateManager = requireServer().tickRateManager()
    serverTickRateManager.stepGameIfPaused(i)
}

/**
 * Stop stepping mode on the tick manager.
 *
 * @return true if stepping was stopped
 */
fun tickStopStepping(): Boolean {
    val serverTickRateManager = requireServer().tickRateManager()
    return serverTickRateManager.stopStepping()
}

/**
 * Stop sprinting mode on the tick manager.
 *
 * @return true if sprinting was stopped
 */
fun tickStopSprinting(): Boolean {
    val serverTickRateManager = requireServer().tickRateManager()
    return serverTickRateManager.stopSprinting()
}


/**
 * Get current day time of a server level in ticks (0..23999).
 *
 * @param serverLevel server level to query
 * @return day time as int
 */
fun getDayTime(serverLevel: ServerLevel): Int {
    return (serverLevel.dayTime % 24000L).toInt()
}

/**
 * Set world time for all server levels.
 *
 * @param level reference server level (used for return)
 * @param i new day time in ticks
 * @return day time for provided level after setting
 */
fun setTime(level: ServerLevel ,i: Int): Int {
    for (serverLevel in requireServer().allLevels) {
        serverLevel.dayTime = i.toLong()
    }

    requireServer().forceTimeSynchronization()
    return getDayTime(level)
}

/**
 * Add time to world time across all server levels.
 *
 * @param level reference server level (used for return)
 * @param i amount of ticks to add
 * @return resulting day time for the given level
 */
fun addTime(level: ServerLevel, i: Int): Int {
    for (serverLevel in requireServer().allLevels) {
        serverLevel.dayTime += i
    }

    requireServer().forceTimeSynchronization()
    val j = getDayTime(level)
    return j
}

/**
 * Teleport a collection of entities to another entity's position.
 *
 * @param collection entities to teleport
 * @param entity destination entity whose position to use
 */
fun teleportToEntity(collection: MutableCollection<out Entity>, entity: Entity) {
    for (entity2 in collection) {
        performTeleport(
            entity2,
            entity.level() as ServerLevel,
            entity.position(),
            entity.rotationVector,
            null
        )
    }
}

/**
 * Teleport a collection of entities to a given position and optionally set rotation.
 *
 * @param collection entities to teleport
 * @param serverLevel destination level
 * @param pos destination position
 * @param rot optional rotation vector; if null, keeps entity rotation
 */
fun teleportToPos(
    collection: MutableCollection<out Entity>,
    serverLevel: ServerLevel,
    pos: Vec3,
    rot: Vec2? = null
) {
    for (entity in collection) {
        if (rot == null) {
            performTeleport(entity, serverLevel, pos, entity.rotationVector, null)
        } else {
            performTeleport(entity, serverLevel, pos, rot, null)
        }
    }
}


/**
 * Teleport a collection of entities to a position and make them look at an entity.
 *
 * @param collection entities to teleport
 * @param serverLevel destination level
 * @param pos destination position
 * @param lookAt entity to look at after teleport
 * @param anchor anchor used for target orientation
 * @param lookAtAnchor anchor used for lookAt orientation
 */
fun teleportToPos(
    collection: MutableCollection<out Entity>,
    serverLevel: ServerLevel,
    pos: Vec3,
    lookAt: Entity,
    anchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET,
    lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET
) {
    for (entity in collection) {
        performTeleport(entity, serverLevel, pos, entity.rotationVector, LookAt.LookAtEntity(lookAt, lookAtAnchor), anchor)
    }
}

/**
 * Teleport a collection of entities to a position and make them look at a position.
 *
 * @param collection entities to teleport
 * @param serverLevel destination level
 * @param pos destination position
 * @param lookAt position to look at
 * @param anchor anchor used for target orientation
 */
fun teleportToPos(
    collection: MutableCollection<out Entity>,
    serverLevel: ServerLevel,
    pos: Vec3,
    lookAt: Vec3,
    anchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET,
) {
    for (entity in collection) {
        performTeleport(entity, serverLevel, pos, entity.rotationVector, LookAt.LookAtPosition(lookAt), anchor)
    }
}

/**
 * Internal teleport helper performing checks and applying lookAt behavior.
 *
 * @param entity entity to teleport
 * @param serverLevel destination level
 * @param pos destination position
 * @param rot rotation vector to apply
 * @param lookAt optional LookAt behavior
 * @param anchor optional anchor for LookAt
 */
private fun performTeleport(
    entity: Entity,
    serverLevel: ServerLevel,
    pos: Vec3,
    rot: Vec2,
    lookAt: LookAt?,
    anchor: EntityAnchorArgument.Anchor? = null
) {
    val blockPos = BlockPos.containing(pos.x, pos.y, pos.z)
    if (!Level.isInSpawnableBounds(blockPos)) {
        LOGGER.error("Invalid position for teleport")
        return
    } else {
        val y = Mth.wrapDegrees(rot.y)
        val x = Mth.wrapDegrees(rot.x)
        if (entity.teleportTo(serverLevel, pos.x, pos.y, pos.z, EnumSet.noneOf(Relative::class.java), y, x, true)) {
            lookAt?.perform(requireServer().createCommandSourceStack().withAnchor(anchor!!), entity)

            if (!(entity is LivingEntity && entity.isFallFlying)) {
                entity.deltaMovement = entity.deltaMovement.multiply(1.0, 0.0, 1.0)
                entity.setOnGround(true)
            }

            if (entity is PathfinderMob) {
                entity.getNavigation().stop()
            }
        }
    }
}

/**
 * Add a trigger score value for a player on a trigger objective.
 *
 * @param serverPlayer player to modify
 * @param objective trigger objective
 * @param i amount to add
 */
fun addTriggerValue(serverPlayer: ServerPlayer, objective: Objective, i: Int){
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.add(i)
}

/**
 * Set a trigger score value for a player on a trigger objective.
 *
 * @param serverPlayer player to modify
 * @param objective trigger objective
 * @param i value to set
 */
fun setTriggerValue(serverPlayer: ServerPlayer, objective: Objective, i: Int){
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.set(i)
}

/**
 * Simple trigger: increment a trigger objective for a player by 1.
 *
 * @param serverPlayer player to trigger
 * @param objective trigger objective
 */
fun simpleTrigger(serverPlayer: ServerPlayer, objective: Objective) {
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.add(1)
}

/**
 * Internal helper to get and lock a trigger score.
 *
 * @param scoreboard scoreboard instance
 * @param scoreHolder target score holder
 * @param objective trigger objective
 * @return ScoreAccess if available and locked, null otherwise
 */
private fun getTriggerScore(scoreboard: Scoreboard, scoreHolder: ScoreHolder, objective: Objective): ScoreAccess? {
    if (objective.criteria !== ObjectiveCriteria.TRIGGER) {
        LOGGER.error("You can only trigger objectives that are 'trigger' type")
        return null
    } else {
        val readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective)
        if (readOnlyScoreInfo != null && !readOnlyScoreInfo.isLocked) {
            val scoreAccess = scoreboard.getOrCreatePlayerScore(scoreHolder, objective)
            scoreAccess.lock()
            return scoreAccess
        } else {
            LOGGER.error("You cannot trigger this objective yet")
            return null
        }
    }
}

/**
 * Set waypoint style for a waypoint transmitter.
 *
 * @param serverLevel server level
 * @param waypointTransmitter waypoint transmitter to modify
 * @param resourceKey waypoint style asset key
 */
fun setWaypointStyle(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, resourceKey: ResourceKey<WaypointStyleAsset>) {
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.style = resourceKey }
}

/**
 * Set waypoint color using ChatFormatting.
 *
 * @param serverLevel server level
 * @param waypointTransmitter waypoint transmitter
 * @param chatFormatting formatting to convert to color
 */
fun setWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, chatFormatting: ChatFormatting){
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.of<Int>(chatFormatting.color!!) }
}

/**
 * Set waypoint color using an integer color value.
 *
 * @param serverLevel server level
 * @param waypointTransmitter waypoint transmitter
 * @param integer integer color value
 */
fun setWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, integer: Int){
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.of<Int>(integer) }
}

/**
 * Reset waypoint color to default (unset).
 *
 * @param serverLevel server level
 * @param waypointTransmitter waypoint transmitter
 */
fun resetWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter) {
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.empty<Int>() }
}

/**
 * Internal helper to mutate a waypoint icon: untrack, apply consumer, then re-track.
 *
 * @param serverLevel server level
 * @param waypointTransmitter transmitter to mutate
 * @param consumer consumer that updates the icon
 */
private fun mutateIcon(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, consumer: Consumer<Waypoint.Icon>) {
    serverLevel.waypointManager.untrackWaypoint(waypointTransmitter)
    consumer.accept(waypointTransmitter.waypointIcon())
    serverLevel.waypointManager.trackWaypoint(waypointTransmitter)
}


/**
 * Resolve duration: if i == -1 use IntProvider sampled value; otherwise return i.
 *
 * @param i provided duration (-1 means sample)
 * @param intProvider provider to sample from
 * @return resolved duration
 */
fun getDuration(i: Int, intProvider: IntProvider): Int {
    return if (i == -1) intProvider.sample(requireServer().overworld().getRandom()) else i
}

/**
 * Set clear weather for specified duration (or sample when -1).
 *
 * @param i duration ticks or -1 to sample
 */
fun setClear(i: Int){
    requireServer().overworld()
        .setWeatherParameters(getDuration(i, ServerLevel.RAIN_DELAY), 0, false, false)
}

/**
 * Set rain weather for specified duration (or sample when -1).
 *
 * @param i duration ticks or -1 to sample
 */
fun setRain(i: Int){
    requireServer().overworld()
        .setWeatherParameters(0, getDuration(i, ServerLevel.RAIN_DURATION), true, false)
}

/**
 * Set thunder weather for specified duration (or sample when -1).
 *
 * @param i duration ticks or -1 to sample
 */
fun setThunder(i: Int){
    requireServer().overworld()
        .setWeatherParameters(0, getDuration(i, ServerLevel.THUNDER_DURATION), true, true)
}

/**
 * Set world border safe-zone buffer.
 *
 * @param level server level whose border to modify
 * @param distance safe distance buffer
 */
fun setWorldBorderDamageBuffer(level: ServerLevel, distance: Double){
    val border = level.worldBorder
    if(border.safeZone == distance) return
    border.safeZone = distance
}

/**
 * Set world border damage per block amount.
 *
 * @param level server level
 * @param damage damage per block
 */
fun setWorldBorderDamageAmount(level: ServerLevel, damage: Double){
    val border = level.worldBorder
    if(border.damagePerBlock == damage) return
    border.damagePerBlock = damage
}

/**
 * Set world border warning time.
 *
 * @param level server level
 * @param time warning time in seconds
 */
fun setWorldBorderWarningTime(level: ServerLevel, time: Int){
    val border = level.worldBorder
    if(border.warningTime == time) return
    border.warningTime = time
}

/**
 * Set world border warning distance in blocks.
 *
 * @param level server level
 * @param distance warning distance
 */
fun setWorldBorderWarningDistance(level: ServerLevel, distance: Int){
    val border = level.worldBorder
    if(border.warningBlocks == distance) return
    border.warningBlocks = distance
}

/**
 * Get the current world border size.
 *
 * @param level server level
 * @return world border size (double)
 */
fun getWorldBorderSize(level: ServerLevel): Double {
    return level.worldBorder.size
}

/**
 * Set world border size, optionally over time.
 *
 * @param level server level
 * @param size target border size
 * @param time time in ticks to lerp to new size (0 for instant)
 */
fun setWorldBorderSize(level: ServerLevel, size: Double, time: Long = 0L){
    val border = level.worldBorder
    if(border.size == size) return
    if(size < 1.0){
        LOGGER.error("World border cannot be smaller than 1 block wide")
        return
    }else if(size > 5.999997E7f){
        LOGGER.error("World border cannot be bigger than 59,999,970 blocks wide")
        return
    }
    if(time > 0L){
        border.lerpSizeBetween(border.size, size, time, level.gameTime)
    }else {
        border.setSize(size)
    }
}

