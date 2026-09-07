package top.katton.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.client.scene.ClientSceneManager;

@Mixin(LevelRenderer.class)
public abstract class SceneLevelRendererMixin {
    /*? if mc_26_2 {*/
    @Inject(method = "render", at = @At("TAIL"))
    /*?} else {*/
    /*@Inject(method = "renderLevel", at = @At("TAIL"))*/
    /*?}*/
    private void katton$draw(CallbackInfo ci) {
        ClientSceneManager.render();
    }
}
