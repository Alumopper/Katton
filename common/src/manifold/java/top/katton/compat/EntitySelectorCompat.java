package top.katton.compat;

#if MC_VERSION == "26.2"
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
#else
import net.minecraft.advancements.criterion.MinMaxBounds;
#endif

import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class EntitySelectorCompat {
    private EntitySelectorCompat() {
    }

    public static EntityType<?> playerEntityType() {
#if MC_VERSION == "26.2"
        return EntityTypes.PLAYER;
#else
        return EntityType.PLAYER;
#endif
    }

    public static EntityType<?> entityTypeById(Identifier id) {
#if MC_VERSION == "26.2"
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
#else
        return EntityType.byString(id.toString()).orElse(null);
#endif
    }

    public static DoubleBounds doublesBetween(double min, double max) {
        return new DoubleBounds(MinMaxBounds.Doubles.between(min, max));
    }

    public static DoubleBounds doublesAtMost(double max) {
        return new DoubleBounds(MinMaxBounds.Doubles.atMost(max));
    }

    public static DoubleBounds doublesAtLeast(double min) {
        return new DoubleBounds(MinMaxBounds.Doubles.atLeast(min));
    }

    public static IntBounds intsBetween(int min, int max) {
        return new IntBounds(MinMaxBounds.Ints.between(min, max));
    }

    public static IntBounds intsAtMost(int max) {
        return new IntBounds(MinMaxBounds.Ints.atMost(max));
    }

    public static IntBounds intsAtLeast(int min) {
        return new IntBounds(MinMaxBounds.Ints.atLeast(min));
    }

    public static FloatDegreesBounds floatDegreesBetween(float min, float max) {
        MinMaxBounds.Bounds<Float> bounds = new MinMaxBounds.Bounds<>(Optional.of(min), Optional.of(max));
        return new FloatDegreesBounds(new MinMaxBounds.FloatDegrees(bounds));
    }

    public static Double maxDistance(DoubleBounds bounds) {
        return bounds == null ? null : bounds.value.bounds().max().orElse(null);
    }

    public static EntitySelector createSelector(
        int maxResults,
        boolean includesEntities,
        boolean worldLimited,
        List<Predicate<Entity>> predicates,
        DoubleBounds distance,
        Function<Vec3, Vec3> position,
        AABB aabb,
        BiConsumer<Vec3, List<? extends Entity>> order,
        EntityType<?> type
    ) {
        return new EntitySelector(
            maxResults,
            includesEntities,
            worldLimited,
            predicates,
            distance == null ? null : distance.value,
            position,
            aabb,
            order,
            false,
            null,
            (UUID) null,
            type,
            true
        );
    }

    public static final class DoubleBounds {
        private final MinMaxBounds.Doubles value;

        private DoubleBounds(MinMaxBounds.Doubles value) {
            this.value = value;
        }
    }

    public static final class IntBounds {
        private final MinMaxBounds.Ints value;

        private IntBounds(MinMaxBounds.Ints value) {
            this.value = value;
        }
    }

    public static final class FloatDegreesBounds {
        private final MinMaxBounds.FloatDegrees value;

        private FloatDegreesBounds(MinMaxBounds.FloatDegrees value) {
            this.value = value;
        }
    }
}
