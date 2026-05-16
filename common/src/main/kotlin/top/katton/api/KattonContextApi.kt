package top.katton.api

import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun Vec3.copy(): Vec3{
    return Vec3(this.x, this.y, this.z)
}

fun Vec2.copy(): Vec2 {
    return Vec2(this.x, this.y)
}

@Suppress("unused")
class ExecutionContext(
    var pos: Vec3 = Vec3.ZERO,
    var server: MinecraftServer = requireServer(),
    var level: ServerLevel = server.overworld(),
    var entity: Entity? = null,
    var anchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.EYES,
    var rotation: Vec2 = Vec2.ZERO
){
    fun source(): CommandSourceStack {
        val source = server.createCommandSourceStack()
            .withLevel(level)
            .withAnchor(anchor)
            .withRotation(rotation)
        entity?.let { return source.withEntity(it) }
        return source
    }

    fun clone(): ExecutionContext{
        return ExecutionContext(pos.copy(), server, level, entity, anchor, rotation.copy())
    }

    inline fun with(run: ExecutionContext.() -> Unit){
        run(this)
    }

    inline fun fork(run: ExecutionContext.() -> Unit) {
        run(this.clone())
    }

    fun walk(forward: Double = 0.0,
             right: Double = 0.0,
             back: Double = 0.0,
             left: Double = 0.0,
             up: Double = 0.0,
             down: Double = 0.0
    ): ExecutionContext{
        val delta = Vec3(forward - back, up - down, right - left)
        // rotate
        val l = delta.length()
        val y = l * sin(rotation.y / 180.0 * PI)
        val qwq = l * cos(rotation.y / 180 * PI)
        val x = - qwq * sin(rotation.x / 180 * PI)
        val z = qwq * cos(rotation.x / 180 * PI)
        val relativeDelta = Vec3(x, y, z)
        this.pos = this.pos.add(relativeDelta)
        return this
    }

    fun move(forward: Double = 0.0,
             right: Double = 0.0,
             back: Double = 0.0,
             left: Double = 0.0,
             up: Double = 0.0,
             down: Double = 0.0
    ): ExecutionContext{
        val delta = Vec3(forward - back, up - down, right - left)
        this.pos = this.pos.add(delta)
        return this
    }

    fun rotate(xRot: Float, yRot: Float): ExecutionContext{
        this.rotation = this.rotation.add(Vec2(xRot, yRot))
        return this
    }

    val blockPos get() = BlockPos(pos.x.toInt(), pos.y.toInt(), pos.z.toInt())
    val blockState get() = level.getBlockState(blockPos)
    val blockEntity get() = level.getBlockEntity(blockPos)
    val biome get() = level.getBiome(blockPos)
    val fluidState get() = level.getFluidState(blockPos)

    inline fun ifBlock(b: Block, block: () -> Unit): ConditionResult{
        val s = blockState
        if(s.`is`(b)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessBlock(b: Block, block: () -> Unit): ConditionResult{
        val s = blockState
        if(!s.`is`(b)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }


    inline fun ifBlock(b: TagKey<Block>, block: () -> Unit): ConditionResult{
        val s = blockState
        if(s.`is`(b)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessBlock(b: TagKey<Block>, block: () -> Unit): ConditionResult{
        val s = blockState
        if(!s.`is`(b)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun <T : Comparable<T>> ifBlock(b: Property<T>, value: T, block: () -> Unit): ConditionResult{
        val s = blockState
        if(s.hasProperty(b) && s.getValue(b) == value){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun <T : Comparable<T>> unlessBlock(b: Property<T>, value: T, block: () -> Unit): ConditionResult{
        val s = blockState
        if(s.hasProperty(b) && s.getValue(b) != value){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifBlock(condition: (BlockState) -> Boolean, block: () -> Unit): ConditionResult{
        val s = blockState
        if(condition(s)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessBlock(condition: (BlockState) -> Boolean, block: () -> Unit): ConditionResult{
        val s = blockState
        if(!condition(s)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun <reified T: BlockEntity> ifBlockEntity(condition: (T) -> Boolean, block: () -> Unit): ConditionResult {
        val s = blockEntity
        if(s != null && s is T && condition(s)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun <reified T: BlockEntity> unlessBlockEntity(condition: (T) -> Boolean, block: () -> Unit): ConditionResult {
        val s = blockEntity
        if(s == null || s !is T || !condition(s)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifBlockEntity(block: (BlockEntity) -> Unit): ConditionResult {
        val s = blockEntity
        if(s != null){
            block(s)
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }


    inline fun unlessBlockEntity(block: () -> Unit): ConditionResult {
        if(blockEntity == null){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifBiome(b: ResourceKey<Biome>, block: () -> Unit): ConditionResult{
        val s = biome
        if(s.`is`(b)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessBiome(b: ResourceKey<Biome>, block: () -> Unit): ConditionResult{
        val s = biome
        if(!s.`is`(b)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifBiome(b: TagKey<Biome>, block: () -> Unit): ConditionResult{
        val s = biome
        if(s.`is`(b)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessBiome(b: TagKey<Biome>, block: () -> Unit): ConditionResult{
        val s = biome
        if(!s.`is`(b)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifBiome(condition: (Holder<Biome>) -> Boolean, block: () -> Unit): ConditionResult{
        val s = biome
        if(condition(s)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessBiome(condition: (Holder<Biome>) -> Boolean, block: () -> Unit): ConditionResult{
        val s = biome
        if(!condition(s)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifFluid(f: Fluid, block: () -> Unit): ConditionResult{
        val s = fluidState
        if(s.`is`(f)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessFluid(f: Fluid, block: () -> Unit): ConditionResult{
        val s = fluidState
        if(!s.`is`(f)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifFluid(f: TagKey<Fluid>, block: () -> Unit): ConditionResult{
        val s = fluidState
        if(s.`is`(f)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessFluid(f: TagKey<Fluid>, block: () -> Unit): ConditionResult{
        val s = fluidState
        if(!s.`is`(f)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifFluid(condition: (FluidState) -> Boolean, block: () -> Unit): ConditionResult{
        val s = fluidState
        if(condition(s)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun unlessFluid(condition: (FluidState) -> Boolean, block: () -> Unit): ConditionResult{
        val s = fluidState
        if(!condition(s)){
            block()
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    inline fun ifEntity(selector: EntitySelector, block: (List<Entity>) -> Unit): ConditionResult {
        val entities = selector.findEntities(source())
        if(entities.isNotEmpty()){
            block(entities)
            return ConditionResult.PASS
        }
        return ConditionResult.FAILED
    }

    companion object {

        enum class ConditionResult(val pass: Boolean){
            PASS(true),
            FAILED(false);

            inline fun elseRun(block:() -> Unit){
                if(!pass) block()
            }
        }

        fun from(source: CommandSourceStack): ExecutionContext {
            return ExecutionContext(source.position, source.server, source.level, source.entity, source.anchor, source.rotation)
        }

        fun overworld(server: MinecraftServer): ExecutionContext {
            return ExecutionContext(Vec3.ZERO, server, server.overworld(), null, EntityAnchorArgument.Anchor.FEET, Vec2.ZERO)
        }

        fun fromEntity(entity: Entity): ExecutionContext {
            return ExecutionContext(entity.position(), entity.level().server!!, entity.level() as ServerLevel, entity, EntityAnchorArgument.Anchor.FEET, entity.rotationVector)
        }
    }
}
/**
 * Determines which position the returned [ExecutionContext] uses.
 */
enum class BonePositionMode {
    /** Bone world-space position (entity position + bone offset, rotated by yaw). */
    BONE,
    /** Entity world-space position (ignoring the bone). */
    ENTITY
}

/**
 * Determines which facing direction the returned [ExecutionContext] uses.
 */
enum class BoneOrientationMode {
    /** Bone's local rotation + entity rotation (approximate world-space facing). */
    BONE,
    /** Entity's natural yaw/pitch. */
    ENTITY,
    /** Default — [Vec2.ZERO] (no rotation). */
    WORLD
}

/**
 * Computes the world-space position of a [ModelPart] bone on the given [entity].
 *
 * The bone offset (pixels, 1/16 block) is rotated by the entity's yaw and added
 * to the entity's interpolated position.
 *
 * Call AFTER animations have been applied ([net.minecraft.client.animation.KeyframeAnimation.apply]),
 * so that [ModelPart.x]/[ModelPart.y]/[ModelPart.z] reflect the animated pose.
 */
fun Entity.computeBoneWorldPos(bone: ModelPart, partialTick: Float = 1.0f): Vec3 {
    // ModelPart coords are in pixels (1/16 block).
    val localX = bone.x / 16.0
    val localY = bone.y / 16.0
    val localZ = bone.z / 16.0

    // MC yaw: 0=south(+Z), 90=west(-X), 180=north(-Z), -90=east(+X).
    // Render PoseStack applies rotation of (180 - yRot)° around Y,
    // equivalent to (yRot - 180)° for mapping model-local → world.
    val radians = Math.toRadians((yRot - 180.0))
    val cosA = cos(radians)
    val sinA = sin(radians)

    val worldX = localX * cosA - localZ * sinA
    val worldZ = localX * sinA + localZ * cosA

    val entityPos = getPosition(partialTick)
    return Vec3(
        entityPos.x + worldX,
        entityPos.y + localY,
        entityPos.z + worldZ
    )
}

/**
 * Creates an [ExecutionContext] positioned at a [ModelPart] bone on an [entity].
 *
 * Combines [BonePositionMode] and [BoneOrientationMode] to set the context's
 * `pos` and `rotation`. Use the returned [ExecutionContext] to execute commands
 * at the bone's world position, or read `pos` / `rotation` directly for custom effects.
 *
 * On a dedicated client (multiplayer), the server reference may be unavailable —
 * in that case the returned context has position/rotation set but no command
 * execution capability.
 *
 * @param modelPart       direct [ModelPart] reference (e.g. `model.rightArm`, `model.head`)
 * @param positionMode    [BonePositionMode.BONE] or [BonePositionMode.ENTITY]
 * @param orientationMode [BoneOrientationMode.BONE], [BoneOrientationMode.ENTITY], or [BoneOrientationMode.WORLD]
 *
 * @example
 * ```kotlin
 * val ctx = createBoneExecution(
 *     modelPart = model.rightArm,
 *     entity = entity,
 *     positionMode = BonePositionMode.BONE,
 *     orientationMode = BoneOrientationMode.BONE
 * )
 * // Execute command at bone position
 * ctx.source().let { stack ->
 *     ctx.server?.commands?.performCommand(stack, "particle minecraft:flame ~ ~ ~ 0 0 0 0.1 5")
 * }
 * ```
 */
fun Entity.createBoneExecution(
    modelPart: ModelPart,
    positionMode: BonePositionMode = BonePositionMode.BONE,
    orientationMode: BoneOrientationMode = BoneOrientationMode.WORLD
): ExecutionContext {
    val pos = when (positionMode) {
        BonePositionMode.BONE -> computeBoneWorldPos(modelPart)
        BonePositionMode.ENTITY -> position()
    }
    val rot = when (orientationMode) {
        BoneOrientationMode.BONE -> Vec2(
            yRot + Math.toDegrees(modelPart.yRot.toDouble()).toFloat(),
            xRot + Math.toDegrees(modelPart.xRot.toDouble()).toFloat()
        )
        BoneOrientationMode.ENTITY -> rotationVector
        BoneOrientationMode.WORLD -> Vec2.ZERO
    }
    val server = Minecraft.getInstance().singleplayerServer
        ?: return ExecutionContext(pos = pos, rotation = rot)
    return ExecutionContext(
        pos = pos,
        server = server,
        level = server.overworld(),
        entity = this,
        rotation = rot
    )
}
