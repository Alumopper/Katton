@file:Suppress("unused", "CAST_NEVER_SUCCEEDS")

package top.katton.paper

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer

private val plugin by lazy { KattonPaperPlugin.getInstance() }

// ── Bukkit entity helper ──────────────────────────────────────────

/** Get the Bukkit Entity from an NMS Entity (safe cast for Paper). */
private fun Entity.toBukkit(): org.bukkit.entity.Entity? = this as? CraftEntity

/** Get the Bukkit Player from an NMS ServerPlayer. */
fun ServerPlayer.toBukkit(): org.bukkit.entity.Player? = this as? CraftPlayer

/** Get the Bukkit World from an NMS ServerLevel. */
private fun ServerLevel.toBukkitWorld(): org.bukkit.World? {
    return Bukkit.getWorlds().firstOrNull { (it as? CraftWorld)?.handle === this }
}

// ── Entity region scheduling ─────────────────────────────────────

/**
 * Schedule [action] to run on the entity's region thread.
 * On non-Folia Paper, runs on the main server thread.
 */
fun <T: Entity> T.schedule(action: T.() -> Unit) {
    val p = plugin ?: run { action(); return }
    val bukkit = toBukkit() ?: run { action(); return }
    bukkit.scheduler.run(p, { _ -> action() }, null)
}

/**
 * Schedule [action] to run on the entity's region thread after [delayTicks].
 */
fun <T: Entity> T.schedule(delayTicks: Long, action: T.() -> Unit) {
    val p = plugin ?: run { action(); return }
    val bukkit = toBukkit() ?: run { action(); return }
    bukkit.scheduler.runDelayed(p, { _ -> action() }, null, delayTicks)
}

/**
 * Schedule [action] to run repeatedly on the entity's region thread.
 * Returns the task for cancellation via [cancelScheduledTask].
 */
fun <T: Entity> T.scheduleRepeating(delayTicks: Long, periodTicks: Long, action: T.() -> Unit): Any? {
    val p = plugin ?: return null
    val bukkit = toBukkit() ?: return null
    return bukkit.scheduler.runAtFixedRate(p, { _ -> action() }, null, delayTicks, periodTicks)
}

// ── Position-based region scheduling ──────────────────────────────

/**
 * Schedule [action] to run on the region thread for the given position.
 */
fun scheduleAt(world: ServerLevel, pos: BlockPos, action: () -> Unit) {
    val p = plugin ?: run { action(); return }
    val bukkitWorld = world.toBukkitWorld() ?: run { action(); return }
    val location = Location(bukkitWorld, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    Bukkit.getRegionScheduler().run(p, location, { _ -> action() })
}

/**
 * Schedule [action] to run on the region thread for the given position after [delayTicks].
 */
fun scheduleAt(world: ServerLevel, pos: BlockPos, delayTicks: Long, action: () -> Unit) {
    val p = plugin ?: run { action(); return }
    val bukkitWorld = world.toBukkitWorld() ?: run { action(); return }
    val location = Location(bukkitWorld, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    Bukkit.getRegionScheduler().runDelayed(p, location, { _ -> action() }, delayTicks)
}

// ── Global region scheduling ──────────────────────────────────────

/**
 * Schedule [action] to run on the global region thread.
 */
fun scheduleGlobal(action: () -> Unit) {
    val p = plugin ?: run { action(); return }
    Bukkit.getGlobalRegionScheduler().run(p, { _ -> action() })
}

/**
 * Schedule [action] to run on the global region thread after [delayTicks].
 */
fun scheduleGlobal(delayTicks: Long, action: () -> Unit) {
    val p = plugin ?: run { action(); return }
    Bukkit.getGlobalRegionScheduler().runDelayed(p, { _ -> action() }, delayTicks)
}

/**
 * Schedule [action] to run repeatedly on the global region thread.
 */
fun scheduleGlobalRepeating(delayTicks: Long, periodTicks: Long, action: () -> Unit): Any? {
    val p = plugin ?: return null
    return Bukkit.getGlobalRegionScheduler().runAtFixedRate(p, { _ -> action() }, delayTicks, periodTicks)
}

// ── Utility ───────────────────────────────────────────────────────

/**
 * Cancel a scheduled task returned by the repeating variants.
 */
fun cancelScheduledTask(task: Any?) {
    if (task is ScheduledTask) {
        task.cancel()
    }
}
