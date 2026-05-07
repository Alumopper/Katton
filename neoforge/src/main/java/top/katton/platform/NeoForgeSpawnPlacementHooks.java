package top.katton.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge-specific spawn placement registration:
 * GLOBAL → queues for {@link RegisterSpawnPlacementsEvent}
 * RELOADABLE → reflection (private method).
 */
public final class NeoForgeSpawnPlacementHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeSpawnPlacementHooks.class);
    private static final List<Pending> pendingGlobal = new ArrayList<>();

    private record Pending(
        EntityType<? extends Mob> type,
        SpawnPlacementType placementType,
        Heightmap.Types heightmap,
        SpawnPlacements.SpawnPredicate<?> predicate
    ) {}

    private NeoForgeSpawnPlacementHooks() {}

    /** Queues for RegisterSpawnPlacementsEvent. */
    @SuppressWarnings("unchecked")
    public static <T extends Mob> void registerGlobal(
            EntityType<T> type, SpawnPlacementType placementType,
            Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        synchronized (pendingGlobal) {
            pendingGlobal.add(new Pending(type, placementType, heightmap, predicate));
        }
    }

    /**
     * Direct reflection call for hot-reload.
     *
     * @param <T>           mob type
     * @param type          entity type
     * @param placementType spawn placement type
     * @param heightmap     heightmap type
     * @param predicate     spawn predicate
     */
    @SuppressWarnings("unchecked")
    public static <T extends Mob> void registerReloadable(
            EntityType<T> type, SpawnPlacementType placementType,
            Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        SpawnPlacementHooks.registerReflective(type, placementType, heightmap, predicate);
    }

    /**
     * Flushes pending GLOBAL registrations via the event.
     *
     * @param event the register spawn placements event
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void flushOnModBus(RegisterSpawnPlacementsEvent event) {
        List<Pending> copy;
        synchronized (pendingGlobal) {
            copy = new ArrayList<>(pendingGlobal);
            pendingGlobal.clear();
        }
        for (Pending reg : copy) {
            event.register((EntityType) reg.type(), reg.placementType(), reg.heightmap(),
                (SpawnPlacements.SpawnPredicate) reg.predicate(), Operation.REPLACE);
        }
        if (!copy.isEmpty()) {
            LOGGER.info("Flushed {} global spawn placement registrations via RegisterSpawnPlacementsEvent", copy.size());
        }
    }
}