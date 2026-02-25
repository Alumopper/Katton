package top.katton.mixin;

import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor for MappedRegistry internal fields.
 * Used to temporarily unfreeze the registry for hot-reload item registration.
 */
@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor {
    
    /**
     * Sets the frozen state of the registry.
     * @param frozen true to freeze, false to unfreeze
     */
    @Accessor("frozen")
    void setFrozen(boolean frozen);
    
    /**
     * Gets the frozen state of the registry.
     * @return true if frozen, false otherwise
     */
    @Accessor("frozen")
    boolean isFrozen();
}
