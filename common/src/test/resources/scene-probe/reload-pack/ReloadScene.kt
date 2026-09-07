import top.katton.api.*
import top.katton.api.scene.*

@ClientScriptEntrypoint(ClientPhase.REGISTRY_SETUP)
fun registerReloadProbeScene() {
    registerClientScene("scene_probe:reload") {
        effect(6000) { context ->
            CameraPath(
                listOf(
                    CameraKeyframe(0, CameraPose(context.origin.add(0.0, 3.0, 5.0))),
                    CameraKeyframe(6000, CameraPose(context.origin.add(60.0, 3.0, 5.0))),
                ),
                CameraOptions(lookAt = EffectAnchor.Origin()),
            )
        }
    }
    // ACTIVATION_POINT
}
