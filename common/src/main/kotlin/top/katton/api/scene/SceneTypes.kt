package top.katton.api.scene

import java.util.UUID
import kotlin.math.*
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3

/**
 * %en
 * Completion is local; cancellation does not change server gameplay.
 * %zh
 * 结束状态仅属于表现层，不修改服务端玩法。
 */
enum class EffectEndReason {
    COMPLETED,
    CANCELLED,
    REPLACED,
    TARGET_LOST,
    WORLD_CHANGED,
    RELOAD,
    ERROR,
    BUDGET,
    REJECTED,
}

enum class SceneEasing {
    LINEAR,
    SMOOTH,
    EASE_IN,
    EASE_OUT;

    fun sample(value: Double): Double {
        val t = value.coerceIn(0.0, 1.0)
        return when (this) {
            LINEAR -> t
            SMOOTH -> t * t * (3 - 2 * t)
            EASE_IN -> t * t
            EASE_OUT -> 1 - (1 - t) * (1 - t)
        }
    }
}

enum class CameraInterpolation {
    LINEAR,
    CATMULL_ROM,
}

enum class EffectBlend {
    ALPHA,
    ADDITIVE,
}

/**
 * %en
 * Coordinates are blocks; angles are degrees.
 * %zh
 * 坐标单位为方块，角度单位为度。
 */
data class CameraPose(
    val position: Vec3,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val fov: Float? = null,
) {
    init {
        require(position.finite() && yaw.isFinite() && pitch.isFinite() && roll.isFinite())
        require(fov == null || fov.isFinite() && fov in 1f..179f)
    }
}

data class CameraKeyframe(
    val tick: Int,
    val pose: CameraPose,
    val easing: SceneEasing = SceneEasing.SMOOTH,
) {
    init {
        require(tick >= 0)
    }
}

/**
 * %en
 * Input locking is optional; Esc always skips an active cutscene.
 * %zh
 * 可选择是否锁定操作；Esc 始终可以跳过正在接管镜头的过场。
 */
data class CameraOptions(
    val lockInput: Boolean = true,
    val interpolation: CameraInterpolation = CameraInterpolation.LINEAR,
    val lookAt: EffectAnchor? = null,
)

/**
 * %en
 * Immutable playback parameters, shared by every track.
 * %zh
 * 每条轨道共享的不可变播放参数。
 */
data class SceneContext(
    val origin: Vec3 = Vec3.ZERO,
    val target: UUID? = null,
    val seed: Long = 0L,
) {
    init {
        require(origin.finite())
    }
}

/**
 * %en
 * Entity anchors never load chunks.
 * %zh
 * 实体锚点不会加载区块。
 */
sealed interface EffectAnchor {
    data class Position(val position: Vec3) : EffectAnchor {
        init {
            require(position.finite())
        }
    }

    data class Origin(val offset: Vec3 = Vec3.ZERO) : EffectAnchor {
        init {
            require(offset.finite())
        }
    }

    data class Entity(val id: UUID? = null, val offset: Vec3 = Vec3.ZERO) : EffectAnchor {
        init {
            require(offset.finite())
        }
    }
}

/**
 * %en
 * Budgets are shared across all packs on this client.
 * %zh
 * 此客户端所有脚本包共享预算。
 */
data class EffectBudgets(
    val activeEffects: Int = 256,
    val particlesPerTick: Int = 4096,
    val verticesPerFrame: Int = 65536,
) {
    init {
        require(
            activeEffects in 1..4096 &&
                particlesPerTick in 0..65536 &&
                verticesPerFrame in 0..1048576
        )
    }
}

data class EffectMaterial(
    val color: Int = -1,
    val width: Double = 0.05,
    val texture: Identifier? = null,
    val throughWalls: Boolean = false,
    val blend: EffectBlend = EffectBlend.ALPHA,
    val fadeOutTicks: Int = 10,
) {
    init {
        require(width.isFinite() && width > 0 && width <= 1024)
        require(fadeOutTicks >= 0)
    }
}

sealed interface ParticleShape {
    data object Point : ParticleShape

    data class Line(val end: Vec3) : ParticleShape {
        init {
            require(end.finite())
        }
    }

    data class Ring(val radius: Double = 1.0) : ParticleShape {
        init {
            require(radius.validSize())
        }
    }

    data class Sphere(val radius: Double = 1.0) : ParticleShape {
        init {
            require(radius.validSize())
        }
    }

    data class Helix(val radius: Double = 1.0, val height: Double = 2.0, val turns: Double = 2.0) :
        ParticleShape {
        init {
            require(
                radius.validSize() &&
                    height.isFinite() &&
                    abs(height) <= 1024 &&
                    turns.isFinite() &&
                    abs(turns) <= 128
            )
        }
    }
}

/**
 * %en
 * Geometry uses local coordinates relative to its anchor.
 * %zh
 * 几何坐标相对于锚点。
 */
sealed interface EffectGeometry {
    data class Line(val end: Vec3) : EffectGeometry {
        init {
            require(end.finite())
        }
    }

    data class Curve(val points: List<Vec3>, val segments: Int = 48) : EffectGeometry {
        init {
            require(points.size in 2..128 && points.all { it.finite() })
            require(segments in 2..512)
        }
    }

