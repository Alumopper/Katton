import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import top.katton.api.*
import top.katton.api.scene.*
import top.katton.registry.registerCommand
import top.katton.util.ScriptExecutionContext

@ClientScriptEntrypoint(ClientPhase.REGISTRY_SETUP)
fun registerSceneExamples() {
    val blue =
        EffectMaterial(color = 0xCC55CCFF.toInt(), width = 0.08, blend = EffectBlend.ADDITIVE)
    registerClientScene("example:impact") {
        parallel {
            effect(CameraShake(12, 2.5f))
            effect(
                ParticleEmitter(
                    ParticleTypes.END_ROD,
                    ParticleShape.Ring(2.5),
                    durationTicks = 4,
                    countPerTick = 48,
                    velocity = Vec3(0.0, 0.08, 0.0),
                )
            )
            effect(GeometryEffect(EffectGeometry.Ring(2.5), durationTicks = 20, material = blue))
        }
    }
    registerClientScene("example:trail") {
        parallel {
            effect(TrailEffect(durationTicks = 200, material = blue, maxPoints = 32))
            effect(
                BeamEffect(
                    EffectAnchor.Origin(Vec3(0.0, 3.0, 0.0)),
                    EffectAnchor.Entity(offset = Vec3(0.0, 1.0, 0.0)),
                    durationTicks = 200,
                    material = blue,
                )
            )
        }
    }
    registerClientScene("example:cutscene") {
        parallel {
            effect(80) { context ->
                CameraPath(
                    listOf(
                        CameraKeyframe(0, CameraPose(context.origin.add(0.0, 2.0, 5.0), fov = 75f)),
                        CameraKeyframe(
                            40,
                            CameraPose(context.origin.add(5.0, 3.0, 0.0), fov = 60f),
                        ),
                        CameraKeyframe(
                            80,
                            CameraPose(context.origin.add(0.0, 5.0, -5.0), fov = 75f),
                        ),
                    ),
                    CameraOptions(
                        lockInput = true,
                        interpolation = CameraInterpolation.CATMULL_ROM,
                        lookAt = EffectAnchor.Origin(Vec3(0.0, 1.0, 0.0)),
                    ),
                )
            }
            effect(
                GeometryEffect(
                    EffectGeometry.Plane(6.0, 6.0),
                    EffectAnchor.Origin(Vec3(0.0, 0.05, 0.0)),
                    80,
                    blue.copy(
                        texture =
                            Identifier.fromNamespaceAndPath("katton", "textures/effect/circle.png"),
                        fadeOutTicks = 20,
                    ),
                )
            )
            effect(
                ParticleEmitter(
                    ParticleTypes.END_ROD,
                    ParticleShape.Helix(2.0, 3.0, 2.0),
                    durationTicks = 80,
                    countPerTick = 4,
                )
            )
        }
    }
    registerClientScene("example:geometry") {
        parallel {
            effect(
                GeometryEffect(
                    EffectGeometry.Curve(
                        listOf(
                            Vec3.ZERO,
                            Vec3(1.0, 2.0, 0.0),
                            Vec3(3.0, 1.0, 0.0),
                            Vec3(4.0, 2.0, 0.0),
                        )
                    ),
                    durationTicks = 100,
                    material = blue,
                )
            )
            effect(
                GeometryEffect(
                    EffectGeometry.Box(wireframe = true),
                    EffectAnchor.Origin(Vec3(0.0, 2.0, 2.0)),
                    100,
                    blue,
                )
            )
            effect(
                GeometryEffect(
                    EffectGeometry.Sphere(0.6),
                    EffectAnchor.Origin(Vec3(2.0, 2.0, 2.0)),
                    100,
                    blue,
                )
            )
            effect(
                GeometryEffect(
                    EffectGeometry.Cylinder(0.6, 1.2, topRadius = 0.0),
                    EffectAnchor.Origin(Vec3(4.0, 2.0, 2.0)),
                    100,
                    blue,
                )
            )
        }
    }
}

@ServerScriptEntrypoint(ServerPhase.READY)
fun registerSceneExampleCommands() {
    // Command callbacks do not carry entrypoint context; capture the revision now.
    val revision = requireNotNull(ScriptExecutionContext.currentScriptRevision())
    registerCommand("kattonscene") {
        for (name in listOf("impact", "trail", "cutscene", "geometry")) {
            literal(name) {
                executes { command ->
                    val player = command.source.playerOrException
                    playPlayerScene(
                        player,
                        "example:$name",
                        SceneContext(player.position(), player.uuid, 42),
                        revision,
                    )
                    1
                }
            }
        }
    }
}
