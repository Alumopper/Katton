@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.client.animation.AnimationDefinition
import net.minecraft.client.animation.KeyframeAnimation
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.AnimationState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Monster
import net.minecraft.resources.Identifier
import org.jetbrains.annotations.ApiStatus
import top.katton.bridge.KattonBridge
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

// ═══════════════════════════════════════════════════════════
//  High-level API: registerAnimatedEntityRenderer
// ═══════════════════════════════════════════════════════════

/**
 * Simplified entity renderer registration with animation support.
 *
 * One call handles model layer, renderer construction, and animation wiring.
 * Uses [Monster] as entity type internally to avoid ClassCastException across
 * script reloads. Animation state is shared through [KattonBridge].
 *
 * **Entity side** — publish animation states in `tick()`:
 * ```kotlin
 * KattonBridge["anim:$id:idle"] = idleAnimationState
 * KattonBridge["anim:$id:walk"] = walkAnimationState
 * ```
 *
 * **Client side** — one call:
 * ```kotlin
 * registerAnimatedEntityRenderer<Zombie1RenderState, Zombie1Model<Zombie1RenderState>>(
 *     entityTypeId = "test:zombie1",
 *     modelLayer = Zombie1Model.LAYER_LOCATION,
 *     bodyLayer = { Zombie1Model.createBodyLayer() },
 *     modelFactory = { root -> Zombie1Model(root) },
 *     texture = id("test", "textures/entity/zombie1.png"),
 *     renderStateFactory = { Zombie1RenderState() },
 *     animations = mapOf(
 *         "idle" to Zombie1Animation.idle,
 *         "walk" to Zombie1Animation.walkforward
 *     )
 * )
 * ```
 *
 * **Custom animation logic** — pass an `animate` callback. It receives
 * the model, entity, render state, and a map of pre-baked animations:
 * ```kotlin
 * animate = { model, entity, state, baked ->
 *     model.resetPose()
 *     // your custom logic...
 *     baked["walk"]?.apply(walkAnimState, state.ageInTicks)
 * }
 * ```
 *
 * @param animations map of name → AnimationDefinition. Default logic plays
 *   "walk" when moving and "idle" otherwise. Animation states are read from
 *   KattonBridge["anim:<entityId>:<name>"].
 */
@ApiStatus.Experimental
fun <S : LivingEntityRenderState, M : EntityModel<S>> registerAnimatedEntityRenderer(
    entityTypeId: String,
    modelLayer: ModelLayerLocation,
    bodyLayer: () -> LayerDefinition,
    modelFactory: (ModelPart) -> M,
    texture: Identifier,
    renderStateFactory: () -> S = { @Suppress("UNCHECKED_CAST") (LivingEntityRenderState() as S) },
    shadowRadius: Float = 0.5f,
    animations: Map<String, AnimationDefinition> = emptyMap(),
    animate: ((M, Monster, S, Map<String, KeyframeAnimation>) -> Unit)? = null
) {
    registerEntityModelLayer(modelLayer, bodyLayer)
    val root = getBakedModelPart(modelLayer)

    registerEntityRenderer(entityTypeId) { ctx ->
        val model = modelFactory(root)
        // Pre-bake all animation definitions
        val bakedAnims: Map<String, KeyframeAnimation> = animations.mapValues { (_, def) ->
            def.bake(model.root())
        }

        object : MobRenderer<Monster, S, M>(ctx, model, shadowRadius) {
            override fun createRenderState(): S = renderStateFactory()

            override fun extractRenderState(entity: Monster, state: S, partialTick: Float) {
                super.extractRenderState(entity, state, partialTick)

                if (animate != null) {
                    model.resetPose()
                    animate(model, entity, state, bakedAnims)
                    return
                }

                // Default animation logic
                model.resetPose()
                val eId = entity.id
                val moving = entity.deltaMovement.horizontalDistanceSqr() > 1.0e-7
                val animName = if (moving && bakedAnims.containsKey("walk")) "walk" else "idle"
                val animState = KattonBridge["anim:$eId:$animName"] as? AnimationState ?: return
                bakedAnims[animName]?.apply(animState, state.ageInTicks)
            }

            override fun getTextureLocation(state: S): Identifier = texture
        }
    }
}
