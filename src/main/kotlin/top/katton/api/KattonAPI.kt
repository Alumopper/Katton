@file:Suppress("unused")

package top.katton.api

import com.mojang.brigadier.StringReader
import com.mojang.datafixers.util.Pair
import com.mojang.logging.LogUtils
import net.minecraft.ChatFormatting
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

// ==================== 命令执行 ====================

fun executeCommand(source: CommandSourceStack, command: String) {
    val srv = source.server
    srv.commands.performPrefixedCommand(source, command)
}

fun executeCommandAsServer(command: String) {
    val srv = requireServer()
    val source = srv.createCommandSourceStack()
    srv.commands.performPrefixedCommand(source, command)
}

fun parseEntitySelector(source: CommandSourceStack, selector: String): EntitySelector {
    val reader = StringReader(selector)
    val parser = EntitySelectorParser(reader, true)
    return parser.parse()
}

fun selectEntities(source: CommandSourceStack, selector: String): List<Entity> {
    return parseEntitySelector(source, selector).findEntities(source)
}

fun selectPlayers(source: CommandSourceStack, selector: String): List<net.minecraft.server.level.ServerPlayer> {
    return parseEntitySelector(source, selector).findPlayers(source)
}

fun parseNbt(nbt: String): CompoundTag = TagParser.parseCompoundFully(nbt)

fun getEntityNbt(entity: Entity): CompoundTag {
    val accessor = EntityDataAccessor(entity)
    return accessor.data
}

fun setEntityNbt(entity: Entity, tag: CompoundTag) {
    val accessor = EntityDataAccessor(entity)
    accessor.data = tag
}

fun getBlockNbt(block: BlockEntity): CompoundTag {
    val accessor = BlockDataAccessor(block, block.blockPos)
    return accessor.data
}

fun setBlockNbt(block: BlockEntity, tag: CompoundTag) {
    val accessor = BlockDataAccessor(block, block.blockPos)
    accessor.data = tag
}

fun getBlockNbt(level: Level, pos: BlockPos): CompoundTag? {
    return level.getBlockEntity(pos)?.let { getBlockNbt(it) }
}

fun setBlockNbt(level: Level, pos: BlockPos, tag: CompoundTag): Boolean {
    return level.getBlockEntity(pos)?.let {
        setBlockNbt(it, tag)
        true
    } ?: false
}

fun getStorageNbt(id: Identifier): CompoundTag{
    return requireServer().commandStorage.get(id)
}

fun setStorageNbt(id: Identifier, tag: CompoundTag) {
    requireServer().commandStorage.set(id, tag)
}

private fun scoreboard(): Scoreboard = requireServer().scoreboard

fun getObjective(name: String): Objective? = scoreboard().getObjective(name)

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

fun setScore(target: String, objective: Objective, value: Int) = setScore(ScoreHolder.forNameOnly(target), objective, value)

fun setScore(target: Entity, objective: Objective, value: Int) = setScore(target as ScoreHolder, objective, value)

fun setScore(target: ScoreHolder, objective: Objective, value: Int) {
    val score = scoreboard().getOrCreatePlayerScore(target, objective)
    score.set(value)
}

fun addScore(target: String, objective: Objective, delta: Int) = addScore(ScoreHolder.forNameOnly(target), objective, delta)

fun addScore(target: Entity, objective: Objective, delta: Int) = addScore(target as ScoreHolder, objective, delta)

fun addScore(target: ScoreHolder, objective: Objective, delta: Int) {
    val score = scoreboard().getOrCreatePlayerScore(target, objective)
    score.add(delta)
}

fun getScore(target: String, objective: Objective): Int? = getScore(ScoreHolder.forNameOnly(target), objective)

fun getScore(target: Entity, objective: Objective): Int? = getScore(target as ScoreHolder, objective)

fun getScore(target: ScoreHolder, objective: Objective): Int? {
    val readOnlyScoreInfo = scoreboard().getPlayerScoreInfo(target, objective)
    return readOnlyScoreInfo?.value()
}

fun resetScore(target: String, objective: Objective) = resetScore(ScoreHolder.forNameOnly(target), objective)

fun resetScore(target: Entity, objective: Objective) = resetScore(target as ScoreHolder, objective)

fun resetScore(target: ScoreHolder, objective: Objective) {
    scoreboard().resetSinglePlayerScore(target, objective)
}

fun getAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Double {
    return entity.getAttributeValue(attribute)
}

fun hasAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Boolean {
    return entity.getAttribute(attribute) != null
}

fun getBaseAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Double? {
    return entity.getAttribute(attribute)?.baseValue
}

