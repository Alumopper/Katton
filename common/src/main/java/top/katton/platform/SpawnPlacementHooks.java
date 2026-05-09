package top.katton.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import top.katton.util.ReflectUtil;

import java.util.function.Consumer;

/**
 * Platform bridge for registering spawn placement rules.
 *
 * <p>Two registration paths:
 * <ul>
 *   <li><b>Global</b> — during mod init; uses modloader-native APIs
 *       (NeoForge {@code RegisterSpawnPlacementsEvent}, Fabric access widener or reflection).</li>
 *   <li><b>Reloadable</b> — during /katton reload; uses reflection via
 *       {@code SpawnPlacements.register()} private method.</li>
 * </ul></p>
 */
public final class SpawnPlacementHooks {

    /**
     * Functional interface matching SpawnPlacements.SpawnPredicate but not package-private.
     */
    @FunctionalInterface
    public interface SpawnPredicate<T extends Mob> {
        boolean test(EntityType<T> type, net.minecraft.world.level.ServerLevelAccessor level,
                     net.minecraft.world.entity.EntitySpawnReason reason, net.minecraft.core.BlockPos pos,
                     net.minecraft.util.RandomSource random);
    }

    private SpawnPlacementHooks() {}

    /**
     * Registers a spawn placement via the modloader's native API.
     * On NeoForge, this queues for {@code RegisterSpawnPlacementsEvent}.
     * On Fabric, this calls the private method via reflection.
     */
    public static <T extends Mob> void registerGlobal(
            EntityType<T> type, SpawnPlacementType placementType,
            Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        // Default: use reflection to call the private method
        registerReflective(type, placementType, heightmap, predicate);
    }

    /**
     * Registers a spawn placement for reloadable mode (always uses reflection).
     */
    public static <T extends Mob> void registerReloadable(
            EntityType<T> type, SpawnPlacementType placementType,
            Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        registerReflective(type, placementType, heightmap, predicate);
    }

    /**
     * Calls SpawnPlacements.register() via reflection.
     * Public so platform-specific hooks can delegate to it.
     */
    public static <T extends Mob> void registerReflective(
            EntityType<T> type, SpawnPlacementType placementType,
            Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        ReflectUtil.INSTANCE.invokeStatic(
            SpawnPlacements.class, "register",
            new Class<?>[] { EntityType.class, SpawnPlacementType.class,
                             Heightmap.Types.class, SpawnPlacements.SpawnPredicate.class },
            type, placementType, heightmap, predicate
        ).getOrThrow();
    }

    /**
     * Removes a spawn placement rule from the internal map.
     * Used during hot-reload cleanup.
     */
    @SuppressWarnings("unchecked")
    public static void unregister(EntityType<?> type) {
        var map = (java.util.Map<EntityType<?>, ?>) ReflectUtil.INSTANCE
            .getStatic(SpawnPlacements.class, "DATA_BY_TYPE")
            .getOrNull();
        if (map != null) {
            map.remove(type);
        }
    }
}