package top.katton.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.client.scene.ClientSceneManager;

@Mixin(MouseHandler.class)
public abstract class SceneMouseMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void katton$mouse(double time, CallbackInfo ci) {
        if (ClientSceneManager.inputLocked()) {
            accumulatedDX = 0;
            accumulatedDY = 0;
            ci.cancel();
        }
    }
}