fun setBaseAttribute(entity: LivingEntity, attribute: Holder<Attribute>, value: Double): Boolean {
    return entity.getAttribute(attribute)?.baseValue?.let {
        it != value
    } ?: false
}

fun addAttributeModify(entity: LivingEntity, attribute: Holder<Attribute>, modifier: AttributeModifier) {
    entity.getAttribute(attribute)?.addTransientModifier(modifier)
}

fun removeAttributeModify(entity: LivingEntity, attribute: Holder<Attribute>, modifier: AttributeModifier) {
    entity.getAttribute(attribute)?.removeModifier(modifier)
}

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

fun getOrCreateBossBar(identifier: Identifier, title: Component): CustomBossEvent {
    val qwq = requireServer().customBossEvents
    if (qwq.get(identifier) != null) return qwq.get(identifier)!!
    return qwq.create(identifier,title)
}

fun clearInventory(player: Player) {
    player.inventory.clearContent()
}

fun setItem(player: Player, slot: Int, itemStack: ItemStack) {
    player.inventory.setItem(slot, itemStack)
    if (player is ServerPlayer) {
        player.inventoryMenu.sendAllDataToRemote()
    }
}

fun getItem(player: Player, slot: Int): ItemStack {
    return player.inventory.getItem(slot)
}

fun giveItem(player: Player, itemStack: ItemStack): Boolean {
    return player.inventory.add(itemStack)
}

fun hasItem(player: Player, item: Item): Int {
    return player.inventory.findSlotMatchingItem(ItemStack(item))
}

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

fun setBlock(level: Level, pos: BlockPos, state: BlockState) {
    level.setBlock(pos, state, 3)
}

fun setBlock(level: Level, pos: BlockPos, block: Block) {
    setBlock(level, pos, block.defaultBlockState())
}

fun fill(level: Level, start: BlockPos, end: BlockPos, state: BlockState) {
    BlockPos.betweenClosed(start, end).forEach { pos ->
        level.setBlock(pos, state, 3)
    }
}

fun fill(level: Level, start: BlockPos, end: BlockPos, block: Block) {
    fill(level, start, end, block.defaultBlockState())
}

fun damage(entity: Entity, amount: Float) {
    entity.hurtServer(
        requireServer().overworld(),
        requireServer().overworld().damageSources().source(DamageTypes.GENERIC),
        amount
    )
}

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

fun damage(target: Entity, amount: Float, attacker: Entity, damageType: DamageType) {
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(Holder.direct(damageType), attacker, attacker),
        amount
    )
}


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

fun damage(target: Entity, amount: Float, pos: Vec3, damageType: DamageType) {
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(Holder.direct(damageType), pos),
        amount
    )
}

fun deop(player: ServerPlayer) {
    requireServer().playerList.deop(player.nameAndId())
}

fun op(player: ServerPlayer) {
    requireServer().playerList.op(player.nameAndId())
}

fun setDifficulty(difficulty: Difficulty, ignoreLock: Boolean = true) {
    requireServer().setDifficulty(difficulty, ignoreLock)
}

fun getDifficulty(): Difficulty {
    return requireServer().overworld().difficulty
}

// ==================== Effect ====================

fun addEffect(entity: LivingEntity, effect: Holder<MobEffect>, duration: Int = 600, amplifier: Int = 0, showParticles: Boolean = true, ambient: Boolean = false) {
    entity.addEffect(MobEffectInstance(effect, duration, amplifier, ambient, showParticles))
}

fun removeEffect(entity: LivingEntity, effect: Holder<MobEffect>) {
    entity.removeEffect(effect)
}

fun clearEffects(entity: LivingEntity) {
    entity.removeAllEffects()
}

fun enchant(itemStack: ItemStack, enchantment: Holder<Enchantment>, level: Int) {
    itemStack.enchant(enchantment, level)
}

fun enchantMainHand(entity: LivingEntity, enchantment: Holder<Enchantment>, level: Int) {
    val stack = entity.mainHandItem
    if (!stack.isEmpty) {
        stack.enchant(enchantment, level)
    }
}

fun addXpPoints(player: Player, points: Int) {
    player.giveExperiencePoints(points)
}

fun addXpLevels(player: Player, levels: Int) {
    player.giveExperienceLevels(levels)
}

fun setXpLevel(player: Player, level: Int) {
    player.experienceLevel = level
}

fun getXpLevel(player: Player): Int {
    return player.experienceLevel
}

fun getXpProgress(player: Player): Float {
    return player.experienceProgress
}

// ==================== FillBiome ====================

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

