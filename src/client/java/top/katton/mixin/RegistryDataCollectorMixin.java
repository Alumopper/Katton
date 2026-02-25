package top.katton.mixin;

import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.network.ClientNetworking;

/**
 * Mixin to intercept RegistryDataCollector.collectGameRegistries().
 * 
 * After collectGameRegistries() completes, it has called updateComponents()
 * which runs DataComponentInitializers.build(). This overwrites holder.components
 * for all items with values from their finalizeInitializer(), which always sets
 * ITEM_NAME to Component.translatable(descriptionId) and ITEM_MODEL to the
 * default model path.
 * 
 * We need to re-apply our custom ITEM_NAME and ITEM_MODEL values after this.
 */
@Mixin(RegistryDataCollector.class)
public class RegistryDataCollectorMixin {

    @Inject(
        method = "collectGameRegistries",
        at = @At("RETURN")
    )
    private void katton$onCollectGameRegistriesReturn(
            ResourceProvider resourceProvider,
            RegistryAccess.Frozen frozen,
            boolean isLocal,
            CallbackInfoReturnable<RegistryAccess.Frozen> cir
    ) {
        // Re-apply custom components after DataComponentInitializers.build() has run
        if (ClientNetworking.INSTANCE.hasRegisteredItems()) {
            ClientNetworking.INSTANCE.reapplyCustomComponents();
        }
    }
}
