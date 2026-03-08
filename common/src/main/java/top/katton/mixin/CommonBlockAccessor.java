package top.katton.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public interface CommonBlockAccessor {
    Holder.Reference<Block> katton$getBuiltInRegistryHolder();
    
    BlockBehaviour.Properties katton$getProperties();
}

