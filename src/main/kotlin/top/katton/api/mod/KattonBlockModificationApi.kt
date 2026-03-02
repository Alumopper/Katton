@file:Suppress("unused")

package top.katton.api.mod

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.MapColor
import org.jetbrains.annotations.ApiStatus
import top.katton.mixin.BlockBehaviourPropertiesAccessor
import top.katton.mixin.BlockAccessor
import top.katton.registry.id

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
    val blockAccessor = block as BlockAccessor
    val properties = blockAccessor.`katton$getProperties`()
    val propsAccessor = properties as BlockBehaviourPropertiesAccessor
    
    config.hardness?.let { hardness ->
        propsAccessor.`katton$setDestroyTime`(hardness)
    }
    
    config.resistance?.let { resistance ->
        propsAccessor.`katton$setExplosionResistance`(resistance)
    }
    
    config.requiresCorrectTool?.let { requiresTool ->
        propsAccessor.`katton$setRequiresCorrectToolForDrops`(requiresTool)
    }
    
    config.friction?.let { friction ->
        propsAccessor.`katton$setFriction`(friction)
    }
    
    config.speedFactor?.let { speedFactor ->
        propsAccessor.`katton$setSpeedFactor`(speedFactor)
    }
    
    config.jumpFactor?.let { jumpFactor ->
        propsAccessor.`katton$setJumpFactor`(jumpFactor)
    }
    
    config.lightEmission?.let { lightEmission ->
        propsAccessor.`katton$setLightEmission`(lightEmission)
    }
    
    config.mapColor?.let { mapColor ->
        propsAccessor.`katton$setMapColor`(mapColor)
    }
    
    config.canOcclude?.let { canOcclude ->
        propsAccessor.`katton$setCanOcclude`(canOcclude)
    }
    
    config.isAir?.let { isAir ->
        propsAccessor.`katton$setIsAir`(isAir)
    }
    
    config.hasCollision?.let { hasCollision ->
        propsAccessor.`katton$setHasCollision`(hasCollision)
    }
    
    config.isSuffocating?.let { isSuffocating ->
        propsAccessor.`katton$setIsSuffocating`(isSuffocating)
    }
    
    config.isViewBlocking?.let { isViewBlocking ->
        propsAccessor.`katton$setIsViewBlocking`(isViewBlocking)
    }
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
