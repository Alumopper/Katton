package top.katton.scene

import kotlin.math.*
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import top.katton.api.scene.*

data class SceneVertex(val position: Vec3, val u: Float, val v: Float, val color: Int)

data class SceneMesh(val material: EffectMaterial, val vertices: List<SceneVertex>)

/** Builds bounded, immutable triangle batches. There is no GPU allocation in this class. */
object SceneGeometry {
    fun build(samples: List<SampledGeometry>, camera: Vec3, maxVertices: Int): List<SceneMesh> {
        var remaining = maxVertices
        val batches = linkedMapOf<EffectMaterial, MutableList<SceneVertex>>()
        for (sample in samples) {
            if (remaining < 3) break
            val material =
                when (val e = sample.effect) {
                    is GeometryEffect -> e.material
                    is BeamEffect -> e.material
                    is TrailEffect -> e.material
                    else -> continue
                }
            val alpha =
                if (material.fadeOutTicks == 0) 1.0
                else
                    ((sample.effect.durationTicks - sample.age) / material.fadeOutTicks).coerceIn(
                        0.0,
                        1.0,
                    )
            val color =
                (material.color and 0xFFFFFF) or
                    ((((material.color ushr 24) * alpha).toInt()) shl 24)
            // Color, width, and fade are baked into vertices, not material state.
            val key = material.copy(color = -1, width = 1.0, fadeOutTicks = 0)
            val vertices = batches.getOrPut(key) { mutableListOf() }
            val builder = Builder(vertices, remaining, color, camera, material.width)
            when (val e = sample.effect) {
                is BeamEffect -> sample.end?.let { builder.ribbon(sample.origin, it) }
                is TrailEffect ->
                    sample.trail.zipWithNext().forEach { (a, b) -> builder.ribbon(a, b) }
                is GeometryEffect -> {
                    val q = SceneMath.rotation(e.yaw, e.pitch, e.roll)
                    builder.transform = { point ->
                        val p =
                            q.transform(
                                Vector3f(point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                            )
                        sample.origin.add(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())
                    }
                    builder.geometry(e.geometry)
                }
                else -> Unit
            }
            remaining -= builder.used
        }
        return batches
            .filterValues { it.isNotEmpty() }
            .map { (material, vertices) ->
                val sorted =
                    if (material.blend == EffectBlend.ALPHA) {
                        vertices
                            .chunked(3)
                            .sortedByDescending { triangle ->
                                triangle[0]
                                    .position
                                    .add(triangle[1].position)
                                    .add(triangle[2].position)
                                    .scale(1.0 / 3.0)
                                    .distanceToSqr(camera)
                            }
                            .flatten()
                    } else vertices.toList()
                SceneMesh(material, sorted)
            }
    }

    private class Builder(
        val output: MutableList<SceneVertex>,
        val budget: Int,
        val color: Int,
        val camera: Vec3,
        val width: Double,
    ) {
        var used = 0
        var transform: (Vec3) -> Vec3 = { it }

        fun triangle(
            a: Vec3,
            b: Vec3,
            c: Vec3,
            au: Float = 0f,
            av: Float = 0f,
            bu: Float = 1f,
            bv: Float = 0f,
            cu: Float = 1f,
            cv: Float = 1f,
        ) {
            if (used + 3 > budget) return
            output += SceneVertex(transform(a), au, av, color)
            output += SceneVertex(transform(b), bu, bv, color)
            output += SceneVertex(transform(c), cu, cv, color)
            used += 3
        }

        fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3) {
            triangle(a, b, c)
            triangle(a, c, d, 0f, 0f, 1f, 1f, 0f, 1f)
        }

        fun ribbon(a: Vec3, b: Vec3) {
            val worldA = transform(a)
            val worldB = transform(b)
            val direction = worldB.subtract(worldA)
            if (direction.lengthSqr() < 1e-12) return
            var side = direction.cross(camera.subtract(worldA))
            if (side.lengthSqr() < 1e-12) side = direction.cross(Vec3(0.0, 1.0, 0.0))
            if (side.lengthSqr() < 1e-12) side = direction.cross(Vec3(1.0, 0.0, 0.0))
            side = side.normalize().scale(width * 0.5)
            val saved = transform
            transform = { it }
            quad(worldA.subtract(side), worldB.subtract(side), worldB.add(side), worldA.add(side))
            transform = saved
        }

        fun geometry(g: EffectGeometry) {
            when (g) {
                is EffectGeometry.Line -> ribbon(Vec3.ZERO, g.end)
                is EffectGeometry.Curve -> {
                    fun point(t: Double): Vec3 {
                        val segment =
                            (t * (g.points.size - 1)).toInt().coerceAtMost(g.points.size - 2)
                        return SceneMath.catmullRom(
                            g.points[maxOf(0, segment - 1)],
                            g.points[segment],
                            g.points[segment + 1],
                            g.points[minOf(g.points.lastIndex, segment + 2)],
                            t * (g.points.size - 1) - segment,
                        )
                    }
                    for (i in 0 until g.segments) ribbon(
                        point(i.toDouble() / g.segments),
                        point((i + 1.0) / g.segments),
                    )
                }
                is EffectGeometry.Ring ->
                    for (i in 0 until g.segments) {
                        val a = i * PI * 2 / g.segments
                        val b = (i + 1) * PI * 2 / g.segments
                        val inner = maxOf(0.0, g.radius - width / 2)
                        val outer = g.radius + width / 2
                        quad(circle(a, inner), circle(b, inner), circle(b, outer), circle(a, outer))
                    }
                is EffectGeometry.Plane ->
                    quad(
                        Vec3(-g.width / 2, 0.0, -g.depth / 2),
                        Vec3(g.width / 2, 0.0, -g.depth / 2),
                        Vec3(g.width / 2, 0.0, g.depth / 2),
                        Vec3(-g.width / 2, 0.0, g.depth / 2),
                    )
                is EffectGeometry.Face ->
                    for (i in 1 until g.points.lastIndex) triangle(
                        g.points[0],
                        g.points[i],
                        g.points[i + 1],
                    )
                is EffectGeometry.Box -> {
                    val s = g.size.scale(0.5)
                    val p =
                        listOf(
                            Vec3(-s.x, -s.y, -s.z),
                            Vec3(s.x, -s.y, -s.z),
                            Vec3(s.x, -s.y, s.z),
                            Vec3(-s.x, -s.y, s.z),
                            Vec3(-s.x, s.y, -s.z),
                            Vec3(s.x, s.y, -s.z),
                            Vec3(s.x, s.y, s.z),
                            Vec3(-s.x, s.y, s.z),
                        )
                    if (g.wireframe) {
                        for (i in 0..3) {
                            ribbon(p[i], p[(i + 1) % 4])
                            ribbon(p[i + 4], p[(i + 1) % 4 + 4])
                            ribbon(p[i], p[i + 4])
                        }
                    } else {
                        quad(p[0], p[1], p[2], p[3])
                        quad(p[4], p[7], p[6], p[5])
                        for (i in 0..3) quad(p[i], p[i + 4], p[(i + 1) % 4 + 4], p[(i + 1) % 4])
                    }
                }
                is EffectGeometry.Sphere -> {
                    fun point(i: Int, j: Int): Vec3 {
                        val latitude = i * PI / g.rings
                        val longitude = j * PI * 2 / g.segments
                        return Vec3(
                                sin(latitude) * cos(longitude),
                                cos(latitude),
                                sin(latitude) * sin(longitude),
                            )
                            .scale(g.radius)
                    }
                    for (i in 0 until g.rings) for (j in 0 until g.segments) quad(
                        point(i, j),
                        point(i + 1, j),
                        point(i + 1, j + 1),
                        point(i, j + 1),
                    )
                }
                is EffectGeometry.Cylinder ->
                    for (i in 0 until g.segments) {
                        val a = i * PI * 2 / g.segments
                        val b = (i + 1) * PI * 2 / g.segments
                        val bottomA = circle(a, g.radius)
                        val bottomB = circle(b, g.radius)
                        val topA = circle(a, g.topRadius).add(0.0, g.height, 0.0)
                        val topB = circle(b, g.topRadius).add(0.0, g.height, 0.0)
                        quad(bottomA, bottomB, topB, topA)
                        triangle(Vec3.ZERO, bottomB, bottomA)
                        if (g.topRadius > 0) triangle(Vec3(0.0, g.height, 0.0), topA, topB)
                    }
            }
        }

        private fun circle(angle: Double, radius: Double) =
            Vec3(cos(angle) * radius, 0.0, sin(angle) * radius)
    }
}
