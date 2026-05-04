@file:Suppress("unused")

package top.katton.registry

import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.core.registries.BuiltInRegistries.*
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.resources.Identifier
import top.katton.api.LOGGER
import top.katton.platform.EntityRendererHooks
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal registry for script-managed entity renderers.
 *
 * Unlike other Katton registries (ITEMS, BLOCKS, etc.), entity renderers
 * are not stored in a Minecraft BuiltInRegistry. Instead, they're injected
 * directly into [EntityRenderDispatcher.renderers] via the platform hooks.
 *
 * This registry tracks ownership and supports hot reload:
 * - [beginReload] clears managed renderers from the game's renderer map
 * - After scripts re-execute, new [register] calls re-populate the map
 */
object EntityRendererRegistration {

    /** Tracks which entity types have renderers managed by Katton (for cleanup). */
    private val managedEntityTypes = ConcurrentHashMap.newKeySet<Identifier>()
    private val pendingRendererFactories = LinkedHashMap<Identifier, MutableList<EntityRendererProvider<out Entity>>>()

    fun isManaged(entityType: EntityType<*>): Boolean {
        val key = ENTITY_TYPE.getKey(entityType)
        return key in managedEntityTypes
    }

    /**
     * Registers an entity renderer for the given entity type.
     *
     * @param entityType the entity type
     * @param factory the renderer factory
     */
    fun <T : Entity> register(entityType: EntityType<T>, factory: EntityRendererProvider<T>) {
        val key = ENTITY_TYPE.getKey(entityType)
        managedEntityTypes.add(key)
        EntityRendererHooks.instance.registerRenderer(entityType, factory)
    }

    @Synchronized
    fun <T : Entity> register(entityTypeId: Identifier, factory: EntityRendererProvider<T>) {
        val entityType = ENTITY_TYPE.getOptional(entityTypeId)
        if (entityType.isPresent) {
            @Suppress("UNCHECKED_CAST")
            register(entityType.get() as EntityType<T>, factory)
            return
        }

        @Suppress("UNCHECKED_CAST")
        pendingRendererFactories
            .getOrPut(entityTypeId) { mutableListOf() }
            .add(factory as EntityRendererProvider<out Entity>)
    }

    /**
     * Begins a hot reload cycle. Unregisters all Katton-managed renderers
     * from the game's rendering system so they can be re-registered fresh.
     */
    @Synchronized
    fun beginReload() {
        val toRemove = managedEntityTypes.toList()
        for (entityId in toRemove) {
            val entityType = ENTITY_TYPE.getOptional(entityId)
            if (entityType.isPresent) {
                EntityRendererHooks.instance.unregisterRenderer(entityType.get())
            }
            managedEntityTypes.remove(entityId)
        }
        synchronized(pendingRendererFactories) {
            pendingRendererFactories.clear()
        }
    }

    fun managedCount(): Int = managedEntityTypes.size

    fun managedIds(): Set<Identifier> = managedEntityTypes.toSet()

    @Synchronized
    fun flushPendingRegistrations() {
        if (pendingRendererFactories.isEmpty()) {
            return
        }

        val unresolved = mutableListOf<Identifier>()
        val iterator = pendingRendererFactories.entries.iterator()
        while (iterator.hasNext()) {
            val (entityId, factories) = iterator.next()
            val entityType = ENTITY_TYPE.getOptional(entityId)
            if (entityType.isEmpty) {
                unresolved += entityId
                continue
            }

            @Suppress("UNCHECKED_CAST")
            val castType = entityType.get() as EntityType<Entity>
            factories.forEach { factory ->
                @Suppress("UNCHECKED_CAST")
                register(castType, factory as EntityRendererProvider<Entity>)
            }
            iterator.remove()
        }

        if (unresolved.isNotEmpty()) {
            LOGGER.debug("Deferred {} entity renderer registrations until entity types are available: {}", unresolved.size, unresolved)
        }
    }

    // ── Model Layer Support ────────────────────────────────────────────

    /** Tracks registered model layers for cleanup on reload. */
    private val managedModelLayers = ConcurrentHashMap.newKeySet<ModelLayerLocation>()

    fun registerModelLayer(layer: ModelLayerLocation, definition: () -> LayerDefinition) {
        EntityRendererHooks.instance.registerModelLayer(layer, definition)
        managedModelLayers.add(layer)
    }

    fun beginModelLayerReload() {
        val toRemove = managedModelLayers.toList()
        for (layer in toRemove) {
            EntityRendererHooks.instance.unregisterModelLayer(layer)
            managedModelLayers.remove(layer)
        }
    }

    fun getBakedModelPart(layer: ModelLayerLocation): ModelPart {
        return EntityRendererHooks.instance.getBakedModelPart(layer)
            ?: throw IllegalStateException("Model layer not registered: $layer. Call registerEntityModelLayer() first.")
    }
}
