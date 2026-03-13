package top.katton.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.KattonClientRenderApiKt;

@Mixin(Gui.class)
public class GuiRenderMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void katton$renderHud(GuiGraphics guiGraphics, Object partialTick, CallbackInfo ci) {
        KattonClientRenderApiKt.dispatchHudRender(guiGraphics, 0.0f);
    }
}
