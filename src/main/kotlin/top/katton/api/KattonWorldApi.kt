@file:Suppress("unused")

package top.katton.api

import com.mojang.datafixers.util.Pair
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.*
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.commands.*
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
import top.katton.util.Result
import kotlin.jvm.optionals.getOrDefault
import kotlin.math.sqrt


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
 * Reset a named random sequence for a level to its default seed.
 *
 * @param level server level
 * @param randomSequence sequence identifier
 */
fun resetSequence(level: ServerLevel, randomSequence: Identifier){
    requireServer().randomSequences.reset(randomSequence, level.seed)
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
    requireServer().randomSequences.reset(randomSequence, level.seed, seed, includeWorldSeed, includeSequenceID)
}


/**
 * Clear all random sequences for a level.
 *
 * @param level server level
 */
fun resetAllSequences(level: ServerLevel){
    requireServer().randomSequences.clear()
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
    requireServer().randomSequences.setSeedDefaults(seed, includeWorldSeed, includeSequenceID)
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
    return requireServer().clockManager().skipToTimeMarker(clock, timeMarker)
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
 * Resolve duration: if i == -1 use IntProvider sampled value; otherwise return i.
 *
 * @param i provided duration (-1 means sample)
 * @param intProvider provider to sample from
 * @return resolved duration
 */
fun getDuration(level: ServerLevel, i: Int, intProvider: IntProvider): Int {
    return if (i == -1) intProvider.sample(requireServer().overworld().getRandom()) else i
}


/**
 * Set clear weather for specified duration (or sample when -1).
 *
 * @param i duration ticks or -1 to sample
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
 * Set rain weather for specified duration (or sample when -1).
 *
 * @param i duration ticks or -1 to sample
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
 * Set thunder weather for specified duration (or sample when -1).
 *
 * @param i duration ticks or -1 to sample
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


