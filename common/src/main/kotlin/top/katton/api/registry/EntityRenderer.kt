@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.resources.Identifier
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.EntityRendererRegistration

/**
 * Registers a custom entity renderer for a script-registered entity type.
 *
 * This is the client-side counterpart to [registerNativeEntity]. After
 * registering an entity type with [registerNativeEntity], call this on the
 * client to give your entity a visual appearance.
 *
 * The [rendererFactory] receives an [EntityRendererProvider.Context], which
 * provides access to the entity render dispatcher, item renderer, resource
 * manager, and entity model set — everything you need to construct a standard
 * [EntityRenderer].
 *
 * @param entityType the entity type (obtained from [registerNativeEntity]'s return value)
 * @param rendererFactory factory that creates the [EntityRenderer] instance
 *
 * @example
 * ```kotlin
 * // First register the entity type
 * val entry = registerNativeEntity("mymod:ghost") { ... }
 *
 * // Then register its renderer on the client
 * @ClientScriptEntrypoint
 * fun initClient() {
 *     registerEntityRenderer(entry.entityType) { context ->
 *         GhostRenderer(context)
 *     }
 * }
 * ```
 */
@ApiStatus.Experimental
fun <T : Entity> registerEntityRenderer(
    entityType: EntityType<T>,
    rendererFactory: EntityRendererProvider<T>
) {
    EntityRendererRegistration.register(entityType, rendererFactory)
}

/**
 * Registers a custom entity renderer by entity type ID.
 *
 * Convenience overload that resolves the [EntityType] from the built-in
 * registry using the given [entityTypeId].
 *
 * @param entityTypeId the entity type identifier (e.g., `"mymod:ghost"`)
 * @param rendererFactory factory that creates the [EntityRenderer] instance
 * @throws IllegalStateException if the entity type is not registered
 */
@ApiStatus.Experimental
fun <T : Entity> registerEntityRenderer(
    entityTypeId: String,
    rendererFactory: EntityRendererProvider<T>
) {
    val id = Identifier.parse(entityTypeId)
    val entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id)
        .orElseThrow { IllegalStateException("Entity type not registered: $entityTypeId") }
    @Suppress("UNCHECKED_CAST")
    registerEntityRenderer(entityType as EntityType<T>, rendererFactory)
}

/**
 * Registers a [ModelLayerLocation] and its [LayerDefinition] for entity model rendering.
 *
 * In Minecraft 1.21.11+, [net.minecraft.client.model.geom.EntityModelSet] uses an
 * ImmutableMap internally, so model layers registered this way cannot be resolved
 * via `context.bakeLayer()`. Instead, use [getBakedModelPart] to retrieve the
 * pre-baked [ModelPart] and pass it directly to your model constructor.
 *
 * @param layer the model layer location (e.g., `ModelLayerLocation(id("mymod:ghost"), "main")`)
 * @param definition factory that creates the layer definition
 *
 * @example
 * ```kotlin
 * @ClientScriptEntrypoint
 * fun initClient() {
 *     val layer = ModelLayerLocation(id("mymod:ghost"), "main")
 *     registerEntityModelLayer(layer) { GhostModel.createBodyLayer() }
 *     registerEntityRenderer("mymod:ghost") { context ->
 *         // Use getBakedModelPart instead of context.bakeLayer():
 *         val root = getBakedModelPart(layer)
 *         GhostRenderer(context, GhostModel(root))
 *     }
 * }
 * ```
 */
@ApiStatus.Experimental
fun registerEntityModelLayer(
    layer: ModelLayerLocation,
    definition: () -> LayerDefinition
) {
    EntityRendererRegistration.registerModelLayer(layer, definition)
}

/**
 * Returns the pre-baked [ModelPart] for a model layer registered with
 * [registerEntityModelLayer].
 *
 * Since Minecraft 1.21.11+ uses an ImmutableMap for model layers,
 * `context.bakeLayer()` cannot resolve dynamically registered layers.
 * Use this function instead to obtain the baked root directly.
 */
@ApiStatus.Experimental
fun getBakedModelPart(layer: ModelLayerLocation): ModelPart {
    return EntityRendererRegistration.getBakedModelPart(layer)
}