fun runFunction(id: Identifier, source: CommandSourceStack = requireServer().createCommandSourceStack()) {
    requireServer().functions.get(id).ifPresent {
        requireServer().functions.execute(it, source)
    }
}

fun setGameMode(player: ServerPlayer, gameMode: GameType) {
    player.setGameMode(gameMode)
}

fun getGameMode(player: ServerPlayer): GameType {
    return player.gameMode.gameModeForPlayer
}

fun <T : Any> setGameRule(key: GameRule<T>, value: T) {
    try{
        requireServer().overworld().gameRules.set(key, value, requireServer())
    }catch (e: IllegalArgumentException) {
        LOGGER.warn("Failed to set game rule $key to $value", e)
    }
}

fun <T : Any> getGameRule(key: GameRule<T>): T {
    return requireServer().overworld().gameRules.get(key)
}

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

fun setEntityItem(entity: Entity, slot: Int, itemStack: ItemStack) {
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return
    }
    if (slotAccess.set(itemStack) && entity is ServerPlayer) {
        entity.containerMenu.broadcastChanges()
    }
}

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

fun getEntityItem(entity: Entity, slot: Int): ItemStack? {
    val slotAccess = entity.getSlot(slot) ?: run {
        LOGGER.warn("Entity ${entity.uuid} does not have slot $slot")
        return null
    }
    return slotAccess.get()
}

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

fun kick(player: Player, reason: Component = Component.translatable("multiplayer.disconnect.kicked")) {
    if (player is ServerPlayer) {
        player.connection.disconnect(reason)
    }
}

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

fun dropChestLoot(lootTable: LootTable): List<ItemStack> {
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
    val params = builder.create(LootContextParamSets.CHEST)
    return lootTable.getRandomItems(params)
}

fun dropFishingLoot(lootTable: LootTable, pos: BlockPos, tool: ItemStack): List<ItemStack> {
    val builder = LootParams.Builder(requireServer().overworld())
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withParameter(LootContextParams.TOOL, tool)
        .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
    val params = builder.create(LootContextParamSets.FISHING)
    return lootTable.getRandomItems(params)
}

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

fun dropToPlayer(player: ServerPlayer, itemStacks: List<ItemStack>) {
    for(item in itemStacks){
        player.inventory.add(item.copy())
    }
}

fun dropToEntity(entity: Entity, i: Int, j: Int, itemStacks: List<ItemStack>) {
    for(k in 0 until j){
        val itemStack = if(k < itemStacks.size) itemStacks[k] else ItemStack.EMPTY
        val slotAccess = entity.getSlot(i + k)
        slotAccess?.set(itemStack.copy())
    }
}

fun dropTo(level: Level, pos: Vec3, itemStacks: List<ItemStack>) {
    for(item in itemStacks){
        val itemEntity = ItemEntity(level, pos.x, pos.y, pos.z, item.copy())
        itemEntity.setDefaultPickUpDelay()
        level.addFreshEntity(itemEntity)
    }
}

fun tell(player: ServerPlayer, message: String) {
    player.sendSystemMessage(Component.literal(message))
}

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


fun locateStructure(structureKey: TagKey<Structure>, level: ServerLevel, startPos: BlockPos = BlockPos.ZERO): BlockPos? {
    val holderSet = requireServer().overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structureKey)
    if(holderSet.isEmpty){
        LOGGER.warn("Structure $structureKey not found")
        return null
    }
    val result = level.chunkSource.generator.findNearestMapStructure(level, holderSet.get(), startPos, 100, false)
    return result?.first
}

fun locateBiome(biomeKey: ResourceKey<Biome>, level: ServerLevel, startPos: BlockPos = BlockPos.ZERO): Pair<BlockPos, Holder<Biome>>? {
    val biome = requireServer().overworld().registryAccess().lookupOrThrow(Registries.BIOME).get(biomeKey)
    if(biome.isEmpty){
        LOGGER.warn("Biome $biomeKey not found")
        return null
    }
    val holderSet = HolderSet.direct(biome.get())
    return level.findClosestBiome3d({it == biome}, startPos, 6400, 32, 64)
}


fun locateBiome(biomeKey: TagKey<Biome>, level: ServerLevel, startPos: BlockPos = BlockPos.ZERO): Pair<BlockPos, Holder<Biome>>? {
    val holderSet = requireServer().overworld().registryAccess().lookupOrThrow(Registries.BIOME).get(biomeKey)
    if(holderSet.isEmpty){
        LOGGER.warn("Biome $biomeKey not found")
        return null
    }
    return level.findClosestBiome3d({holderSet.get().contains(it)}, startPos, 6400, 32, 64)
}

