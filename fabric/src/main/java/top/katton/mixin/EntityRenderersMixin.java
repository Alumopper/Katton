package top.katton.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.platform.FabricEntityRendererHooks;

import java.util.Map;

@Mixin(EntityRenderers.class)
public abstract class EntityRenderersMixin {

    @Inject(method = "createEntityRenderers", at = @At("HEAD"))
    private static void captureContext(EntityRendererProvider.Context context, CallbackInfoReturnable<Map<EntityType<?>, EntityRenderer<?, ?>>> cir) {
        FabricEntityRendererHooks.INSTANCE.setCapturedContext(context);
    }
}
