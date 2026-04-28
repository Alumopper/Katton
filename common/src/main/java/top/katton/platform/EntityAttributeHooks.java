package top.katton.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.function.BiConsumer;

import top.katton.registry.DefaultAttributesHelper;

/**
 * Platform bridge for registering entity default attributes.
 *
 * <p>Supports two distinct registration paths:
 * <ul>
 *   <li><b>Global</b> — during mod initialization; uses modloader-native APIs
 *       (FabricDefaultAttributeRegistry on Fabric, EntityAttributeCreationEvent on NeoForge).</li>
 *   <li><b>Reloadable</b> — during /katton reload; uses {@link DefaultAttributesHelper}
 *       to bypass modloader limitations on late registration.</li>
 * </ul></p>
 */
public final class EntityAttributeHooks {

    private static BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> globalRegistrar = (type, supplier) -> {
        DefaultAttributesHelper.INSTANCE.ensureMutable();
        DefaultAttributesHelper.INSTANCE.register(type, supplier);
    };

    private static BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> reloadableRegistrar = (type, supplier) -> {
        DefaultAttributesHelper.INSTANCE.ensureMutable();
        DefaultAttributesHelper.INSTANCE.register(type, supplier);
    };

    private EntityAttributeHooks() {}

    /**
     * Sets the platform-specific global attribute registration callback.
     *
     * <p>This callback is invoked when entities are registered as GLOBAL
     * (or AUTO during mod init). The platform should use its native
     * API for optimal compatibility.</p>
     */
    public static void setGlobalRegistrar(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> registrar) {
        if (registrar != null) {
            EntityAttributeHooks.globalRegistrar = registrar;
        }
    }

    /**
     * Sets the platform-specific reloadable attribute registration callback.
     *
     * <p>This callback is invoked when entities are registered as RELOADABLE
     * (or AUTO after server start). The platform must handle late registration,
     * typically via {@link DefaultAttributesHelper}.</p>
     */
    public static void setReloadableRegistrar(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> registrar) {
        if (registrar != null) {
            EntityAttributeHooks.reloadableRegistrar = registrar;
        }
    }

    /**
     * Sets both registrars to the same callback (backward-compatible shorthand).
     * For backward compatibility, calls {@link #setGlobalRegistrar} and {@link #setReloadableRegistrar}.
     */
    public static void setAttributeRegistrar(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> registrar) {
        setGlobalRegistrar(registrar);
        setReloadableRegistrar(registrar);
    }

    /**
     * Registers entity attributes, choosing the path based on the reloadable flag.
     *
     * @param entityType the entity type
     * @param supplier the attribute supplier
     * @param reloadable {@code true} for RELOADABLE/AUTO-after-init;
     *                   {@code false} for GLOBAL/AUTO-at-init
     */
    public static void registerAttributes(EntityType<? extends LivingEntity> entityType, AttributeSupplier supplier, boolean reloadable) {
        (reloadable ? reloadableRegistrar : globalRegistrar).accept(entityType, supplier);
    }

    /**
     * Registers entity attributes using the reloadable path by default
     * (backward-compatible overload).
     *
     * @param entityType the entity type
     * @param supplier the attribute supplier
     */
    public static void registerAttributes(EntityType<? extends LivingEntity> entityType, AttributeSupplier supplier) {
        registerAttributes(entityType, supplier, false);
    }
}