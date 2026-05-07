package top.katton.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.katton.registry.DefaultAttributesHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge-specific hooks for registering entity default attributes.
 *
 * <p>Two distinct registration paths:
 * <ul>
 *   <li><b>Global</b> — during mod init: entries are queued and flushed
 *       via {@link EntityAttributeCreationEvent} on the mod bus, using
 *       NeoForge's native API for optimal compatibility.</li>
 *   <li><b>Reloadable</b> — during /katton reload: uses
 *       {@link DefaultAttributesHelper} to directly manipulate the
 *       {@code DefaultAttributes.SUPPLIERS} map via reflection.</li>
 * </ul></p>
 */
public final class NeoForgeEntityAttributeHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoForgeEntityAttributeHooks.class);

    /** Pending global attribute registrations, flushed on EntityAttributeCreationEvent. */
    private static final List<PendingRegistration> pendingGlobal = new ArrayList<>();

    private record PendingRegistration(
        EntityType<? extends LivingEntity> entityType,
        AttributeSupplier supplier
    ) {}

    private NeoForgeEntityAttributeHooks() {}

    /**
     * Queues an attribute registration for the {@link EntityAttributeCreationEvent}.
     *
     * <p>Called during mod init for GLOBAL entities. The actual registration
     * happens when {@link #flushOnModBus(EntityAttributeCreationEvent)} is
     * invoked from the event listener.</p>
     */
    public static void registerAttributesGlobal(EntityType<? extends LivingEntity> entityType, AttributeSupplier supplier) {
        synchronized (pendingGlobal) {
            pendingGlobal.add(new PendingRegistration(entityType, supplier));
        }
    }

    /**
     * Directly registers attributes via DefaultAttributes map manipulation.
     *
     * <p>Called for RELOADABLE entities during hot-reload. Since
     * {@link EntityAttributeCreationEvent} no longer fires at this point,
     * we use reflection to bypass it.</p>
     */
    public static void registerAttributesReloadable(EntityType<? extends LivingEntity> entityType, AttributeSupplier supplier) {
        DefaultAttributesHelper.INSTANCE.ensureMutable();
        DefaultAttributesHelper.INSTANCE.register(entityType, supplier);
    }

    /**
     * Flushes all pending global attribute registrations via the given event.
     *
     * <p>Must be called from a mod bus listener for
     * {@link EntityAttributeCreationEvent}. After flushing, the pending
     * queue is cleared.</p>
     *
     * @param event the EntityAttributeCreationEvent to submit registrations to
     */
    public static void flushOnModBus(EntityAttributeCreationEvent event) {
        List<PendingRegistration> copy;
        synchronized (pendingGlobal) {
            copy = new ArrayList<>(pendingGlobal);
            pendingGlobal.clear();
        }
        for (PendingRegistration reg : copy) {
            event.put(reg.entityType(), reg.supplier());
        }
        if (!copy.isEmpty()) {
            LOGGER.info("Flushed {} global entity attribute registrations via EntityAttributeCreationEvent", copy.size());
        }
    }

    /**
     * Legacy method: registers attributes directly via DefaultAttributesHelper
     * (same as reloadable path). Provided for backward compatibility.
     */
    public static void registerAttributes(EntityType<? extends LivingEntity> entityType, AttributeSupplier supplier) {
        registerAttributesReloadable(entityType, supplier);
    }
}