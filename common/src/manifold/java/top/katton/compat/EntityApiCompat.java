package top.katton.compat;

#if MC_VERSION == "26.2"
import net.minecraft.advancements.predicates.NbtPredicate;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityTypes;
#else
import net.minecraft.advancements.criterion.NbtPredicate;
#endif

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class EntityApiCompat {
    private EntityApiCompat() {
    }

    public static boolean isPlayerEntityType(EntityType<?> type) {
#if MC_VERSION == "26.2"
        return type == EntityTypes.PLAYER;
#else
        return type == EntityType.PLAYER;
#endif
    }

    public static Entity loadEntityRecursive(
        CompoundTag tag,
        Level level,
        EntitySpawnReason reason,
        EntityProcessor processor
    ) {
#if MC_VERSION == "26.2"
        return EntityType.loadEntityRecursive(tag, level, new EntitySpawnRequest(reason, false), processor);
#else
        return EntityType.loadEntityRecursive(tag, level, reason, processor);
#endif
    }

    public static CompoundTag getEntityTagToCompare(Entity entity) {
        return NbtPredicate.getEntityTagToCompare(entity);
    }
}
