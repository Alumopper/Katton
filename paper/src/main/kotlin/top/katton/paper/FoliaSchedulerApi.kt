@file:Suppress("unused")

package top.katton.paper

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.craftbukkit.CraftWorld
import top.katton.util.ReflectUtil

private val plugin by lazy { KattonPaperPlugin.getInstance() }

// ── Bukkit entity helper ──────────────────────────────────────────

/** Get the Bukkit Entity from an NMS Entity (safe cast for Paper). */
private fun Entity.toBukkit(): org.bukkit.entity.Entity? {
    return ReflectUtil.invoke(this, "getBukkitEntity").getOrNull() as? org.bukkit.entity.Entity
}

/** Get the Bukkit Player from an NMS ServerPlayer. */
fun ServerPlayer.toBukkit(): org.bukkit.entity.Player? = (this as Entity).toBukkit() as? org.bukkit.entity.Player

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
    managedSchedule(false, { action() }) { callback -> bukkit.scheduler.run(p, callback, null) }
}

/**
 * Schedule [action] to run on the entity's region thread after [delayTicks].
 */
fun <T: Entity> T.schedule(delayTicks: Long, action: T.() -> Unit) {
    val p = plugin ?: run { action(); return }
    val bukkit = toBukkit() ?: run { action(); return }
    managedSchedule(false, { action() }) { callback -> bukkit.scheduler.runDelayed(p, callback, null, delayTicks.coerceAtLeast(1)) }
}

/**
 * Schedule [action] to run repeatedly on the entity's region thread.
 * Returns the task for cancellation via [cancelScheduledTask].
 */
fun <T: Entity> T.scheduleRepeating(delayTicks: Long, periodTicks: Long, action: T.() -> Unit): Any? {
    val p = plugin ?: return null
    val bukkit = toBukkit() ?: return null
    return managedSchedule(true, { action() }) { callback -> bukkit.scheduler.runAtFixedRate(p, callback, null, delayTicks.coerceAtLeast(1), periodTicks) }
}

// ── Position-based region scheduling ──────────────────────────────

/**
 * Schedule [action] to run on the region thread for the given position.
 */
fun scheduleAt(world: ServerLevel, pos: BlockPos, action: () -> Unit) {
    val p = plugin ?: run { action(); return }
    val bukkitWorld = world.toBukkitWorld() ?: run { action(); return }
    val location = Location(bukkitWorld, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    managedSchedule(false, action) { callback -> Bukkit.getRegionScheduler().run(p, location, callback) }
}

/**
 * Schedule [action] to run on the region thread for the given position after [delayTicks].
 */
fun scheduleAt(world: ServerLevel, pos: BlockPos, delayTicks: Long, action: () -> Unit) {
    val p = plugin ?: run { action(); return }
    val bukkitWorld = world.toBukkitWorld() ?: run { action(); return }
    val location = Location(bukkitWorld, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    managedSchedule(false, action) { callback -> Bukkit.getRegionScheduler().runDelayed(p, location, callback, delayTicks.coerceAtLeast(1)) }
}

// ── Global region scheduling ──────────────────────────────────────

/**
 * Schedule [action] to run on the global region thread.
 */
fun scheduleGlobal(action: () -> Unit) {
    val p = plugin ?: run { action(); return }
    managedSchedule(false, action) { callback -> Bukkit.getGlobalRegionScheduler().run(p, callback) }
}

/**
 * Schedule [action] to run on the global region thread after [delayTicks].
 */
fun scheduleGlobal(delayTicks: Long, action: () -> Unit) {
    val p = plugin ?: run { action(); return }
    managedSchedule(false, action) { callback -> Bukkit.getGlobalRegionScheduler().runDelayed(p, callback, delayTicks.coerceAtLeast(1)) }
}

/**
 * Schedule [action] to run repeatedly on the global region thread.
 */
fun scheduleGlobalRepeating(delayTicks: Long, periodTicks: Long, action: () -> Unit): Any? {
    val p = plugin ?: return null
    return managedSchedule(true, action) { callback -> Bukkit.getGlobalRegionScheduler().runAtFixedRate(p, callback, delayTicks.coerceAtLeast(1), periodTicks) }
}

// ── Utility ───────────────────────────────────────────────────────

/**
 * Cancel a scheduled task returned by the repeating variants.
 */
fun cancelScheduledTask(task: Any?) {
    if (task is ManagedScheduledTask) task.cancel()
    if (task is ScheduledTask) {
        task.cancel()
    }
}

/** Retains the platform task during a tentative detach so rollback does not duplicate repeating tasks. */
private class ManagedScheduledTask(
    private val repeating: Boolean,
    private val action: () -> Unit,
    private val schedule: (java.util.function.Consumer<ScheduledTask>) -> ScheduledTask?
) {
    private val context = top.katton.scene.SceneOwner.capture()
    @Volatile private var active = true
    @Volatile private var cancelled = false
    @Volatile private var missed = false
    private var task: ScheduledTask? = null
    private val callback = java.util.function.Consumer<ScheduledTask> {
        synchronized(this) {
            if (!cancelled) {
                if (!active || top.katton.engine.ManagedResources.isPaused(context.owner)) {
                    missed = !repeating
                } else if (!top.katton.engine.ManagedResources.runCallback { context.invoke(action) }) {
                    missed = !repeating
                }
            }
        }
    }
    fun start(): ManagedScheduledTask {
        task = schedule(callback)
        top.katton.engine.ManagedResources.record(
            attach = { active = true },
            detach = { active = false },
            dispose = { cancel() },
            resumed = { synchronized(this) { if (missed && !cancelled) { missed = false; task = schedule(callback) } } }
        )
        return this
    }
    @Synchronized fun cancel() { cancelled = true; task?.cancel(); task = null }
}

private fun managedSchedule(repeating: Boolean, action: () -> Unit,
    schedule: (java.util.function.Consumer<ScheduledTask>) -> ScheduledTask?): Any =
    ManagedScheduledTask(repeating, action, schedule).start()
