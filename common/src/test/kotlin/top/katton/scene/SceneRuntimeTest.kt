package top.katton.scene

import java.util.UUID
import kotlin.test.*
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import top.katton.api.scene.*
import top.katton.engine.ScriptEnvironment
import top.katton.pack.ScriptPackScope

class SceneRuntimeTest {
    private class Backend : SceneBackend {
        val entities = mutableMapOf<UUID, Vec3>()
        var interpolationOffset = Vec3.ZERO
        var particles = 0
        val diagnostics = mutableListOf<String>()

        override fun resolve(
            anchor: EffectAnchor,
            context: SceneContext,
            partialTick: Float,
        ): Vec3? =
            when (anchor) {
                is EffectAnchor.Position -> anchor.position
                is EffectAnchor.Origin -> context.origin.add(anchor.offset)
                is EffectAnchor.Entity ->
                    entities[anchor.id ?: context.target]
                        ?.add(anchor.offset)
                        ?.add(interpolationOffset.scale(1.0 - partialTick))
            }

        override fun particle(type: ParticleOptions, position: Vec3, velocity: Vec3) {
            particles++
        }

        override fun diagnostic(message: String, failure: Throwable?) {
            diagnostics += message
        }
    }

    private fun path(lock: Boolean = true) =
        CameraPath(
            listOf(
                CameraKeyframe(0, CameraPose(Vec3.ZERO)),
                CameraKeyframe(10, CameraPose(Vec3(10.0, 0.0, 0.0))),
            ),
            CameraOptions(lockInput = lock),
        )

    @Test
    fun `sequential parallel and explicit offsets share one clock`() {
        val backend = Backend()
        val runtime = SceneRuntime(backend)
        val definition = clientScene {
            parallel {
                effect(CameraShake(5))
                sequential {
                    effect(CameraFov(70f, 80f, 2))
                    effect(CameraFov(80f, 90f, 3))
                }
            }
            effect(CameraShake(2))
            at(8) { effect(CameraShake(2)) }
        }
        assertEquals(listOf(0, 0, 2, 5, 8), definition.tracks.map { it.startTick })
        assertEquals(10, definition.durationTicks)
        val handle = runtime.play(definition)
        assertEquals(2, runtime.activeEffects)
        repeat(7) { runtime.tick() }
        assertTrue(handle.isActive)
        assertEquals(0, runtime.activeEffects)
        runtime.tick()
        assertEquals(1, runtime.activeEffects)
        repeat(2) { runtime.tick() }
        assertEquals(EffectEndReason.COMPLETED, handle.endReason)
    }

    @Test
    fun `pause resume finite repetition and callbacks`() {
        val runtime = SceneRuntime(Backend())
        val h =
            runtime.play(
                clientScene {
                    repeats = 2
                    effect(CameraShake(2))
                }
            )
        var completed = 0
        h.onEnd { completed++ }
        h.pause()
        repeat(20) { runtime.tick() }
        assertTrue(h.isActive)
        h.resume()
        repeat(3) { runtime.tick() }
        assertTrue(h.isActive)
        runtime.tick()
        assertEquals(1, completed)
        h.cancel()
        assertEquals(1, completed)
    }

    @Test
    fun `future explicit track does not advance parallel sequence cursor`() {
        val scene = clientScene {
            at(100) { effect(CameraShake(10)) }
            parallel { effect(CameraShake(5)) }
            effect(CameraShake(2))
        }
        assertEquals(listOf(0, 5, 100), scene.tracks.map { it.startTick })
        assertEquals(110, scene.durationTicks)
    }

    @Test
    fun `replacement cancels prior scene children and releases input`() {
        val runtime = SceneRuntime(Backend())
        val old =
            runtime.play(
                clientScene {
                    parallel {
                        effect(path())
                        effect(CameraShake(20))
                    }
                }
            )
        assertTrue(runtime.inputLocked)
        val fresh = runtime.play(clientScene { effect(path(false)) })
        assertEquals(EffectEndReason.REPLACED, old.endReason)
        assertFalse(runtime.inputLocked)
        assertEquals(1, runtime.activeEffects)
        assertTrue(runtime.skipCamera())
        assertEquals(EffectEndReason.CANCELLED, fresh.endReason)
        assertFalse(runtime.skipCamera())
    }

