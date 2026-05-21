package top.katton.mixin;

import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.registry.KattonRegistry;
import top.katton.api.mod.KattonItemModificationApiKt;

/**
 * Mixin to intercept RegistryDataCollector.collectGameRegistries().
 *
 * During collectGameRegistries(), Minecraft calls DataComponentInitializers.build()
 * which applies all component initializers to registry holders. This overwrites
 * the custom ITEM_NAME and ITEM_MODEL that Katton set during item registration
 * with defaults from Item's constructor (finalizeInitializer).
 *
 * This mixin runs after collectGameRegistries() completes and reapplies the
 * custom components to all Katton-registered items.
 */
@Mixin(RegistryDataCollector.class)
public class RegistryDataCollectorMixin {

    @Inject(
        method = "collectGameRegistries",
        at = @At("RETURN")
    )
    private void katton$onCollectGameRegistriesReturn(
        ResourceProvider resourceProvider,
        RegistryAccess.Frozen originalRegistries,
        boolean tagsAndComponentsForSynchronizedRegistriesOnly,
        CallbackInfoReturnable<RegistryAccess.Frozen> cir
    ) {
        KattonRegistry.ITEMS.reapplyCustomItemComponents();
        KattonItemModificationApiKt.reapplyItemModifications();
    }
}
