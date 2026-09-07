package top.katton.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.client.scene.ClientSceneManager;

@Mixin(Minecraft.class)
public abstract class SceneInteractionMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void katton$attack(CallbackInfoReturnable<Boolean> cir) {
        if (ClientSceneManager.inputLocked()) cir.setReturnValue(false);
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void katton$interaction(CallbackInfo ci) {
        if (ClientSceneManager.inputLocked()) ci.cancel();
    }
    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean katton$releaseMining(boolean attacking) {
        return !ClientSceneManager.inputLocked() && attacking;
    }
}
