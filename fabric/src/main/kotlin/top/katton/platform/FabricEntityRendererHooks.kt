@file:Suppress("unused")

package top.katton.platform

import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object FabricEntityRendererHooks : EntityRendererHooks {

    init { EntityRendererHooks.setInstance(this) }

    private val LOGGER = LoggerFactory.getLogger(FabricEntityRendererHooks::class.java)
    @Volatile var capturedContext: EntityRendererProvider.Context? = null
    private val kattonRenderers = ConcurrentHashMap<EntityType<*>, EntityRenderer<*, *>>()
    private val rendererByStateClass = ConcurrentHashMap<Class<*>, EntityRenderer<*, *>>()
    private val bakedModelParts = ConcurrentHashMap<ModelLayerLocation, ModelPart>()

    fun getKattonRenderer(type: EntityType<*>): EntityRenderer<*, *>? = kattonRenderers[type]

    fun findKattonRendererByState(state: EntityRenderState): EntityRenderer<*, *>? {
        return rendererByStateClass[state.javaClass]
    }

    override fun <T : Entity> registerRenderer(entityType: EntityType<T>, factory: EntityRendererProvider<T>) {
        try {
            val context = capturedContext
                ?: throw IllegalStateException("EntityRendererProvider.Context not yet captured.")
            @Suppress("UNCHECKED_CAST")
            val renderer = factory.create(context) as EntityRenderer<*, *>
            top.katton.engine.ManagedResources.put(kattonRenderers, entityType, renderer, exclusive = true)
            // Store state class for getRenderer(EntityRenderState) lookup (MC 26.1+)
            @Suppress("UNCHECKED_CAST")
            val state = (renderer as EntityRenderer<Entity, EntityRenderState>).createRenderState()
            top.katton.engine.ManagedResources.put(rendererByStateClass, state.javaClass, renderer, exclusive = true)
        } catch (e: Exception) {
            LOGGER.error("Failed to register entity renderer for {}", entityType, e)
            throw e
        }
    }

    override fun unregisterRenderer(entityType: EntityType<*>): Boolean {
        val removed = kattonRenderers.remove(entityType)
        if (removed != null) {
            rendererByStateClass.entries.removeAll { it.value == removed }
        }
        return removed != null
    }

    override fun registerModelLayer(layer: ModelLayerLocation, definition: () -> LayerDefinition) {
        try { top.katton.engine.ManagedResources.put(bakedModelParts, layer, definition().bakeRoot(), exclusive = true) }
        catch (e: Exception) { LOGGER.error("Failed to register model layer {}", layer, e); throw e }
    }

    override fun unregisterModelLayer(layer: ModelLayerLocation): Boolean {
        bakedModelParts.remove(layer); return false
    }

    override fun getBakedModelPart(layer: ModelLayerLocation): ModelPart? = bakedModelParts[layer]

    override fun flushPending() {}
}
