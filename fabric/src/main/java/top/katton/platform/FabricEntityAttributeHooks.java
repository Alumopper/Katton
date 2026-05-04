package top.katton.platform;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric-specific hooks for registering entity default attributes.
 *
 * <p>On Fabric, we attempt to use {@link FabricDefaultAttributeRegistry} first
 * for Fabric API compatibility. If that fails (e.g. during hot-reload when
 * attributes were already registered), we silently continue since the attributes
 * have already been registered in the underlying map.</p>
 */
public final class FabricEntityAttributeHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger(FabricEntityAttributeHooks.class);

    private FabricEntityAttributeHooks() {}

    /**
     * Registers default attributes for an entity type on Fabric.
     *
     * <p>First attempts Fabric API registration for proper compatibility,
     * then falls back silently if the registration is rejected (duplicate
     * registration during hot-reload).</p>
     *
     * @param entityType the entity type
     * @param supplier the attribute supplier
     */
    public static void registerAttributes(EntityType<? extends LivingEntity> entityType, AttributeSupplier supplier) {
        try {
            FabricDefaultAttributeRegistry.register(entityType, supplier);
        } catch (IllegalStateException e) {
            // Duplicate registration during hot-reload — Fabric API rejects it.
            // The attribute map was already populated, so this is expected.
            LOGGER.debug("Entity type {} already has Fabric attributes registered, updating directly", entityType, e);
        }
    }
}