    data class Ring(val radius: Double = 1.0, val segments: Int = 64) : EffectGeometry {
        init {
            require(radius.validSize() && segments in 3..512)
        }
    }

    data class Plane(val width: Double = 2.0, val depth: Double = 2.0) : EffectGeometry {
        init {
            require(width.validSize() && depth.validSize())
        }
    }

    /** A convex planar polygon, triangulated as a fan. / 共面凸多边形，使用扇形三角剖分。 */
    data class Face(val points: List<Vec3>) : EffectGeometry {
        init {
            require(points.size in 3..128 && points.all { it.finite() })
        }
    }

    data class Box(val size: Vec3 = Vec3(1.0, 1.0, 1.0), val wireframe: Boolean = false) :
        EffectGeometry {
        init {
            require(size.x.validSize() && size.y.validSize() && size.z.validSize())
        }
    }

    data class Sphere(val radius: Double = 1.0, val segments: Int = 24, val rings: Int = 12) :
        EffectGeometry {
        init {
            require(radius.validSize() && segments in 3..128 && rings in 2..64)
        }
    }

    data class Cylinder(
        val radius: Double = 1.0,
        val height: Double = 2.0,
        val segments: Int = 32,
        val topRadius: Double = radius,
    ) : EffectGeometry {
        init {
            require(
                radius.validSize() &&
                    height.validSize() &&
                    topRadius.isFinite() &&
                    topRadius in 0.0..1024.0 &&
                    segments in 3..128
            )
        }
    }
}

sealed interface SceneEffect {
    val durationTicks: Int
}

data class CameraPath(
    val keyframes: List<CameraKeyframe>,
    val options: CameraOptions = CameraOptions(),
) : SceneEffect {
    init {
        require(
            keyframes.size in 2..1024 &&
                keyframes.first().tick == 0 &&
                keyframes.zipWithNext().all { (a, b) -> a.tick < b.tick }
        )
        require(keyframes.last().tick <= MAX_SCENE_TICKS)
    }

    override val durationTicks
        get() = keyframes.last().tick
}

data class CameraFollow(
    val anchor: EffectAnchor,
    override val durationTicks: Int,
    val options: CameraOptions = CameraOptions(),
    val yaw: Float = 0f,
    val pitch: Float = 0f,
) : SceneEffect {
    init {
        durationTicks.requireDuration()
        require(yaw.isFinite() && pitch.isFinite())
    }
}

data class CameraShake(
    override val durationTicks: Int = 10,
    val strength: Float = 2f,
    val frequency: Float = 0.7f,
) : SceneEffect {
    init {
        durationTicks.requireDuration()
        require(
            strength.isFinite() &&
                strength in 0f..45f &&
                frequency.isFinite() &&
                frequency in 0f..10f
        )
    }
}

data class CameraFov(
    val from: Float,
    val to: Float,
    override val durationTicks: Int,
    val easing: SceneEasing = SceneEasing.SMOOTH,
) : SceneEffect {
    init {
        durationTicks.requireDuration()
        require(from.isFinite() && to.isFinite() && from in 1f..179f && to in 1f..179f)
    }
}

data class ParticleEmitter(
    val particle: ParticleOptions,
    val shape: ParticleShape = ParticleShape.Point,
    val anchor: EffectAnchor = EffectAnchor.Origin(),
    override val durationTicks: Int = 1,
    val countPerTick: Int = 16,
    val velocity: Vec3 = Vec3.ZERO,
) : SceneEffect {
    init {
        durationTicks.requireDuration()
        require(countPerTick in 0..65536 && velocity.finite())
    }
}

data class GeometryEffect(
    val geometry: EffectGeometry,
    val anchor: EffectAnchor = EffectAnchor.Origin(),
    override val durationTicks: Int = 20,
    val material: EffectMaterial = EffectMaterial(),
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
) : SceneEffect {
    init {
        durationTicks.requireDuration()
        require(yaw.isFinite() && pitch.isFinite() && roll.isFinite())
    }
}

data class BeamEffect(
    val from: EffectAnchor,
    val to: EffectAnchor,
    override val durationTicks: Int = 20,
    val material: EffectMaterial = EffectMaterial(),
) : SceneEffect {
    init {
        durationTicks.requireDuration()
    }
}

data class TrailEffect(
    val anchor: EffectAnchor = EffectAnchor.Entity(),
    override val durationTicks: Int = 100,
    val material: EffectMaterial = EffectMaterial(),
    val maxPoints: Int = 32,
    val teleportDistance: Double = 8.0,
) : SceneEffect {
    init {
        durationTicks.requireDuration()
        require(maxPoints in 2..1024 && teleportDistance.validSize())
    }
}

internal const val MAX_SCENE_TICKS = 72000

internal fun Int.requireDuration() {
    require(this in 1..MAX_SCENE_TICKS) { "Duration must be 1..$MAX_SCENE_TICKS ticks" }
}

internal fun Vec3.finite() =
    x.isFinite() && y.isFinite() && z.isFinite() && abs(x) <= 6e7 && abs(y) <= 6e7 && abs(z) <= 6e7

private fun Double.validSize() = isFinite() && this > 0 && this <= 1024
