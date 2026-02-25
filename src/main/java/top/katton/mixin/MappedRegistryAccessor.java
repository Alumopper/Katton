package top.katton.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor {
    @Accessor("frozen")
    void setFrozen(boolean frozen);
    
    @Accessor("frozen")
    boolean isFrozen();

}