fun placeFeature(feature: Identifier, pos: BlockPos){
    val f = requireServer().registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(feature)
    if(f.isEmpty){
        LOGGER.warn("Feature $feature not found")
        return
    }
    PlaceCommand.placeFeature(requireServer().createCommandSourceStack(), f.get(), pos)
}

fun placeJigsaw(templatePool: Identifier, start: Identifier, depth: Int, pos: BlockPos){
    val templatePoolHolder = requireServer().overworld().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).get(templatePool)
    if(templatePoolHolder.isEmpty){
        LOGGER.warn("Jigsaw pool $templatePool not found")
        return
    }
    PlaceCommand.placeJigsaw(requireServer().createCommandSourceStack(), templatePoolHolder.get(), start, depth, pos)
}

fun placeStructure(structure: Identifier, pos: BlockPos){
    val structureHolder = requireServer().overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structure)
    if(structureHolder.isEmpty){
        LOGGER.warn("Structure $structure not found")
        return
    }
    PlaceCommand.placeStructure(requireServer().createCommandSourceStack(), structureHolder.get(), pos)
}

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

fun resetSequence(level: ServerLevel, randomSequence: Identifier){
level.randomSequences.reset(randomSequence, level.seed)
}

fun resetSequence(level: ServerLevel, randomSequence: Identifier, seed: Int, includeWorldSeed: Boolean = true, includeSequenceID: Boolean = true){
    requireServer().overworld().randomSequences.reset(randomSequence, level.seed, seed, includeWorldSeed, includeSequenceID)
}

fun resetAllSequences(level: ServerLevel){
    level.randomSequences.clear()
}

fun resetAllSequencesAndSetNewDefaults(level: ServerLevel, seed: Int, includeWorldSeed: Boolean = true, includeSequenceID: Boolean = true){
    level.randomSequences.setSeedDefaults(seed, includeWorldSeed, includeSequenceID)
}

fun giveRecipes(players: Collection<ServerPlayer>, recipes: Collection<RecipeHolder<*>>){
    for(player in players){
        player.awardRecipes(recipes)
    }
}

fun takeRecipes(players: Collection<ServerPlayer>, recipes: Collection<RecipeHolder<*>>){
    for(player in players){
        player.resetRecipes(recipes)
    }
}

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

fun dismount(passenger: Entity): Boolean {
    if(passenger.vehicle == null){
        LOGGER.error("${passenger.displayName} is not riding any vehicle")
        return false
    }
    passenger.stopRiding()
    return true
}

fun rotate(target: Entity, rot: Vec2, relative: Boolean = false){
    target.forceSetRotation(rot.y, relative, rot.x, relative)
}

fun rotate(target: Entity, lookAt: Entity, targetAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET, lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET){
    if(target is ServerPlayer){
        target.lookAt(targetAnchor, lookAt, lookAtAnchor)
    }else {
        target.lookAt(targetAnchor, lookAtAnchor.apply(lookAt))
    }
}

fun rotate(target: Entity, lookAt: Vec3, targetAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET, lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET){
    target.lookAt(targetAnchor, lookAt)
}

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

fun setWorldSpawn(level: ServerLevel, blockPos: BlockPos, rot: Vec2) {
    val f = rot.y
    val g = rot.x
    val respawnData = RespawnData.of(level.dimension(), blockPos, f, g)
    level.respawnData = respawnData
}

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


fun getTags(entity: Entity): MutableCollection<String> {
    return entity.tags
}

fun addTag(entity: Entity, string: String): Boolean {
    return entity.addTag(string)
}

fun removeTag(entity: Entity, string: String): Boolean {
    return entity.removeTag(string)
}

fun leaveTeam(members: Collection<ScoreHolder>){
    val scoreboard = requireServer().scoreboard
    for(member in members){
        scoreboard.removePlayerFromTeam(member.scoreboardName)
    }
}

fun joinTeam(team: PlayerTeam, members: Collection<ScoreHolder>){
    val scoreboard = requireServer().scoreboard
    for(member in members){
        scoreboard.addPlayerToTeam(member.scoreboardName, team)
    }
}

fun emptyTeam(team: PlayerTeam) {
    val scoreboard = requireServer().scoreboard
    for(member in team.players){
        scoreboard.removePlayerFromTeam(member, team)
    }
}

fun deleteTeam(team: PlayerTeam) {
    val scoreboard = requireServer().scoreboard
    scoreboard.removePlayerTeam(team)
}

