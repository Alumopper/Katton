package top.katton.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Block.class)
public interface BlockAccessor {
    @Accessor("builtInRegistryHolder")
    Holder.Reference<Block> katton$getBuiltInRegistryHolder();
}

