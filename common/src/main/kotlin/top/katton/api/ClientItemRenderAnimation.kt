@file:Suppress("unused")

package top.katton.api

import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class ClientItemRenderAnimationTarget {
    TRANSLATE,
    ROTATE,
    SCALE
}

enum class ClientItemRenderAnimationMode {
    ABSOLUTE,
    RELATIVE
}

enum class ClientItemRenderEasing(private val sampler: (Float) -> Float) {
    LINEAR({ it }),
    EASE({ cubicBezier(0.25f, 0.1f, 0.25f, 1.0f, it) }),
    EASE_IN({ cubicBezier(0.42f, 0.0f, 1.0f, 1.0f, it) }),
    EASE_OUT({ cubicBezier(0.0f, 0.0f, 0.58f, 1.0f, it) }),
    EASE_IN_OUT({ cubicBezier(0.42f, 0.0f, 0.58f, 1.0f, it) }),
    STEP_START({ if (it <= 0.0f) 0.0f else 1.0f }),
    STEP_END({ if (it < 1.0f) 0.0f else 1.0f }),
    EASE_IN_SINE({ (1.0 - cos((it * PI) / 2.0)).toFloat() }),
    EASE_OUT_SINE({ sin((it * PI) / 2.0).toFloat() }),
    EASE_IN_OUT_SINE({ (-(cos(PI * it) - 1.0) / 2.0).toFloat() }),
    EASE_IN_QUAD({ it * it }),
    EASE_OUT_QUAD({ 1.0f - (1.0f - it) * (1.0f - it) }),
    EASE_IN_OUT_QUAD({ if (it < 0.5f) 2.0f * it * it else 1.0f - ((-2.0f * it + 2.0f).pow(2.0f) / 2.0f) }),
    EASE_IN_CUBIC({ it * it * it }),
    EASE_OUT_CUBIC({ 1.0f - (1.0f - it).pow(3.0f) }),
    EASE_IN_OUT_CUBIC({ if (it < 0.5f) 4.0f * it * it * it else 1.0f - ((-2.0f * it + 2.0f).pow(3.0f) / 2.0f) }),
    EASE_IN_QUART({ it * it * it * it }),
    EASE_OUT_QUART({ 1.0f - (1.0f - it).pow(4.0f) }),
    EASE_IN_OUT_QUART({ if (it < 0.5f) 8.0f * it * it * it * it else 1.0f - ((-2.0f * it + 2.0f).pow(4.0f) / 2.0f) }),
    EASE_IN_QUINT({ it * it * it * it * it }),
    EASE_OUT_QUINT({ 1.0f - (1.0f - it).pow(5.0f) }),
    EASE_IN_OUT_QUINT({ if (it < 0.5f) 16.0f * it * it * it * it * it else 1.0f - ((-2.0f * it + 2.0f).pow(5.0f) / 2.0f) }),
    EASE_IN_EXPO({ if (it == 0.0f) 0.0f else 2.0f.pow(10.0f * it - 10.0f) }),
    EASE_OUT_EXPO({ if (it == 1.0f) 1.0f else 1.0f - 2.0f.pow(-10.0f * it) }),
    EASE_IN_OUT_EXPO({
        when {
            it == 0.0f -> 0.0f
            it == 1.0f -> 1.0f
            it < 0.5f -> 2.0f.pow(20.0f * it - 10.0f) / 2.0f
            else -> (2.0f - 2.0f.pow(-20.0f * it + 10.0f)) / 2.0f
        }
    }),
    EASE_IN_CIRC({ (1.0 - sqrt(1.0 - (it * it))).toFloat() }),
    EASE_OUT_CIRC({ sqrt(1.0 - (it - 1.0f).pow(2.0f)).toFloat() }),
    EASE_IN_OUT_CIRC({
        if (it < 0.5f) {
            ((1.0 - sqrt(1.0 - (2.0f * it).pow(2.0f))) / 2.0).toFloat()
        } else {
            ((sqrt(1.0 - (-2.0f * it + 2.0f).pow(2.0f)) + 1.0) / 2.0).toFloat()
        }
    }),
    EASE_IN_BACK(::easeInBack),
    EASE_OUT_BACK(::easeOutBack),
    EASE_IN_OUT_BACK(::easeInOutBack),
    EASE_IN_ELASTIC(::easeInElastic),
    EASE_OUT_ELASTIC(::easeOutElastic),
    EASE_IN_OUT_ELASTIC(::easeInOutElastic),
    EASE_IN_BOUNCE({ 1.0f - easeOutBounce(1.0f - it) }),
    EASE_OUT_BOUNCE(::easeOutBounce),
    EASE_IN_OUT_BOUNCE({
        if (it < 0.5f) {
            (1.0f - easeOutBounce(1.0f - 2.0f * it)) / 2.0f
        } else {
            (1.0f + easeOutBounce(2.0f * it - 1.0f)) / 2.0f
        }
    });

