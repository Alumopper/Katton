@file:Suppress("unused")

package top.katton.platform

import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType

/**
 * Platform abstraction for entity renderer registration.
 *
 * Each loader (Fabric, NeoForge) provides its own implementation to wire
 * entity renderers into the game's rendering system. The common module
 * calls these hooks through the static [instance].
 */
interface EntityRendererHooks {
    /**
     * Registers an entity renderer for the given entity type.
     *
     * @param entityType the entity type to register a renderer for
     * @param factory the renderer factory (receives EntityRendererProvider.Context)
     */
    fun <T : Entity> registerRenderer(entityType: EntityType<T>, factory: EntityRendererProvider<T>)

    /**
     * Unregisters the renderer for the given entity type.
     *
     * Used during hot reload to clear stale renderers before re-registration.
     *
     * @param entityType the entity type whose renderer should be removed
     * @return true if a renderer was removed, false otherwise
     */
    fun unregisterRenderer(entityType: EntityType<*>): Boolean

    /**
     * Registers a model layer definition.
     *
     * @param layer the model layer location
     * @param definition factory that creates the layer definition
     */
    fun registerModelLayer(layer: ModelLayerLocation, definition: () -> LayerDefinition)

    /**
     * Unregisters a model layer definition.
     *
     * @param layer the model layer location
     * @return true if the layer was removed, false otherwise
     */
    fun unregisterModelLayer(layer: ModelLayerLocation): Boolean

    /**
     * Returns the pre-baked [ModelPart] for a previously registered model layer.
     *
     * Use this instead of `context.bakeLayer()` for dynamically registered
     * model layers, since [net.minecraft.client.model.geom.EntityModelSet]
     * uses an ImmutableMap in 1.21.11+ and cannot resolve them.
     */
    fun getBakedModelPart(layer: ModelLayerLocation): ModelPart?

    /**
     * Called during Katton client init to bootstrap all pending renderer
     * registrations that were queued before the client was ready.
     */
    fun flushPending()

    companion object {
        @Volatile
        var instance: EntityRendererHooks = NoopEntityRendererHooks
            private set

        fun setInstance(hooks: EntityRendererHooks) {
            instance = hooks
        }
    }
}

/**
 * No-op implementation used when no platform has initialized the hooks yet.
 */
private object NoopEntityRendererHooks : EntityRendererHooks {
    override fun <T : Entity> registerRenderer(entityType: EntityType<T>, factory: EntityRendererProvider<T>) {}
    override fun unregisterRenderer(entityType: EntityType<*>): Boolean = false
    override fun registerModelLayer(layer: ModelLayerLocation, definition: () -> LayerDefinition) {}
    override fun unregisterModelLayer(layer: ModelLayerLocation): Boolean = false
    override fun getBakedModelPart(layer: ModelLayerLocation): ModelPart? = null
    override fun flushPending() {}
}
