package top.katton.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.client.scene.ClientSceneManager;

@Mixin(KeyboardHandler.class)
public abstract class SceneKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void katton$skip(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS && ClientSceneManager.skipCamera()) ci.cancel();
    }
}