    @Test
    fun `entity loss ends whole scene without leaving camera or geometry`() {
        val runtime = SceneRuntime(Backend())
        val h =
            runtime.play(
                clientScene {
                    parallel {
                        effect(CameraFollow(EffectAnchor.Entity(), 10))
                        effect(GeometryEffect(EffectGeometry.Ring()))
                    }
                }
            )
        assertNull(runtime.frame(0.5f).camera)
        assertEquals(EffectEndReason.TARGET_LOST, h.endReason)
        assertEquals(0, runtime.activeEffects)
        assertFalse(runtime.inputLocked)
    }

    @Test
    fun `budgets reject overload and do not prevent completion`() {
        val runtime = SceneRuntime(Backend(), EffectBudgets(activeEffects = 1))
        val a = runtime.play(clientScene { effect(CameraShake(2)) })
        val b = runtime.play(clientScene { effect(CameraShake(2)) })
        assertFalse(b.isActive)
        assertTrue(a.isActive)
        repeat(2) { runtime.tick() }
        assertEquals(0, runtime.activeScenes)
    }

    @Test
    fun `overloaded new scene cannot replace an existing camera`() {
        val runtime = SceneRuntime(Backend(), EffectBudgets(activeEffects = 2))
        val camera = runtime.play(clientScene { effect(path()) })
        val rejected = runtime.play(clientScene {
            parallel {
                effect(path())
                effect(CameraShake(10))
            }
        })
        assertEquals(EffectEndReason.REJECTED, rejected.endReason)
        assertTrue(camera.isActive)
        assertTrue(runtime.inputLocked)
    }

    @Test
    fun `future budget rejection preserves timeline completion`() {
        val runtime = SceneRuntime(Backend(), EffectBudgets(activeEffects = 2))
        runtime.play(clientScene { effect(CameraShake(10)) })
        val timeline = runtime.play(clientScene {
            waitTicks(2)
            parallel {
                effect(CameraShake(2))
                effect(CameraFov(75f, 60f, 2))
            }
        })
        repeat(2) { runtime.tick() }
        assertTrue(timeline.isActive)
        assertEquals(2, runtime.activeEffects)
        repeat(2) { runtime.tick() }
        assertEquals(EffectEndReason.COMPLETED, timeline.endReason)
    }

    @Test
    fun `reentrant factories cannot exceed the global effect budget`() {
        val runtime = SceneRuntime(Backend(), EffectBudgets(activeEffects = 2))
        runtime.play(clientScene {
            effect(10) {
                runtime.play(clientScene {
                    parallel {
                        effect(CameraShake(10))
                        effect(CameraShake(10))
                    }
                })
                CameraShake(10)
            }
        })
        assertEquals(2, runtime.activeEffects)
        repeat(10) { runtime.tick() }
        assertEquals(0, runtime.activeScenes)
    }

    @Test
    fun `owner reload cleanup preserves unrelated definitions and instances`() {
        val runtime = SceneRuntime(Backend())
        val a =
            runtime.play(
                clientScene { effect(CameraShake(10)) },
                owner = SceneOwner("world:a", ScriptPackScope.WORLD, ScriptEnvironment.CLIENT),
            )
        val b =
            runtime.play(
                clientScene { effect(CameraShake(10)) },
                owner = SceneOwner("global:b", ScriptPackScope.GLOBAL, ScriptEnvironment.CLIENT),
            )
        runtime.clear(EffectEndReason.RELOAD) { it.scope == ScriptPackScope.WORLD }
        assertEquals(EffectEndReason.RELOAD, a.endReason)
        assertTrue(b.isActive)
    }

    @Test
    fun `factory failures and end callback exceptions release resources`() {
        val backend = Backend()
        val runtime = SceneRuntime(backend)
        val h = runtime.play(clientScene { effect(10) { error("factory failure") } })
        assertEquals(EffectEndReason.ERROR, h.endReason)
        h.onEnd { error("callback failure") }
        assertEquals(0, runtime.activeScenes)
        assertEquals(2, backend.diagnostics.size)
    }

