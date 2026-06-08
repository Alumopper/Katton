@file:Suppress("unused")

package top.katton.api.dpcaller

import com.mojang.datafixers.util.Pair
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.commands.FillBiomeCommand
import net.minecraft.server.commands.PlaceCommand
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.world.clock.ClockTimeMarker
import net.minecraft.world.clock.WorldClock
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.phys.Vec3
import net.minecraft.world.timeline.Timeline
import top.katton.api.LOGGER
import top.katton.api.requireServer
import top.katton.util.Result
import kotlin.jvm.optionals.getOrDefault
import kotlin.math.sqrt

/**
 * %en
 * World management API for level operations.
 *
 * This module provides functions for working with Minecraft worlds including:
 * - Level access and management
 * - Block operations (get, set, fill)
 * - Biome manipulation
 * - Structure location
 * - Sound playing
 *
 * %zh
 * 世界管理 API，用于关卡相关操作。
 * 本模块提供一组处理 Minecraft 世界的函数，包括：
 * - 关卡访问与管理
 * - 方块操作（获取、设置、填充）
 * - 生物群系操作
 * - 结构定位
 * - 声音播放
 */

/**
 * %en
 * Map-like access to all server levels by ResourceKey.
 *
 * %zh
 * 以类似 Map 的方式按 ResourceKey 访问所有服务端关卡。
 */
class KattonLevelMap(
    private val server: MinecraftServer
) : Map<ResourceKey<Level>, ServerLevel> {
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

    /**
 * %en
 * Quick access to the Overworld level.
 *
 * %zh
 * 快速访问主世界关卡。
 */
    val overworld: ServerLevel
        get() = get(Level.OVERWORLD) ?: error("Overworld not found")
    
    /**
 * %en
 * Quick access to the Nether level.
 *
 * %zh
 * 快速访问下界关卡。
 */
    val nether: ServerLevel
        get() = get(Level.NETHER) ?: error("Nether not found")
    
    /**
 * %en
 * Quick access to the End level.
 *
 * %zh
 * 快速访问末地关卡。
 */
    val end: ServerLevel
        get() = get(Level.END) ?: error("End not found")
}

/**
 * %en
 * Map-like access to all server levels.
 *
 * %zh
 * 以类似 Map 的方式访问所有服务端关卡。
 */
val levels: KattonLevelMap
    get() = KattonLevelMap(requireServer())

/**
 * %en
 * Access to players in a level.
 *
 * %zh
 * 访问关卡中的玩家。
 */
val ServerLevel.players: KattonLevelPlayerCollection
    get() = KattonLevelPlayerCollection(this)

/**
 * %en
 * Access to block entities in a level.
 *
 * %zh
 * 访问关卡中的方块实体。
 */
val ServerLevel.blockEntities: KattonLevelBlockEntityCollection
    get() = KattonLevelBlockEntityCollection(this)

/**
 * %en
 * Access to entities in a level.
 *
 * %zh
 * 访问关卡中的实体。
 */
val ServerLevel.entities: KattonLevelEntityCollection
    get() = KattonLevelEntityCollection(this)

/**
 * %en
 * Access to blocks in a level.
 *
 * %zh
 * 访问关卡中的方块。
 */
val Level.blocks: KattonLevelBlockCollection
    get() = KattonLevelBlockCollection(this)

/**
 * %en
 * Access to block states in a level.
 *
 * %zh
 * 访问关卡中的方块状态。
 */
val Level.blockStates: KattonLevelBlockStateCollection
    get() = KattonLevelBlockStateCollection(this)

/**
 * %en
 * Set a block at position to a specific BlockState.
 *
 * %zh
 * 在指定位置设置特定方块状态。
 * @param level
 * %en level to modify
 * %zh 要修改的关卡。
 * @param pos
 * %en block position
 * %zh 方块位置。
 * @param state
 * %en BlockState to set
 * %zh 要设置的方块状态。
 */
fun setBlock(level: Level, pos: BlockPos, state: BlockState) {
    level.setBlock(pos, state, 3)
}


/**
 * %en
 * Set a block at position using a Block type's default state.
 *
 * %zh
 * 使用方块类型的默认状态在指定位置设置方块。
 * @param level
 * %en level to modify
 * %zh 要修改的关卡。
 * @param pos
 * %en block position
 * %zh 方块位置。
 * @param block
 * %en block type to set
 * %zh 要设置的方块类型。
 */
fun setBlock(level: Level, pos: BlockPos, block: Block) {
    setBlock(level, pos, block.defaultBlockState())
}


