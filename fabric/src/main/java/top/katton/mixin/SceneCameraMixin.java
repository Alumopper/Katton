package top.katton.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.api.scene.CameraPose;
import top.katton.client.scene.ClientSceneManager;
import top.katton.scene.SceneFrame;

/** Apply before vanilla computes projection and culling; never mutate the camera entity. */
@Mixin(Camera.class)
public abstract class SceneCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private float yRot;
    @Shadow private float xRot;
    @Shadow private boolean detached;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void katton$camera(float partialTick, CallbackInfo ci) {
        SceneFrame frame = ClientSceneManager.sample(partialTick);
        CameraPose pose = frame.getCamera();
        if (pose != null) {
            setPosition(pose.getPosition());
            detached = true;
        }
        float roll = (pose == null ? 0 : pose.getRoll()) + frame.getShakeRoll();
        if (pose != null || frame.getShakeYaw() != 0 || frame.getShakePitch() != 0 || roll != 0) {
            setRotation((pose == null ? yRot : pose.getYaw()) + frame.getShakeYaw(),
                    (pose == null ? xRot : pose.getPitch()) + frame.getShakePitch());
            rotation.rotateZ((float) Math.toRadians(roll));
            rotation.transform(new Vector3f(0, 0, -1), forwards);
            rotation.transform(new Vector3f(0, 1, 0), up);
            rotation.transform(new Vector3f(-1, 0, 0), left);
        }
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void katton$fov(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(ClientSceneManager.fov(cir.getReturnValue()));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void katton$extractGeometry(CallbackInfo ci) {
        ClientSceneManager.prepareGeometry((Camera) (Object) this);
    }
}
