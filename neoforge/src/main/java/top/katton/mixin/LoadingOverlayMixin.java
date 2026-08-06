package top.katton.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.client.ReloadProgressOverlay;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void katton$renderReloadProgress(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ReloadProgressOverlay.INSTANCE.renderExtractor(graphics);
    }
}
