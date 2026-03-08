package top.katton.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockBehaviour.Properties.class)
public interface BlockBehaviourPropertiesAccessor extends CommonBlockBehaviourPropertiesAccessor {
    
    @Accessor("destroyTime")
    void katton$setDestroyTime(float destroyTime);
    
    @Accessor("explosionResistance")
    void katton$setExplosionResistance(float explosionResistance);
    
    @Accessor("requiresCorrectToolForDrops")
    void katton$setRequiresCorrectToolForDrops(boolean requiresCorrectToolForDrops);
    
    @Accessor("friction")
    void katton$setFriction(float friction);
    
    @Accessor("speedFactor")
    void katton$setSpeedFactor(float speedFactor);
    
    @Accessor("jumpFactor")
    void katton$setJumpFactor(float jumpFactor);
    
    @Accessor("lightEmission")
    void katton$setLightEmission(int lightEmission);
    
    @Accessor("mapColor")
    void katton$setMapColor(MapColor mapColor);
    
    @Accessor("canOcclude")
    void katton$setCanOcclude(boolean canOcclude);
    
    @Accessor("isAir")
    void katton$setIsAir(boolean isAir);
    
    @Accessor("hasCollision")
    void katton$setHasCollision(boolean hasCollision);
    
    @Accessor("isSuffocating")
    void katton$setIsSuffocating(boolean isSuffocating);
    
    @Accessor("isViewBlocking")
    void katton$setIsViewBlocking(boolean isViewBlocking);
}
