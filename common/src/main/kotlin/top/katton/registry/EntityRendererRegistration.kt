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
import top.katton.platform.EntityRendererHooks
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.renderer.entity.EntityRenderDispatcher

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
    }

    fun managedCount(): Int = managedEntityTypes.size

    fun managedIds(): Set<Identifier> = managedEntityTypes.toSet()

    // ── Model Layer Support ────────────────────────────────────────────

    /** Pending model layers to register when the platform is ready. */
    private val pendingModelLayers = ConcurrentHashMap<ModelLayerLocation, () -> LayerDefinition>()

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