/**
 * %en
 * Fill a region with a given BlockState.
 *
 * %zh
 * 使用给定方块状态填充一个区域。
 * @param level
 * %en level to modify
 * %zh 要修改的关卡。
 * @param start
 * %en start position (inclusive)
 * %zh 起始位置（包含）。
 * @param end
 * %en end position (inclusive)
 * %zh 结束位置（包含）。
 * @param state
 * %en BlockState to place
 * %zh 要放置的方块状态。
 */
fun fill(level: Level, start: BlockPos, end: BlockPos, state: BlockState) {
    BlockPos.betweenClosed(start, end).forEach { pos ->
        level.setBlock(pos, state, 3)
    }
}


/**
 * %en
 * Fill a region with a given Block type using its default state.
 *
 * %zh
 * 使用给定方块类型的默认状态填充一个区域。
 * @param level
 * %en level to modify
 * %zh 要修改的关卡。
 * @param start
 * %en start position (inclusive)
 * %zh 起始位置（包含）。
 * @param end
 * %en end position (inclusive)
 * %zh 结束位置（包含）。
 * @param block
 * %en block type to place
 * %zh 要放置的方块类型。
 */
fun fill(level: Level, start: BlockPos, end: BlockPos, block: Block) {
    fill(level, start, end, block.defaultBlockState())
}


/**
 * %en
 * Fill a region's biome using a predicate on biome holder.
 *
 * %zh
 * 使用给定谓词筛选生物群系 Holder，并填充区域内的生物群系。
 * @param level
 * %en server-level to modify
 * %zh 要修改的服务端关卡。
 * @param start
 * %en start position
 * %zh 起始位置。
 * @param end
 * %en end position
 * %zh 结束位置。
 * @param biome
 * %en biome identifier to apply
 * %zh 要应用的生物群系标识符。
 * @param biomePredicate
 * %en predicate to further filter biome application
 * %zh 用于进一步筛选生物群系应用范围的谓词。
 */
