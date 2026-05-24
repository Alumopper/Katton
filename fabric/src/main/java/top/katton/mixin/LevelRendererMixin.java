package top.katton.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.KattonClientRenderApiKt;
import top.katton.client.ClientItemRenderMarkerManager;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Unique
    private float katton$tickDelta;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void katton$captureTickDelta(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci) {
        this.katton$tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void katton$renderWorld(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci) {
        KattonClientRenderApiKt.dispatchWorldRender(cameraState, this.katton$tickDelta);
    }

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void katton$renderItemMarkers(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        ClientItemRenderMarkerManager.render(levelRenderState.cameraRenderState, poseStack, submitNodeCollector, this.katton$tickDelta);
    }
}
