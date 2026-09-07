package top.katton.mixin;

import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.client.scene.ClientSceneManager;

@Mixin(KeyboardInput.class)
public abstract class SceneInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void katton$input(CallbackInfo ci) {
        if (ClientSceneManager.inputLocked()) {
            keyPresses = Input.EMPTY;
            moveVector = Vec2.ZERO;
        }
    }
}
