package top.katton.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.client.scene.ClientSceneManager;

@Mixin(GameRenderer.class)
public abstract class SceneHandMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void katton$detachedHand(CallbackInfo ci) {
        if (ClientSceneManager.cameraActive()) ci.cancel();
    }
}
