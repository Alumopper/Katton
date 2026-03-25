package top.katton.api

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
