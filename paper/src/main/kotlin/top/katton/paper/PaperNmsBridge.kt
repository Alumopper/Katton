package top.katton.paper

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot as NmsEquipmentSlot
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.bukkit.block.BlockFace
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.block.impl.CraftLever
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftExperienceOrb
import org.bukkit.craftbukkit.entity.CraftLivingEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import top.katton.util.ReflectUtil
import top.katton.util.create

/**
 * Bridge converts Paper (Bukkit) types to NMS types.
 * Uses Fabric Loom on compile classpath for NMS type resolution.
 */
object PaperNmsBridge {

    @JvmStatic
    fun toNmsServer(b: org.bukkit.Server): MinecraftServer {
        return if(b is CraftServer){
            b.server
        }else {
            ReflectUtil.invoke(b, "getServer").getOrThrow() as MinecraftServer
        }
    }
    @JvmStatic
    fun toNmsPlayer(b: org.bukkit.entity.Player): ServerPlayer {
        return if (b is CraftPlayer){
            b.handle
        } else {
            ReflectUtil.invoke(b, "getHandle").getOrThrow() as ServerPlayer
        }
    }

    @JvmStatic
    fun toNmsWorld(b: org.bukkit.World): ServerLevel {
        return if (b is CraftWorld) {
            b.handle
        } else {
            ReflectUtil.invoke(b, "getHandle").getOrThrow() as ServerLevel
        }
    }

    @JvmStatic
    fun toNmsEntity(b: org.bukkit.entity.Entity): Entity {
        return if (b is CraftEntity) {
            b.handle
        }else {
            ReflectUtil.invoke(b, "getHandle").getOrThrow() as Entity
        }
    }

    @JvmStatic
    fun toNmsLivingEntity(b: org.bukkit.entity.LivingEntity): LivingEntity {
        return if (b is CraftLivingEntity) {
            b.handle as LivingEntity
        } else {
            ReflectUtil.invoke(b, "getHandle").getOrThrow() as LivingEntity
        }
    }

    @JvmStatic
    fun toNmsLevel(b: org.bukkit.World): ServerLevel = toNmsWorld(b)

    @JvmStatic
    fun toNmsBlockPos(x: Int, y: Int, z: Int): BlockPos = BlockPos(x, y, z)

    @JvmStatic
    fun toNmsBlockPos(loc: org.bukkit.Location): BlockPos = BlockPos(loc.blockX, loc.blockY, loc.blockZ)

    @JvmStatic
    fun toNmsVec3(x: Double, y: Double, z: Double): Vec3 = Vec3(x, y, z)

    @JvmStatic
    fun toNmsDirection(face: BlockFace): Direction = when (face) {
        BlockFace.DOWN -> Direction.DOWN
        BlockFace.UP -> Direction.UP
        BlockFace.NORTH -> Direction.NORTH
        BlockFace.SOUTH -> Direction.SOUTH
        BlockFace.WEST -> Direction.WEST
        BlockFace.EAST -> Direction.EAST
        else -> Direction.NORTH
    }

    @JvmStatic
    fun toNmsInteractionHand(slot: EquipmentSlot?): InteractionHand = when (slot) {
        EquipmentSlot.OFF_HAND -> InteractionHand.OFF_HAND
        else -> InteractionHand.MAIN_HAND
    }

    @JvmStatic
    fun toNmsEquipmentSlot(slot: EquipmentSlot): NmsEquipmentSlot = when (slot) {
        EquipmentSlot.HAND -> NmsEquipmentSlot.MAINHAND
        EquipmentSlot.OFF_HAND -> NmsEquipmentSlot.OFFHAND
        EquipmentSlot.FEET -> NmsEquipmentSlot.FEET
        EquipmentSlot.LEGS -> NmsEquipmentSlot.LEGS
        EquipmentSlot.CHEST -> NmsEquipmentSlot.CHEST
        EquipmentSlot.HEAD -> NmsEquipmentSlot.HEAD
        EquipmentSlot.BODY -> NmsEquipmentSlot.BODY
        else -> NmsEquipmentSlot.MAINHAND
    }

    @JvmStatic
    fun toNmsComponent(text: String): Component = Component.literal(text)

    @JvmStatic
    fun toNmsExpOrb(b: org.bukkit.entity.ExperienceOrb): ExperienceOrb {
        return (b as? CraftExperienceOrb)?.handle ?: ReflectUtil.invoke(b, "getHandle").getOrThrow() as ExperienceOrb
    }

    @JvmStatic
    fun toNmsItemStack(b: org.bukkit.inventory.ItemStack?): ItemStack? {
        if (b == null) {
            return null
        }
        return CraftItemStack.asNMSCopy(b)
    }

    @JvmStatic
    fun toBukkitItemStack(stack: ItemStack?): org.bukkit.inventory.ItemStack? {
        if (stack == null) {
            return null
        }
        return CraftItemStack.asBukkitCopy(stack)
    }

    @JvmStatic
    fun toNmsChunk(chunk: org.bukkit.Chunk): LevelChunk? {
        return if(chunk is CraftChunk){
            chunk.getHandle(ChunkStatus.FULL) as? LevelChunk
        }else {
            runCatching {
                ReflectUtil.invoke(chunk, "getHandle").getOrThrow() as? LevelChunk
            }.getOrNull()
        }
    }

    @JvmStatic
    fun toNmsBlockState(block: org.bukkit.block.Block): BlockState =
        toNmsWorld(block.world).getBlockState(toNmsBlockPos(block.location))

    @JvmStatic
    fun toNmsBlockEntity(block: org.bukkit.block.Block): BlockEntity? =
        toNmsWorld(block.world).getBlockEntity(toNmsBlockPos(block.location))

    @JvmStatic
    fun toBlockHitResult(event: PlayerInteractEvent): BlockHitResult? {
        val block = event.clickedBlock ?: return null
        val point = event.interactionPoint ?: block.location.clone().add(0.5, 0.5, 0.5)
        return BlockHitResult(
            toNmsVec3(point.x, point.y, point.z),
            toNmsDirection(event.blockFace),
            toNmsBlockPos(block.location),
            false
        )
    }

    @JvmStatic
    fun toEntityHitResult(entity: org.bukkit.entity.Entity): EntityHitResult = EntityHitResult(toNmsEntity(entity))

    @JvmStatic
    fun toNmsDamageSource(server: MinecraftServer, source: org.bukkit.damage.DamageSource): DamageSource {
        val key = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.parse(source.damageType.key.toString())
        )
        val registry = server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
        val holder = registry.get(key).orElse(null)
            ?: registry.get(DamageTypes.GENERIC).orElse(null)
            ?: error("Missing minecraft:generic damage type in registry")

        val direct = source.directEntity?.let(::toNmsEntity)
        val causing = source.causingEntity?.let(::toNmsEntity)
        val location = source.damageLocation ?: source.sourceLocation

        return when {
            direct != null || causing != null -> DamageSource(holder, direct ?: causing, causing ?: direct)
            location != null -> DamageSource(holder, toNmsVec3(location.x, location.y, location.z))
            else -> DamageSource(holder)
        }
    }

    @JvmStatic
    fun getResourceManager(server: MinecraftServer): ResourceManager {
        return server.resources.resourceManager()
    }

}
