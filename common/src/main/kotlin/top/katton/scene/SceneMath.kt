package top.katton.scene

import kotlin.math.*
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import top.katton.api.scene.*

object SceneMath {
    private const val RAD = (PI / 180.0).toFloat()

    fun rotation(yaw: Float, pitch: Float, roll: Float): Quaternionf =
        Quaternionf().rotationYXZ(-yaw * RAD, -pitch * RAD, roll * RAD)

    fun catmullRom(a: Vec3, b: Vec3, c: Vec3, d: Vec3, t: Double): Vec3 {
        val t2 = t * t
        val t3 = t2 * t
        return b.scale(2.0)
            .add(c.subtract(a).scale(t))
            .add(a.scale(2.0).subtract(b.scale(5.0)).add(c.scale(4.0)).subtract(d).scale(t2))
            .add(a.scale(-1.0).add(b.scale(3.0)).subtract(c.scale(3.0)).add(d).scale(t3))
            .scale(0.5)
    }

    fun camera(path: CameraPath, tick: Double): CameraPose {
        val keys = path.keyframes
        if (tick <= 0) return keys.first().pose
        if (tick >= path.durationTicks) return keys.last().pose
        val i = keys.indexOfLast { it.tick <= tick }.coerceIn(0, keys.size - 2)
        val a = keys[i]
        val b = keys[i + 1]
        val t = b.easing.sample((tick - a.tick) / (b.tick - a.tick))
        val position =
            when (path.options.interpolation) {
                CameraInterpolation.LINEAR -> a.pose.position.lerp(b.pose.position, t)
                CameraInterpolation.CATMULL_ROM ->
                    catmullRom(
                        keys[maxOf(0, i - 1)].pose.position,
                        a.pose.position,
                        b.pose.position,
                        keys[minOf(keys.lastIndex, i + 2)].pose.position,
                        t,
                    )
            }
        val q =
            rotation(a.pose.yaw, a.pose.pitch, a.pose.roll)
                .slerp(rotation(b.pose.yaw, b.pose.pitch, b.pose.roll), t.toFloat())
        val angles = q.getEulerAnglesYXZ(Vector3f())
        val fov =
            if (a.pose.fov != null && b.pose.fov != null)
                (a.pose.fov + (b.pose.fov - a.pose.fov) * t).toFloat()
            else a.pose.fov ?: b.pose.fov
        return CameraPose(position, -angles.y / RAD, -angles.x / RAD, angles.z / RAD, fov)
    }

    fun lookAt(pose: CameraPose, target: Vec3): CameraPose {
        val d = target.subtract(pose.position)
        if (d.lengthSqr() < 1e-12) return pose
        return pose.copy(
            yaw = Math.toDegrees(atan2(-d.x, d.z)).toFloat(),
            pitch = -Math.toDegrees(atan2(d.y, hypot(d.x, d.z))).toFloat(),
        )
    }

    fun particlePoint(
        shape: ParticleShape,
        index: Int,
        count: Int,
        random: java.util.Random,
    ): Vec3 {
        val t = if (count <= 1) 0.0 else index.toDouble() / (count - 1)
        val angle = index.toDouble() / maxOf(1, count) * PI * 2
        return when (shape) {
            ParticleShape.Point -> Vec3.ZERO
            is ParticleShape.Line -> shape.end.scale(t)
            is ParticleShape.Ring -> Vec3(cos(angle) * shape.radius, 0.0, sin(angle) * shape.radius)
            is ParticleShape.Sphere -> {
                val y = random.nextDouble() * 2 - 1
                val azimuth = random.nextDouble() * PI * 2
                val r = sqrt(1 - y * y)
                Vec3(r * cos(azimuth), y, r * sin(azimuth)).scale(shape.radius)
            }
            is ParticleShape.Helix ->
                Vec3(
                    cos(t * shape.turns * PI * 2) * shape.radius,
                    t * shape.height,
                    sin(t * shape.turns * PI * 2) * shape.radius,
                )
        }
    }
}
