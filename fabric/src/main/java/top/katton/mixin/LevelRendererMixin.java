package top.katton.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.KattonClientRenderApiKt;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void katton$renderWorld(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f projectionMatrixForCulling, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
        KattonClientRenderApiKt.dispatchWorldRender(modelViewMatrix, projectionMatrix, camera, 0.0f);
    }
}
