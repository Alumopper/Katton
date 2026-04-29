package top.katton.mixin;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.platform.NeoForgeEntityRendererHooks;

@Mixin(EntityRenderers.class)
public abstract class EntityRenderersMixin {

    @Inject(method = "createEntityRenderers", at = @At("HEAD"))
    private static void captureContext(EntityRendererProvider.Context context, CallbackInfo ci) {
        NeoForgeEntityRendererHooks.INSTANCE.setCapturedContext(context);
    }
}
