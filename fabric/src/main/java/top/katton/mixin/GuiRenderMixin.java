package top.katton.mixin;

import net.minecraft.client.DeltaTracker;
/*? if mc_26_1_2 {*/
/*import net.minecraft.client.gui.Gui;*/
/*?}*/
import net.minecraft.client.gui.GuiGraphicsExtractor;
/*? if mc_26_2 {*/
import net.minecraft.client.gui.Hud;
/*?}*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.KattonClientRenderApiKt;
import top.katton.client.ReloadProgressOverlay;

/*? if mc_26_1_2 {*/
/*
@Mixin(Gui.class)
public class GuiRenderMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void katton$renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        KattonClientRenderApiKt.dispatchHudRender(graphics, 0.0f);
        ReloadProgressOverlay.renderExtractor(graphics);
    }
}
*/
/*?} mc_26_2 {*/
@Mixin(Hud.class)
public class GuiRenderMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void katton$renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        KattonClientRenderApiKt.dispatchHudRender(graphics, 0.0f);
        ReloadProgressOverlay.renderExtractor(graphics);
    }
}
/*?}*/
