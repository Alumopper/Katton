package top.katton.mixin;

import net.minecraft.world.level.material.MapColor;

public interface CommonBlockBehaviourPropertiesAccessor {

    void katton$setDestroyTime(float destroyTime);

    void katton$setExplosionResistance(float explosionResistance);

    void katton$setRequiresCorrectToolForDrops(boolean requiresCorrectToolForDrops);

    void katton$setFriction(float friction);

    void katton$setSpeedFactor(float speedFactor);

    void katton$setJumpFactor(float jumpFactor);

    void katton$setLightEmission(int lightEmission);

    void katton$setMapColor(MapColor mapColor);

    void katton$setCanOcclude(boolean canOcclude);

    void katton$setIsAir(boolean isAir);

    void katton$setHasCollision(boolean hasCollision);

    void katton$setIsSuffocating(boolean isSuffocating);

    void katton$setIsViewBlocking(boolean isViewBlocking);
}