fun fillBiome(level: Level, start: BlockPos, end: BlockPos, biome: Identifier, biomePredicate: (Holder<Biome>) -> Boolean) {
    if (level !is ServerLevel) return
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
 * %en
 * Locate a structure by ResourceKey.
 *
 * %zh
 * 按 ResourceKey 定位结构。
 * @param structureKey
 * %en structure resource key
 * %zh 结构资源键。
 * @param level
 * %en server level to search in
 * %zh 要搜索的服务端关卡。
 * @param startPos
 * %en starting position for search
 * %zh 搜索起始位置。
 * @return
 * %en BlockPos of the structure, or null if not found
 * %zh 返回结构所在的 BlockPos；找不到时返回 null。
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
 * %en
 * Locate a structure by TagKey.
 *
 * %zh
 * 按 TagKey 定位结构。
 * @param structureKey
 * %en structure tag key
 * %zh 结构标签键。
 * @param level
 * %en server level to search in
 * %zh 要搜索的服务端关卡。
 * @param startPos
 * %en starting position
 * %zh 起始位置。
 * @return
 * %en BlockPos, or null if not found
 * %zh 返回 BlockPos；找不到时返回 null。
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
 * %en
 * Find closest biome by resource key.
 *
 * %zh
 * 按 ResourceKey 查找最近的生物群系。
 * @param biomeKey
 * %en biome resource key
 * %zh 生物群系资源键。
 * @param level
 * %en server level to search
 * %zh 要搜索的服务端关卡。
 * @param startPos
 * %en starting position
 * %zh 起始位置。
 * @return
 * %en pair of BlockPos and biome Holder, or null if not found
 * %zh 返回方块位置和生物群系 Holder 的 Pair；找不到则返回 null。
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
 * %en
 * Find closest biome by tag key.
 *
 * %zh
 * 按 TagKey 查找最近的生物群系。
 * @param biomeKey
 * %en biome tag key
 * %zh 生物群系标签键。
 * @param level
 * %en server level to search
 * %zh 要搜索的服务端关卡。
 * @param startPos
 * %en starting position
 * %zh 起始位置。
 * @return
 * %en pair of BlockPos and biome Holder, or null if not found
 * %zh 返回方块位置和生物群系 Holder 的 Pair；找不到则返回 null。
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
 * %en
 * Place a configured feature at a position.
 *
 * %zh
 * 在指定位置放置已配置特性。
 * @param feature
 * %en feature identifier
 * %zh 特性标识符。
 * @param pos
 * %en position to place
 * %zh 放置位置。
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
 * %en
 * Place a jigsaw structure from a template pool.
 *
 * %zh
 * 从模板池放置拼图结构。
 * @param templatePool
 * %en template pool identifier
 * %zh 池标识符。
 * @param start
 * %en starting template identifier
 * %zh 起始模板标识符。
 * @param depth
 * %en placement depth
 * %zh 放置深度。
 * @param pos
 * %en starting position
 * %zh 起始位置。
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
 * %en
 * Place a structure by identifier at a position.
 *
 * %zh
 * 按标识符在指定位置放置结构。
 * @param structure
 * %en structure identifier
 * %zh 结构标识符。
 * @param pos
 * %en position to place
 * %zh 放置位置。
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
 * %en
 * Play a sound to a set of players, performing distance attenuation and minimum volume handling.
 *
 * %zh
 * 向一组玩家播放声音，并处理距离衰减和最小音量。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param players
 * %en players to send the sound to
 * %zh 要接收声音的玩家。
 * @param sound
 * %en sound identifier
 * %zh 声音标识符。
 * @param soundSource
 * %en sound source category
 * %zh 声音类别或来源。
 * @param pos
 * %en sound origin position
 * %zh 声音来源位置。
 * @param volume
 * %en base volume
 * %zh 基础音量。
 * @param pitch
 * %en playback pitch
 * %zh 播放音高。
 * @param minVolume
 * %en minimum audible volume when out of range
 * %zh 超出范围时的最小可听音量。
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
 * %en
 * Sample a random integer in [min, max] using optional random sequence.
 *
 * %zh
 * 使用可选随机序列在 [min, max] 范围内抽取随机整数。
 * @param randomSequence
 * %en optional random sequence identifier
 * %zh 随机序列的可选标识符。
 * @param broadcast
 * %en whether to broadcast the result to players
 * %zh 是否向玩家广播结果。
 * @param min
 * %en inclusive minimum value
 * %zh 包含的最小值。
 * @param max
 * %en inclusive maximum value
 * %zh 包含的最大值。
 * @return
 * %en integer or null on invalid range
 * %zh 返回随机整数；范围无效时返回 null。
 */
fun randomSample(randomSequence: Identifier? = null, broadcast: Boolean = false, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int?{
    val randomSource = randomSequence?.let { requireServer().getRandomSequence(it) } ?: requireServer().overworld().random
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
 * %en
 * Reset a named random sequence for a level to its default seed.
 *
 * %zh
 * 将关卡中的命名随机序列重置为默认种子。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param randomSequence
 * %en sequence identifier
 * %zh 序列标识符。
 */
fun resetSequence(level: ServerLevel, randomSequence: Identifier){
    requireServer().randomSequences.reset(randomSequence, level.seed)
}


/**
 * %en
 * Reset a named random sequence with a specific seed and behavior flags.
 *
 * %zh
 * 使用指定种子和行为标志重置命名随机序列。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param randomSequence
 * %en sequence identifier
 * %zh 序列标识符。
 * @param seed
 * %en integer seed
 * %zh 整数种子。
 * @param includeWorldSeed
 * %en whether to include the world seed
 * %zh 是否包含世界种子。
 * @param includeSequenceID
 * %en whether to include the sequence ID
 * %zh 是否包含序列 ID。
 */
fun resetSequence(level: ServerLevel, randomSequence: Identifier, seed: Int, includeWorldSeed: Boolean = true, includeSequenceID: Boolean = true){
    requireServer().randomSequences.reset(randomSequence, level.seed, seed, includeWorldSeed, includeSequenceID)
}


/**
 * %en
 * Clear all random sequences for a level.
 *
 * %zh
 * 清除关卡中的所有随机序列。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 */
fun resetAllSequences(level: ServerLevel){
    requireServer().randomSequences.clear()
}


/**
 * %en
 * Reset all sequences and set new defaults.
 *
 * %zh
 * 重置所有随机序列并设置新的默认值。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param seed
 * %en seed to use as the new default
 * %zh 要设为默认值的种子。
 * @param includeWorldSeed
 * %en whether to include the world seed
 * %zh 是否包含世界种子。
 * @param includeSequenceID
 * %en whether to include the sequence ID
 * %zh 是否包含序列 ID。
 */
fun resetAllSequencesAndSetNewDefaults(level: ServerLevel, seed: Int, includeWorldSeed: Boolean = true, includeSequenceID: Boolean = true){
    requireServer().randomSequences.setSeedDefaults(seed, includeWorldSeed, includeSequenceID)
}


/**
 * %en
 * Set the server ticking rate.
 *
 * %zh
 * 设置服务器 tick 速率。
 * @param f
 * %en new tick rate (ticks per second)
 * %zh 新的 tick 速率（每秒刻数）。
 */
fun setTickingRate(f: Float) {
    requireServer().tickRateManager().setTickRate(f)
}


/**
 * %en
 * Query current tickrate.
 *
 * %zh
 * 查询当前 tick 速率。
 * @return
 * %en tick rate value
 * %zh 返回 tick 速率值。
 */
fun tickQuery(): Float {
    return requireServer().tickRateManager().tickrate()
}


/**
 * %en
 * Request the server to sprint tick advancement by given ticks.
 *
 * %zh
 * 请求服务器按给定 tick 数快速推进。
 * @param i
 * %en number of ticks to sprint
 * %zh 要快速推进的 tick 数。
 */
fun tickSprint(i: Int){
    requireServer().tickRateManager().requestGameToSprint(i)
}


/**
 * %en
 * Freeze or unfreeze server tick processing.
 *
 * %zh
 * 冻结或解除冻结服务器 tick 处理。
 * @param freeze
 * %en true to freeze, false to unfreeze
 * %zh true 表示冻结，false 表示解除冻结。
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
 * %en
 * Step the server forward a number of ticks while paused.
 *
 * %zh
 * 服务器暂停时按指定 tick 数步进。
 * @param i
 * %en number of ticks to step
 * %zh 要步进的 tick 数。
 */
fun tickStep(i: Int) {
    val serverTickRateManager = requireServer().tickRateManager()
    serverTickRateManager.stepGameIfPaused(i)
}


/**
 * %en
 * Stop stepping mode on the tick manager.
 *
 * %zh
 * 停止 tick 管理器的步进模式。
 * @return
 * %en if stepping was stopped
 * %zh 如果已停止步进则返回 true。
 */
fun tickStopStepping(): Boolean {
    val serverTickRateManager = requireServer().tickRateManager()
    return serverTickRateManager.stopStepping()
}


/**
 * %en
 * Stop sprinting mode on the tick manager.
 *
 * %zh
 * 停止 tick 管理器的快速推进模式。
 * @return
 * %en if sprinting was stopped
 * %zh 如果已停止快速推进则返回 true。
 */
fun tickStopSprinting(): Boolean {
    val serverTickRateManager = requireServer().tickRateManager()
    return serverTickRateManager.stopSprinting()
}


fun queryGameTime(level: ServerLevel): Int {
    return  wrapTime(level.gameTime)
}


fun queryTime(clock: Holder<WorldClock>): Int {
    return wrapTime(requireServer().clockManager().getTotalTicks(clock))
}


fun queryTimelineTicks(clock: Holder<WorldClock>, timeline: Holder<Timeline>): Result<Int> {
    if(clock != timeline.value().clock()){
        return Result.failure("Timeline ${timeline.value()} is not valid for clock ${clock.value()}")
    }
    val currentTicks = timeline.value().getCurrentTicks(requireServer().clockManager())
    return Result.success(wrapTime(currentTicks))
}



fun queryTimelineRepetitions(clock: Holder<WorldClock>, timeline: Holder<Timeline>): Result<Int> {
    if(clock != timeline.value().clock()){
        return Result.failure("Timeline ${timeline.value()} is not valid for clock ${clock.value()}")
    }
    val r = timeline.value().getPeriodCount(requireServer().clockManager())
    return Result.success(wrapTime(r.toLong()))
}



fun setTotalTicks(clock: Holder<WorldClock>, ticks: Int) {
    requireServer().clockManager().setTotalTicks(clock, ticks.toLong())
}


fun addTime(clock: Holder<WorldClock>, ticks: Int) {
    requireServer().clockManager().addTicks(clock, ticks)
}


fun setTimeToTimeMarker(clock: Holder<WorldClock>, timeMarker: ResourceKey<ClockTimeMarker>): Boolean {
    return requireServer().clockManager().moveToTimeMarker(clock, timeMarker)
}


fun setPaused(clock: Holder<WorldClock>, paused: Boolean) {
    requireServer().clockManager().setPaused(clock, paused)
}


fun getDefaultClock(level: ServerLevel): Holder<WorldClock>? {
    return level.dimensionTypeRegistration().value().defaultClock.getOrDefault(null)
}


private fun wrapTime(ticks: Long): Int {
    return Math.toIntExact(ticks % 2147483647L)
}


/**
 * %en
 * Resolve duration: if i == -1 use IntProvider sampled value; otherwise return i.
 *
 * %zh
 * 解析持续时间：如果 [i] 为 -1，则从 [intProvider] 抽样；否则返回 [i]。
 * @param i
 * %en provided duration (-1 means sample)
 * %zh 提供的持续时间，-1 表示抽样。
 * @param intProvider
 * %en provider to sample from
 * %zh 用于抽样的 IntProvider。
 * @return
 * %en duration
 * %zh 返回持续时间。
 */
fun getDuration(level: ServerLevel, i: Int, intProvider: IntProvider): Int {
    return if (i == -1) intProvider.sample(requireServer().overworld().getRandom()) else i
}


/**
 * %en
 * Set clear weather for specified duration (or sample when -1).
 *
 * %zh
 * 设置晴朗天气的持续时间；传入 -1 时从 IntProvider 抽样。
 * @param i
 * %en duration in ticks, or -1 to sample
 * %zh 持续 tick 数，或 -1 表示抽样。
 */
fun setClear(level: ServerLevel, i: Int){
    requireServer().setWeatherParameters(
        getDuration(level, i, ServerLevel.RAIN_DELAY),
        0,
        false,
        false
    )
}


/**
 * %en
 * Set rain weather for specified duration (or sample when -1).
 *
 * %zh
 * 设置降雨天气的持续时间；传入 -1 时从 IntProvider 抽样。
 * @param i
 * %en duration in ticks, or -1 to sample
 * %zh 持续 tick 数，或 -1 表示抽样。
 */
fun setRain(level: ServerLevel, i: Int){
    requireServer().setWeatherParameters(
        getDuration(level, i, ServerLevel.RAIN_DURATION),
        0,
        false,
        false
    )
}



/**
 * %en
 * Set thunder weather for specified duration (or sample when -1).
 *
 * %zh
 * 设置雷暴天气的持续时间；传入 -1 时从 IntProvider 抽样。
 * @param i
 * %en duration in ticks, or -1 to sample
 * %zh 持续 tick 数，或 -1 表示抽样。
 */
fun setThunder(level: ServerLevel, i: Int){
    requireServer().setWeatherParameters(
        getDuration(level, i, ServerLevel.THUNDER_DURATION),
        0,
        false,
        false
    )
}



/**
 * %en
 * Set the world border safe-zone buffer.
 *
 * %zh
 * 设置世界边界安全缓冲区。
 * @param level
 * %en server level whose border to modify
 * %zh 要修改世界边界的服务端关卡。
 * @param distance
 * %en safe distance buffer
 * %zh 安全距离缓冲区。
 */
fun setWorldBorderDamageBuffer(level: ServerLevel, distance: Double){
    val border = level.worldBorder
    if(border.safeZone == distance) return
    border.safeZone = distance
}


/**
 * %en
 * Set the world border damage amount per block.
 *
 * %zh
 * 设置世界边界每格伤害值。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param damage
 * %en damage per block
 * %zh 每格伤害值。
 */
fun setWorldBorderDamageAmount(level: ServerLevel, damage: Double){
    val border = level.worldBorder
    if(border.damagePerBlock == damage) return
    border.damagePerBlock = damage
}


/**
 * %en
 * Set the world border warning time.
 *
 * %zh
 * 设置世界边界警告时间。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param time
 * %en warning time in seconds
 * %zh 警告时间，单位为秒。
 */
fun setWorldBorderWarningTime(level: ServerLevel, time: Int){
    val border = level.worldBorder
    if(border.warningTime == time) return
    border.warningTime = time
}


/**
 * %en
 * Set world border warning distance in blocks.
 *
 * %zh
 * 设置世界边界警告距离，单位为方块。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param distance
 * %en warning distance
 * %zh 警告距离。
 */
fun setWorldBorderWarningDistance(level: ServerLevel, distance: Int){
    val border = level.worldBorder
    if(border.warningBlocks == distance) return
    border.warningBlocks = distance
}


/**
 * %en
 * Get the current world border size.
 *
 * %zh
 * 获取当前世界边界大小。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @return
 * %en border size (double)
 * %zh 返回世界边界大小。
 */
fun getWorldBorderSize(level: ServerLevel): Double {
    return level.worldBorder.size
}


/**
 * %en
 * Set world border size, optionally over time.
 *
 * %zh
 * 设置世界边界大小，可选在一段时间内渐变。
 * @param level
 * %en server level
 * %zh 服务端关卡。
 * @param size
 * %en target border size
 * %zh 目标边界大小。
 * @param time
 * %en interpolation time in ticks (0 applies immediately)
 * %zh 插值到新大小的 tick 数，0 表示立即生效。
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
