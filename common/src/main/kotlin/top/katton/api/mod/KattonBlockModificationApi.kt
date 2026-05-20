@file:Suppress("unused")

package top.katton.api.mod

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.MapColor
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.id
import top.katton.util.ReflectUtil

/**
 * Configuration for modifying existing block properties.
 * 
 * This class provides a fluent API for modifying properties of existing
 * blocks registered in Minecraft's block registry. Similar to KubeJS's block
 * modification system.
 * 
 * @property blockId The identifier of the block to modify
 */
class BlockModificationConfig(
    val blockId: Identifier
) {
    var hardness: Float? = null
    var resistance: Float? = null
    var requiresCorrectTool: Boolean? = null
    var friction: Float? = null
    var speedFactor: Float? = null
    var jumpFactor: Float? = null
    var lightEmission: Int? = null
    var mapColor: MapColor? = null
    var canOcclude: Boolean? = null
    var isAir: Boolean? = null
    var hasCollision: Boolean? = null
    var isSuffocating: Boolean? = null
    var isViewBlocking: Boolean? = null
    var soundType: SoundType? = null

    fun hardness(value: Float) {
        hardness = value
    }

    fun resistance(value: Float) {
        resistance = value
    }

    fun strength(value: Float) {
        hardness = value
        resistance = value * 6.0f
    }

    fun requiresCorrectTool(value: Boolean = true) {
        requiresCorrectTool = value
    }

    fun friction(value: Float) {
        friction = value
    }

    fun speedFactor(value: Float) {
        speedFactor = value
    }

    fun jumpFactor(value: Float) {
        jumpFactor = value
    }

    fun lightEmission(value: Int) {
        lightEmission = value
    }

    fun lightLevel(value: Int) {
        lightEmission = value
    }

    fun mapColor(value: MapColor) {
        mapColor = value
    }

    fun canOcclude(value: Boolean) {
        canOcclude = value
    }

    fun isAir(value: Boolean) {
        isAir = value
    }

    fun hasCollision(value: Boolean) {
        hasCollision = value
    }

    fun isSuffocating(value: Boolean) {
        isSuffocating = value
    }

    fun isViewBlocking(value: Boolean) {
        isViewBlocking = value
    }

    fun soundType(value: SoundType) {
        soundType = value
    }
}

/**
 * Modifies an existing block's properties.
 * 
 * This function allows you to modify properties of blocks already registered
 * in Minecraft's block registry. Changes are applied to the block's default
 * state and will affect all instances of that block.
 * 
 * @param blockId The identifier of the block to modify (e.g., "minecraft:stone")
 * @param configure Configuration lambda for block modifications
 * @return The modified Block instance
 * 
 * @example
 * ```kotlin
 * modifyBlock("minecraft:stone") {
 *     hardness = 1.0f
 *     resistance = 10.0f
 *     lightEmission = 5
 *     friction = 0.8f
 * }
 * ```
 */
@ApiStatus.Experimental
fun modifyBlock(blockId: String, configure: BlockModificationConfig.() -> Unit): Block {
    return modifyBlock(id(blockId), configure)
}

/**
 * Modifies an existing block's properties.
 * 
 * @param blockId The identifier of the block to modify
 * @param configure Configuration lambda for block modifications
 * @return The modified Block instance
 */
@ApiStatus.Experimental
fun modifyBlock(blockId: Identifier, configure: BlockModificationConfig.() -> Unit): Block {
    val block = BuiltInRegistries.BLOCK.getOptional(blockId)
        .orElseThrow { IllegalArgumentException("Block not found: $blockId") }
    val config = BlockModificationConfig(blockId).apply(configure)
    
    applyBlockModifications(block, config)
    return block
}