    /**
     * Clamp only the input progress. The sampled value is intentionally raw so
     * overshooting curves such as back, elastic, or bounce keep their shape.
     */
    fun apply(progress: Float): Float = sampler(progress.coerceIn(0.0f, 1.0f))
}

private fun easeInBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1.0f
    return c3 * t * t * t - c1 * t * t
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1.0f
    val p = t - 1.0f
    return 1.0f + c3 * p * p * p + c1 * p * p
}

private fun easeInOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c2 = c1 * 1.525f
    return if (t < 0.5f) {
        ((2.0f * t).pow(2.0f) * ((c2 + 1.0f) * 2.0f * t - c2)) / 2.0f
    } else {
        (((2.0f * t - 2.0f).pow(2.0f) * ((c2 + 1.0f) * (2.0f * t - 2.0f) + c2) + 2.0f)) / 2.0f
    }
}

private fun easeInElastic(t: Float): Float {
    val c4 = (2.0 * PI) / 3.0
    return when (t) {
        0.0f -> 0.0f
        1.0f -> 1.0f
        else -> (-(2.0.pow(10.0 * t - 10.0)) * sin((t * 10.0 - 10.75) * c4)).toFloat()
    }
}

private fun easeOutElastic(t: Float): Float {
    val c4 = (2.0 * PI) / 3.0
    return when (t) {
        0.0f -> 0.0f
        1.0f -> 1.0f
        else -> (2.0.pow(-10.0 * t) * sin((t * 10.0 - 0.75) * c4) + 1.0).toFloat()
    }
}

private fun easeInOutElastic(t: Float): Float {
    val c5 = (2.0 * PI) / 4.5
    return when {
        t == 0.0f -> 0.0f
        t == 1.0f -> 1.0f
        t < 0.5f -> (-(2.0.pow(20.0 * t - 10.0) * sin((20.0 * t - 11.125) * c5)) / 2.0).toFloat()
        else -> ((2.0.pow(-20.0 * t + 10.0) * sin((20.0 * t - 11.125) * c5)) / 2.0 + 1.0).toFloat()
    }
}

private fun easeOutBounce(t: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        t < 1.0f / d1 -> n1 * t * t
        t < 2.0f / d1 -> {
            val p = t - 1.5f / d1
            n1 * p * p + 0.75f
        }
        t < 2.5f / d1 -> {
            val p = t - 2.25f / d1
            n1 * p * p + 0.9375f
        }
        else -> {
            val p = t - 2.625f / d1
            n1 * p * p + 0.984375f
        }
    }
}

private fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, progress: Float): Float {
    var low = 0.0f
    var high = 1.0f
    var u = progress
    repeat(16) {
        u = (low + high) * 0.5f
        val x = cubic(u, x1, x2)
        if (x < progress) {
            low = u
        } else {
            high = u
        }
    }
    return cubic(u, y1, y2)
}

private fun cubic(t: Float, p1: Float, p2: Float): Float {
    val inv = 1.0f - t
    return 3.0f * inv * inv * t * p1 + 3.0f * inv * t * t * p2 + t * t * t
}

interface ClientItemRenderKeyframe {
    val time: Float
}

/**
 * A transform keyframe on an animation track. [time] is usually 0.0 to 1.0.
 *
 * Translation values are block offsets, rotation values are degrees, and scale
 * values are multipliers.
 */
data class ClientItemRenderAnimationKeyframe(
    override val time: Float,
    val value: Vec3,
    val easing: ClientItemRenderEasing = ClientItemRenderEasing.LINEAR
) : ClientItemRenderKeyframe

/**
 * A client-local function keyframe. Function callbacks are not serialized over
 * the network; server-spawned markers can only transmit transform keyframes.
 */
