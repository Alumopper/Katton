package top.katton.scene

import kotlin.test.*
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.junit.jupiter.api.Test
import top.katton.api.scene.*

class SceneMathTest {
    @Test
    fun `camera clamps endpoints and takes shortest rotation path`() {
        val path =
            CameraPath(
                listOf(
                    CameraKeyframe(0, CameraPose(Vec3.ZERO, 179f)),
                    CameraKeyframe(10, CameraPose(Vec3(10.0, 0.0, 0.0), -179f)),
                )
            )
        assertEquals(path.keyframes.first().pose, SceneMath.camera(path, -1.0))
        assertEquals(path.keyframes.last().pose, SceneMath.camera(path, 20.0))
        val midpoint = SceneMath.camera(path, 5.0)
        assertEquals(5.0, midpoint.position.x, 1e-6)
        val forward =
            SceneMath.rotation(midpoint.yaw, midpoint.pitch, midpoint.roll)
                .transform(Vector3f(0f, 0f, 1f))
        assertTrue(forward.z < -0.999f)
    }

    @Test
    fun `catmull rom hits internal endpoints`() {
        val a = Vec3(0.0, 1.0, 0.0)
        val b = Vec3(1.0, 3.0, 2.0)
        val c = Vec3(2.0, 2.0, 4.0)
        val d = Vec3(3.0, 4.0, 6.0)
        assertEquals(b, SceneMath.catmullRom(a, b, c, d, 0.0))
        assertEquals(c, SceneMath.catmullRom(a, b, c, d, 1.0))
    }

    @Test
    fun `all geometry is bounded and triangulated`() {
        val geometries =
            listOf(
                EffectGeometry.Line(Vec3(1.0, 0.0, 0.0)),
                EffectGeometry.Curve(listOf(Vec3.ZERO, Vec3(1.0, 1.0, 1.0))),
                EffectGeometry.Ring(),
                EffectGeometry.Plane(),
                EffectGeometry.Face(listOf(Vec3.ZERO, Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0))),
                EffectGeometry.Box(),
                EffectGeometry.Box(wireframe = true),
                EffectGeometry.Sphere(),
                EffectGeometry.Cylinder(),
                EffectGeometry.Cylinder(topRadius = 0.0),
            )
        geometries.forEach { g ->
            val mesh =
                SceneGeometry.build(
                    listOf(SampledGeometry(GeometryEffect(g), Vec3.ZERO, null, emptyList(), 0.0)),
                    Vec3(0.0, 5.0, 5.0),
                    99,
                )
            val vertices = mesh.flatMap { it.vertices }
            assertTrue(vertices.isNotEmpty(), g.toString())
            assertTrue(vertices.size <= 99)
            assertEquals(0, vertices.size % 3)
            assertTrue(
                vertices.all {
                    it.position.x.isFinite() && it.position.y.isFinite() && it.position.z.isFinite()
                }
            )
        }
    }

    @Test
    fun `particle shapes remain finite and respect their radius`() {
        val random = java.util.Random(42)
        repeat(100) { i ->
            assertEquals(
                2.0,
                SceneMath.particlePoint(ParticleShape.Sphere(2.0), i, 100, random).length(),
                1e-8,
            )
            assertEquals(
                3.0,
                SceneMath.particlePoint(ParticleShape.Ring(3.0), i, 100, random).length(),
                1e-8,
            )
        }
    }

    @Test
    fun `invalid authoring inputs fail immediately`() {
        assertFailsWith<IllegalArgumentException> { CameraPose(Vec3(Double.NaN, 0.0, 0.0)) }
        assertFailsWith<IllegalArgumentException> { CameraFov(0f, 90f, 10) }
        assertFailsWith<IllegalArgumentException> {
            clientScene {
                repeats = 1000
                waitTicks(1000)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            EffectGeometry.Sphere(segments = Int.MAX_VALUE)
        }
    }
}