private fun applyBlockModifications(block: Block, config: BlockModificationConfig) {
    val properties = block.properties()

    config.hardness?.let { hardness ->
        properties.destroyTime(hardness)
    }

    config.resistance?.let { resistance ->
        properties.explosionResistance(resistance)
        setBlockField(block, "explosionResistance", resistance)
    }

    config.requiresCorrectTool?.let { requiresTool ->
        setPropertyField(properties, "requiresCorrectToolForDrops", requiresTool)
    }

    config.friction?.let { friction ->
        properties.friction(friction)
        setBlockField(block, "friction", friction)
    }

    config.speedFactor?.let { speedFactor ->
        properties.speedFactor(speedFactor)
        setBlockField(block, "speedFactor", speedFactor)
    }

    config.jumpFactor?.let { jumpFactor ->
        properties.jumpFactor(jumpFactor)
        setBlockField(block, "jumpFactor", jumpFactor)
    }

    config.lightEmission?.let { lightEmission ->
        properties.lightLevel { lightEmission }
    }

    config.mapColor?.let { mapColor ->
        properties.mapColor(mapColor)
    }

    config.canOcclude?.let { canOcclude ->
        setPropertyField(properties, "canOcclude", canOcclude)
    }

    config.isAir?.let { isAir ->
        setPropertyField(properties, "isAir", isAir)
    }

    config.hasCollision?.let { hasCollision ->
        setPropertyField(properties, "hasCollision", hasCollision)
        setBlockField(block, "hasCollision", hasCollision)
    }

    config.isSuffocating?.let { isSuffocating ->
        properties.isSuffocating(statePredicate(isSuffocating))
    }

    config.isViewBlocking?.let { isViewBlocking ->
        properties.isViewBlocking(statePredicate(isViewBlocking))
    }

    config.soundType?.let { soundType ->
        properties.sound(soundType)
        setBlockField(block, "soundType", soundType)
    }

    applyBlockStateModifications(block, config)
}

private fun applyBlockStateModifications(block: Block, config: BlockModificationConfig) {
    block.stateDefinition.possibleStates.forEach { state ->
        config.hardness?.let { setStateField(state, "destroySpeed", it) }
        config.requiresCorrectTool?.let { setStateField(state, "requiresCorrectToolForDrops", it) }
        config.lightEmission?.let { setStateField(state, "lightEmission", it) }
        config.mapColor?.let { setStateField(state, "mapColor", it) }
        config.canOcclude?.let { setStateField(state, "canOcclude", it) }
        config.isAir?.let { setStateField(state, "isAir", it) }
        config.isSuffocating?.let { setStateField(state, "isSuffocating", statePredicate(it)) }
        config.isViewBlocking?.let { setStateField(state, "isViewBlocking", statePredicate(it)) }
        state.initCache()
    }
}

private fun setStateField(state: BlockState, fieldName: String, value: Any) {
    val result = ReflectUtil.setFinal(state, fieldName, value)
    if (result.isFailure) {
        throw IllegalStateException("Failed to set BlockStateBase.$fieldName")
    }
}

private fun setBlockField(block: Block, fieldName: String, value: Any) {
    val result = ReflectUtil.setFinal(block, fieldName, value)
    if (result.isFailure) {
        throw IllegalStateException("Failed to set BlockBehaviour.$fieldName")
    }
}

private fun setPropertyField(properties: BlockBehaviour.Properties, fieldName: String, value: Any) {
    val result = ReflectUtil.set(properties, fieldName, value)
    if (result.isFailure) {
        throw IllegalStateException("Failed to set BlockBehaviour.Properties.$fieldName")
    }
}

private fun statePredicate(value: Boolean): BlockBehaviour.StatePredicate {
    return BlockBehaviour.StatePredicate { _, _, _ -> value }
}

/**
 * Gets a block by its identifier.
 * 
 * @param blockId The block identifier
 * @return The Block instance, or null if not found
 */
fun getBlock(blockId: String): Block? {
    return getBlock(id(blockId))
}

/**
 * Gets a block by its identifier.
 * 
 * @param blockId The block identifier
 * @return The Block instance, or null if not found
 */
fun getBlock(blockId: Identifier): Block? {
    return BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null)
}

/**
 * Gets the default block state for a block.
 * 
 * @param blockId The block identifier
 * @return The default BlockState, or null if block not found
 */
fun getBlockState(blockId: String): BlockState? {
    return getBlockState(id(blockId))
}

/**
 * Gets the default block state for a block.
 * 
 * @param blockId The block identifier
 * @return The default BlockState, or null if block not found
 */
fun getBlockState(blockId: Identifier): BlockState? {
    val block = getBlock(blockId) ?: return null
    return block.defaultBlockState()
}
