package top.katton.paper

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/**
 * Bridge converts Paper (Bukkit) types to NMS types.
 * Uses Fabric Loom on compile classpath for NMS type resolution.
 */
object PaperNmsBridge {

    @JvmStatic fun toNmsServer(b: org.bukkit.Server): MinecraftServer = b.javaClass.getMethod("getServer").invoke(b) as MinecraftServer
    @JvmStatic fun toNmsPlayer(b: org.bukkit.entity.Player): ServerPlayer = b.javaClass.getMethod("getHandle").invoke(b) as ServerPlayer
    @JvmStatic fun toNmsWorld(b: org.bukkit.World): ServerLevel = b.javaClass.getMethod("getHandle").invoke(b) as ServerLevel
    @JvmStatic fun toNmsEntity(b: org.bukkit.entity.Entity): Entity = b.javaClass.getMethod("getHandle").invoke(b) as Entity
    @JvmStatic fun toNmsLivingEntity(b: org.bukkit.entity.LivingEntity): LivingEntity = b.javaClass.getMethod("getHandle").invoke(b) as LivingEntity
    @JvmStatic fun toNmsLevel(b: org.bukkit.World): ServerLevel = toNmsWorld(b)
    @JvmStatic fun toNmsBlockPos(x: Int, y: Int, z: Int): BlockPos = BlockPos(x, y, z)
    @JvmStatic fun toNmsBlockPos(loc: org.bukkit.Location): BlockPos = BlockPos(loc.blockX, loc.blockY, loc.blockZ)
    @JvmStatic fun toNmsVec3(x: Double, y: Double, z: Double): Vec3 = Vec3(x, y, z)
    @JvmStatic fun toNmsDirection(f: org.bukkit.block.BlockFace): Direction = Direction.valueOf(f.name)
    @JvmStatic fun toNmsInteractionHand(s: org.bukkit.inventory.EquipmentSlot): InteractionHand = when(s) { org.bukkit.inventory.EquipmentSlot.HAND -> InteractionHand.MAIN_HAND; org.bukkit.inventory.EquipmentSlot.OFF_HAND -> InteractionHand.OFF_HAND; else -> InteractionHand.MAIN_HAND }
    @JvmStatic fun toNmsComponent(text: String): Component = Component.literal(text)
    @JvmStatic fun toNmsExpOrb(b: org.bukkit.entity.ExperienceOrb): ExperienceOrb = b.javaClass.getMethod("getHandle").invoke(b) as ExperienceOrb
    @JvmStatic fun toNmsItemStack(b: org.bukkit.inventory.ItemStack): ItemStack? {
        val handle = try { b.javaClass.getMethod("getHandle").invoke(b) } catch (_: Exception) { null } ?: return null
        return handle as? ItemStack
    }
    @JvmStatic fun anyPlaceholder(): Any = Unit
}
