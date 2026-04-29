package top.katton.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.platform.NeoForgeEntityRendererHooks;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void katton$injectEntityRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        EntityType<?> type = entity.getType();
        EntityRenderer<?, ?> kattonRenderer = NeoForgeEntityRendererHooks.INSTANCE.getKattonRenderer(type);
        if (kattonRenderer != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) kattonRenderer);
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer", at = @At("HEAD"), cancellable = true)
    private <S extends EntityRenderState> void katton$injectStateRenderer(S state, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        EntityRenderer<?, ?> renderer = NeoForgeEntityRendererHooks.INSTANCE.findKattonRendererByState(state);
        if (renderer != null) {
            cir.setReturnValue((EntityRenderer<?, ? super S>) renderer);
        }
    }
}