fun createTeam(name: String, displayName: Component = Component.literal(name)){
    val scoreboard = requireServer().scoreboard
    if(scoreboard.getPlayerTeam(name) != null) return
    val team = scoreboard.addPlayerTeam(name)
    team.displayName = displayName
}

fun getTeam(name: String): PlayerTeam? {
    return requireServer().scoreboard.getPlayerTeam(name)
}

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


fun setTickingRate(f: Float) {
    requireServer().tickRateManager().setTickRate(f)
}

fun tickQuery(): Float {
    return requireServer().tickRateManager().tickrate()
}

fun tickSprint(i: Int){
    requireServer().tickRateManager().requestGameToSprint(i)
}

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

fun tickStep(i: Int) {
    val serverTickRateManager = requireServer().tickRateManager()
    serverTickRateManager.stepGameIfPaused(i)
}

fun tickStopStepping(): Boolean {
    val serverTickRateManager = requireServer().tickRateManager()
    return serverTickRateManager.stopStepping()
}

fun tickStopSprinting(): Boolean {
    val serverTickRateManager = requireServer().tickRateManager()
    return serverTickRateManager.stopSprinting()
}


fun getDayTime(serverLevel: ServerLevel): Int {
    return (serverLevel.dayTime % 24000L).toInt()
}

fun setTime(level: ServerLevel ,i: Int): Int {
    for (serverLevel in requireServer().allLevels) {
        serverLevel.dayTime = i.toLong()
    }

    requireServer().forceTimeSynchronization()
    return getDayTime(level)
}

fun addTime(level: ServerLevel, i: Int): Int {
    for (serverLevel in requireServer().allLevels) {
        serverLevel.dayTime += i
    }

    requireServer().forceTimeSynchronization()
    val j = getDayTime(level)
    return j
}

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

fun addTriggerValue(serverPlayer: ServerPlayer, objective: Objective, i: Int){
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.add(i)
}

fun setTriggerValue(serverPlayer: ServerPlayer, objective: Objective, i: Int){
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.set(i)
}

fun simpleTrigger(serverPlayer: ServerPlayer, objective: Objective) {
    val scoreAccess = getTriggerScore(requireServer().scoreboard, serverPlayer, objective)
    scoreAccess?.add(1)
}

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

fun setWaypointStyle(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, resourceKey: ResourceKey<WaypointStyleAsset>) {
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.style = resourceKey }
}

fun setWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, chatFormatting: ChatFormatting){
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.of<Int?>(chatFormatting.color!!) }
}

fun setWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, integer: Int){
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.of<Int?>(integer) }
}

fun resetWaypointColor(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter) {
    mutateIcon(
        serverLevel,
        waypointTransmitter
    ) { icon: Waypoint.Icon -> icon.color = Optional.empty<Int?>() }
}

private fun mutateIcon(serverLevel: ServerLevel, waypointTransmitter: WaypointTransmitter, consumer: Consumer<Waypoint.Icon>) {
    serverLevel.waypointManager.untrackWaypoint(waypointTransmitter)
    consumer.accept(waypointTransmitter.waypointIcon())
    serverLevel.waypointManager.trackWaypoint(waypointTransmitter)
}


fun getDuration(i: Int, intProvider: IntProvider): Int {
    return if (i == -1) intProvider.sample(requireServer().overworld().getRandom()) else i
}

fun setClear(i: Int){
    requireServer().overworld()
        .setWeatherParameters(getDuration(i, ServerLevel.RAIN_DELAY), 0, false, false)
}

fun setRain(i: Int){
    requireServer().overworld()
        .setWeatherParameters(0, getDuration(i, ServerLevel.RAIN_DURATION), true, false)
}

fun setThunder(i: Int){
    requireServer().overworld()
        .setWeatherParameters(0, getDuration(i, ServerLevel.THUNDER_DURATION), true, true)
}

fun setWorldBorderDamageBuffer(level: ServerLevel, distance: Double){
    val border = level.worldBorder
    if(border.safeZone == distance) return
    border.safeZone = distance
}

fun setWorldBorderDamageAmount(level: ServerLevel, damage: Double){
    val border = level.worldBorder
    if(border.damagePerBlock == damage) return
    border.damagePerBlock = damage
}

fun setWorldBorderWarningTime(level: ServerLevel, time: Int){
    val border = level.worldBorder
    if(border.warningTime == time) return
    border.warningTime = time
}

fun setWorldBorderWarningDistance(level: ServerLevel, distance: Int){
    val border = level.worldBorder
    if(border.warningBlocks == distance) return
    border.warningBlocks = distance
}

fun getWorldBorderSize(level: ServerLevel): Double {
    return level.worldBorder.size
}

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

