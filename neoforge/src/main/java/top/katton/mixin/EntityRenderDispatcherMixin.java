package top.katton.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.platform.NeoForgeEntityRendererHooks;
import top.katton.util.EntityRenderDispatcherAccessor;

import java.util.function.Supplier;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements EntityRenderDispatcherAccessor {

    @Final @Shadow private BlockModelResolver blockModelResolver;
    @Final @Shadow private ItemModelResolver itemModelResolver;
    @Final @Shadow private MapRenderer mapRenderer;
    @Final @Shadow private ItemInHandRenderer itemInHandRenderer;
    @Final @Shadow private AtlasManager atlasManager;
    @Final @Shadow private Font font;
    @Final @Shadow public Options options;
    @Final @Shadow private Supplier<EntityModelSet> entityModels;
    @Final @Shadow private EquipmentAssetManager equipmentAssets;
    @Final @Shadow private PlayerSkinRenderCache playerSkinRenderCache;

    @Unique
    public EntityRendererProvider.Context katton$getContext() {
        return new EntityRendererProvider.Context(
                (EntityRenderDispatcher) (Object)this,
                this.blockModelResolver,
                this.itemModelResolver,
                this.mapRenderer,
                Minecraft.getInstance().getResourceManager(),
                this.entityModels.get(),
                this.equipmentAssets,
                this.atlasManager,
                this.font,
                this.playerSkinRenderCache
        );

    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer*", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void katton$injectEntityRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        EntityType<?> type = entity.getType();
        EntityRenderer<?, ?> kattonRenderer = NeoForgeEntityRendererHooks.INSTANCE.getKattonRenderer(type);
        if (kattonRenderer != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) kattonRenderer);
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer*", at = @At("HEAD"), cancellable = true)
    private <S extends EntityRenderState> void katton$injectStateRenderer(S state, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        EntityRenderer<?, ?> renderer = NeoForgeEntityRendererHooks.INSTANCE.findKattonRendererByState(state);
        if (renderer != null) {
            cir.setReturnValue((EntityRenderer<?, ? super S>) renderer);
        }
    }

    /**
     * Guards against entities with no renderer at all.
     * Returns false to prevent NPE when renderer is null.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void katton$guardMissingRenderer(E entity,
            Frustum frustum,
            double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        EntityRenderDispatcher self = (EntityRenderDispatcher) (Object) this;
        if (self.getRenderer(entity) == null) {
            cir.setReturnValue(false);
        }
    }
}
