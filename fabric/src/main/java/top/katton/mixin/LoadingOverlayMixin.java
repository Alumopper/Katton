package top.katton.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.Katton;
import top.katton.client.ReloadProgressOverlay;
import top.katton.engine.ScriptReloadManager;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Unique
    private boolean katton$clientScriptReloaded;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void katton$renderReloadProgress(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ReloadProgressOverlay.renderExtractor(graphics);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void katton$finishOnDone(CallbackInfo ci) {
        if (reload.isDone()) {
            if (!katton$clientScriptReloaded) {
                ScriptReloadManager.reloadClientScriptsAsync();
                katton$clientScriptReloaded = true;
            }
        }
    }
}
