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
import org.bukkit.block.BlockFace
import org.bukkit.damage.DamageType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Bridge converts Paper (Bukkit) types to NMS types.
 * Uses Fabric Loom on compile classpath for NMS type resolution.
 */
object PaperNmsBridge {

    @JvmStatic
    fun toNmsServer(b: org.bukkit.Server): MinecraftServer = b.javaClass.getMethod("getServer").invoke(b) as MinecraftServer

    @JvmStatic
    fun toNmsPlayer(b: org.bukkit.entity.Player): ServerPlayer = b.javaClass.getMethod("getHandle").invoke(b) as ServerPlayer

    @JvmStatic
    fun toNmsWorld(b: org.bukkit.World): ServerLevel = b.javaClass.getMethod("getHandle").invoke(b) as ServerLevel

    @JvmStatic
    fun toNmsEntity(b: org.bukkit.entity.Entity): Entity = b.javaClass.getMethod("getHandle").invoke(b) as Entity

    @JvmStatic
    fun toNmsLivingEntity(b: org.bukkit.entity.LivingEntity): LivingEntity = b.javaClass.getMethod("getHandle").invoke(b) as LivingEntity

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
    fun toNmsExpOrb(b: org.bukkit.entity.ExperienceOrb): ExperienceOrb = b.javaClass.getMethod("getHandle").invoke(b) as ExperienceOrb

    @JvmStatic
    fun toNmsItemStack(b: org.bukkit.inventory.ItemStack?): ItemStack? {
        if (b == null) {
            return null
        }

        return runCatching {
            val craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
            craftItemStack.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack::class.java)
                .invoke(null, b) as ItemStack
        }.getOrNull()
    }

    @JvmStatic
    fun toBukkitItemStack(stack: ItemStack?): org.bukkit.inventory.ItemStack? {
        if (stack == null) {
            return null
        }

        return runCatching {
            val craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
            craftItemStack.getMethod("asBukkitCopy", ItemStack::class.java)
                .invoke(null, stack) as org.bukkit.inventory.ItemStack
        }.getOrNull()
    }

    @JvmStatic
    fun toNmsChunk(chunk: org.bukkit.Chunk): LevelChunk? = runCatching {
        chunk.javaClass.getMethod("getHandle").invoke(chunk) as? LevelChunk
    }.getOrNull()

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
    fun findResourceManager(server: MinecraftServer): ResourceManager? {
        val resources = findField(server.javaClass, "resources")?.let { field ->
            field.isAccessible = true
            field.get(server)
        } ?: return null

        return resources.javaClass.methods
            .firstOrNull { it.name == "resourceManager" && it.parameterCount == 0 }
            ?.invoke(resources) as? ResourceManager
    }

    @JvmStatic
    fun anyPlaceholder(): Any = Unit

    private fun findField(type: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredField(name) }.getOrNull()?.let { return it }
            current = current.superclass
        }
        return null
    }
}