    @Test
    fun `precompile fallback preserves old playback while accepted revisions are replaced`() {
        val runtime = SceneRuntime(Backend())
        val preserved =
            runtime.play(
                clientScene { effect(path()) },
                owner =
                    SceneOwner("world:old", ScriptPackScope.WORLD, ScriptEnvironment.CLIENT, "old"),
            )
        val replaced =
            runtime.play(
                clientScene { effect(CameraShake(20)) },
                owner =
                    SceneOwner(
                        "world:changed",
                        ScriptPackScope.WORLD,
                        ScriptEnvironment.CLIENT,
                        "changed",
                    ),
            )
        runtime.tick()
        val before = runtime.frame(0f).camera
        runtime.clear(EffectEndReason.RELOAD) { it.revision != "old" }
        assertTrue(preserved.isActive)
        assertEquals(before, runtime.frame(0f).camera)
        assertEquals(EffectEndReason.RELOAD, replaced.endReason)
        runtime.tick()
        assertTrue(runtime.inputLocked)
        runtime.clear(EffectEndReason.RELOAD)
        assertFalse(runtime.inputLocked)
    }

    @Test
    fun `reentrant cleanup callback cannot create a leaked scene`() {
        val runtime = SceneRuntime(Backend())
        val h = runtime.play(clientScene { effect(path()) })
        h.onEnd {
            runtime.clear()
            runtime.play(clientScene { effect(path()) })
        }
        runtime.clear()
        assertEquals(0, runtime.activeScenes)
        assertFalse(runtime.inputLocked)
    }

    @Test
    fun `one hundred play cancel cycles return to baseline`() {
        val runtime = SceneRuntime(Backend())
        repeat(100) {
            val h =
                runtime.play(
                    clientScene {
                        parallel {
                            effect(path())
                            effect(GeometryEffect(EffectGeometry.Sphere()))
                        }
                    }
                )
            runtime.frame(0.5f)
            runtime.tick()
            h.cancel()
        }
        assertEquals(0, runtime.activeScenes)
        assertEquals(0, runtime.activeEffects)
        assertTrue(runtime.frame(0f).geometry.isEmpty())
        assertFalse(runtime.inputLocked)
    }

    @Test
    fun `trail history is bounded and teleport breaks its segment`() {
        val backend = Backend()
        val id = UUID.randomUUID()
        backend.entities[id] = Vec3.ZERO
        val runtime = SceneRuntime(backend)
        runtime.play(clientScene { effect(TrailEffect(maxPoints = 4)) }, SceneContext(target = id))
        repeat(10) {
            backend.entities[id] = Vec3(it.toDouble(), 0.0, 0.0)
            runtime.tick()
        }
        assertEquals(4, runtime.frame(0f).geometry.single().trail.size)
        backend.interpolationOffset = Vec3(-1.0, 0.0, 0.0)
        val interpolated = runtime.frame(0.5f).geometry.single().trail
        assertEquals(4, interpolated.size)
        assertEquals(8.5, interpolated.last().x, 1e-6)
        backend.interpolationOffset = Vec3.ZERO
        backend.entities[id] = Vec3(100.0, 0.0, 0.0)
        runtime.tick()
        assertEquals(1, runtime.frame(0f).geometry.single().trail.size)
    }

    @Test
    fun `emission obeys shared tick budget and ends without further spawns`() {
        val particle =
            object : ParticleOptions {
                override fun getType(): ParticleType<*> =
                    error("Backend must not inspect the particle registry")
            }
        val backend = Backend()
        val runtime = SceneRuntime(backend, EffectBudgets(particlesPerTick = 5))
        runtime.play(
            clientScene {
                parallel {
                    effect(ParticleEmitter(particle, countPerTick = 4))
                    effect(ParticleEmitter(particle, countPerTick = 4))
                }
            }
        )
        runtime.tick()
        assertEquals(5, backend.particles)
        runtime.tick()
        assertEquals(5, backend.particles)
        assertEquals(0, runtime.activeEffects)
    }
}