data class ClientItemRenderFunctionKeyframe(
    override val time: Float,
    val function: (ClientItemRenderMarker) -> Unit
) : ClientItemRenderKeyframe

/**
 * One animation track inside a [ClientItemRenderAnimationSet].
 */
data class ClientItemRenderAnimation(
    val target: ClientItemRenderAnimationTarget,
    val mode: ClientItemRenderAnimationMode = ClientItemRenderAnimationMode.ABSOLUTE,
    val keyframes: List<ClientItemRenderKeyframe>
)

/**
 * A playable animation set. All tracks in this set share the same duration,
 * delay, and loop behavior. Multiple sets can be played together and stacked.
 */
data class ClientItemRenderAnimationSet(
    val animations: List<ClientItemRenderAnimation>,
    val durationTicks: Int,
    val delayTicks: Int = 0,
    val loop: Boolean = true,
    val onStart: ((ClientItemRenderMarker) -> Unit)? = null,
    val onEnd: ((ClientItemRenderMarker) -> Unit)? = null
)

class ClientItemRenderAnimationSetBuilder {
    private val animations = arrayListOf<ClientItemRenderAnimation>()
    var durationTicks: Int = 20
    var delayTicks: Int = 0
    var loop: Boolean = true
    var onStart: ((ClientItemRenderMarker) -> Unit)? = null
    var onEnd: ((ClientItemRenderMarker) -> Unit)? = null

    fun build(): ClientItemRenderAnimationSet = ClientItemRenderAnimationSet(
        animations = animations.toList(),
        durationTicks = durationTicks,
        delayTicks = delayTicks,
        loop = loop,
        onStart = onStart,
        onEnd = onEnd
    )

    fun addAnimation(animationBuilder: (ClientItemRenderAnimationBuilder) -> Unit) {
        val builder = ClientItemRenderAnimationBuilder()
        animationBuilder(builder)
        animations.add(builder.build())
    }

    fun animation(
        target: ClientItemRenderAnimationTarget,
        mode: ClientItemRenderAnimationMode = ClientItemRenderAnimationMode.ABSOLUTE,
        animationBuilder: ClientItemRenderAnimationBuilder.() -> Unit
    ) {
        val builder = ClientItemRenderAnimationBuilder()
        builder.target = target
        builder.mode = mode
        builder.animationBuilder()
        animations.add(builder.build())
    }

    fun translate(
        mode: ClientItemRenderAnimationMode = ClientItemRenderAnimationMode.ABSOLUTE,
        animationBuilder: ClientItemRenderAnimationBuilder.() -> Unit
    ) {
        animation(ClientItemRenderAnimationTarget.TRANSLATE, mode, animationBuilder)
    }

    fun rotate(
        mode: ClientItemRenderAnimationMode = ClientItemRenderAnimationMode.RELATIVE,
        animationBuilder: ClientItemRenderAnimationBuilder.() -> Unit
    ) {
        animation(ClientItemRenderAnimationTarget.ROTATE, mode, animationBuilder)
    }

    fun scale(
        mode: ClientItemRenderAnimationMode = ClientItemRenderAnimationMode.ABSOLUTE,
        animationBuilder: ClientItemRenderAnimationBuilder.() -> Unit
    ) {
        animation(ClientItemRenderAnimationTarget.SCALE, mode, animationBuilder)
    }
}

class ClientItemRenderAnimationBuilder {
    private val keyframes = arrayListOf<ClientItemRenderKeyframe>()
    var target: ClientItemRenderAnimationTarget = ClientItemRenderAnimationTarget.TRANSLATE
    var mode: ClientItemRenderAnimationMode = ClientItemRenderAnimationMode.ABSOLUTE

    fun build(): ClientItemRenderAnimation = ClientItemRenderAnimation(
        target = target,
        mode = mode,
        keyframes = keyframes.toList()
    )

    fun keyframe(
        time: Float,
        x: Double,
        y: Double,
        z: Double,
        easing: ClientItemRenderEasing = ClientItemRenderEasing.LINEAR
    ) {
        keyframes.add(ClientItemRenderAnimationKeyframe(time, Vec3(x, y, z), easing))
    }

    fun functionKeyframe(time: Float, function: (ClientItemRenderMarker) -> Unit) {
        keyframes.add(ClientItemRenderFunctionKeyframe(time, function))
    }
}
