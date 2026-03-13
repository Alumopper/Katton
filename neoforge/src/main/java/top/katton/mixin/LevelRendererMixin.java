package top.katton.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.KattonClientRenderApiKt;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void katton$renderWorld(Object deltaTracker, boolean renderBlockOutline, Camera camera, Object gameRenderer, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        KattonClientRenderApiKt.dispatchWorldRender(matrix4f, matrix4f2, camera, 0.0f);
    }
}
