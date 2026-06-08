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
import net.minecraft.world.entity.Mob
import net.minecraft.resources.Identifier
import org.jetbrains.annotations.ApiStatus
import top.katton.bridge.KattonBridge
import top.katton.registry.EntityRendererRegistration
import java.util.concurrent.ConcurrentHashMap

/**
 * %en
 * Registers a custom entity renderer for a script-registered entity type.
 *
 * This is the client-side counterpart to [registerNativeEntity]. After
 * registering an entity type with [registerNativeEntity], call this on the
 * client to give your entity a visual appearance.
 *
 * The [rendererFactory] receives an [EntityRendererProvider.Context], which
 * provides access to the entity render dispatcher, item renderer, resource
 * manager, and entity model set - everything you need to construct a standard
 * [EntityRenderer].
 *
 * %zh
 * 为脚本注册的实体类型注册自定义实体渲染器。
 * 这是 [registerNativeEntity] 在客户端侧的配套 API。完成实体类型注册后，
 * 在客户端调用这里的方法，就能为实体提供可视化外观。
 * [rendererFactory] 会接收一个 [EntityRendererProvider.Context]，其中可访问
 * 实体渲染分发器、物品渲染器、资源管理器和实体模型集，足以构造标准 [EntityRenderer]。
 * @param entityType
 * %en the entity type (obtained from [registerNativeEntity]'s return value)
 * %zh 实体类型，来源于 [registerNativeEntity] 的返回值。
 * @param rendererFactory
 * %en factory that creates the [EntityRenderer] instance
 * %zh 创建 [EntityRenderer] 实例的工厂函数。
 * @example
 * %en
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
 * %zh 示例代码见英文部分。
 */
@ApiStatus.Experimental
fun <T : Entity> registerEntityRenderer(
    entityType: EntityType<T>,
    rendererFactory: EntityRendererProvider<T>
) {
    EntityRendererRegistration.register(entityType, rendererFactory)
}

/**
 * %en
 * Registers a custom entity renderer by entity type ID.
 *
 * Convenience overload that resolves the [EntityType] from the built-in
 * registry using the given [entityTypeId].
 *
 * %zh
 * 通过实体类型 ID 注册自定义实体渲染器。
 * 这是一个便捷重载，会根据给定的 [entityTypeId] 从内置注册表中解析 [EntityType]。
 * @param entityTypeId
 * %en the entity type identifier (e.g., `"mymod:ghost"`)
 * %zh 实体类型标识符，例如 "mymod:ghost"。
 * @param rendererFactory
 * %en factory that creates the [EntityRenderer] instance
 * %zh 创建 [EntityRenderer] 实例的工厂函数。
 * @throws IllegalStateException
 * %en if the entity type is not registered
 * %zh 当实体类型未注册时抛出。
 */
@ApiStatus.Experimental
fun <T : Entity> registerEntityRenderer(
    entityTypeId: String,
    rendererFactory: EntityRendererProvider<T>
) {
    val id = Identifier.parse(entityTypeId)
    EntityRendererRegistration.register(id, rendererFactory)
}

/**
 * %en
 * Registers a [ModelLayerLocation] and its [LayerDefinition] for entity model rendering.
 *
 * In Minecraft 1.21.11+, [net.minecraft.client.model.geom.EntityModelSet] uses an
 * ImmutableMap internally, so model layers registered this way cannot be resolved
 * via `context.bakeLayer()`. Instead, use [getBakedModelPart] to retrieve the
 * pre-baked [ModelPart] and pass it directly to your model constructor.
 *
 * %zh
 * 为实体模型渲染注册 [ModelLayerLocation] 及其对应的 [LayerDefinition]。
 * 在 Minecraft 1.21.11+ 中，[net.minecraft.client.model.geom.EntityModelSet] 内部使用
 * ImmutableMap，因此通过这种方式注册的模型层无法再用 `context.bakeLayer()` 解析。
 * 请改用 [getBakedModelPart] 获取预烘焙的 [ModelPart]，并直接传给模型构造器。
 * @param layer
 * %en the model layer location (e.g., `ModelLayerLocation(id("mymod:ghost"), "main")`)
 * %zh 模型层位置，例如 `ModelLayerLocation(id("mymod:ghost"), "main")`。
 * @param definition
 * %en factory that creates the layer definition
 * %zh 创建层定义的工厂函数。
 * @example
 * %en
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
 * %zh 示例代码见英文部分。
 */
@ApiStatus.Experimental
fun registerEntityModelLayer(
    layer: ModelLayerLocation,
    definition: () -> LayerDefinition
) {
    EntityRendererRegistration.registerModelLayer(layer, definition)
}

/**
 * %en
 * Returns the pre-baked [ModelPart] for a model layer registered with
 * [registerEntityModelLayer].
 *
 * Since Minecraft 1.21.11+ uses an ImmutableMap for model layers,
 * `context.bakeLayer()` cannot resolve dynamically registered layers.
 * Use this function instead to obtain the baked root directly.
 *
 * %zh
 * 返回通过 [registerEntityModelLayer] 注册的模型层所对应的预烘焙 [ModelPart]。
 * 由于 Minecraft 1.21.11+ 的模型层使用 ImmutableMap 存储，`context.bakeLayer()`
 * 无法解析动态注册的层，因此应改用这个方法直接获取烘焙后的根节点。
 */
@ApiStatus.Experimental
fun getBakedModelPart(layer: ModelLayerLocation): ModelPart {
    return EntityRendererRegistration.getBakedModelPart(layer)
}

// Keyframe callback system
/**
 * %en
 * A time-stamped callback that fires at a specific point during an entity animation.
 *
 * Created per-entity-type in [registerAnimatedEntityRenderer]'s [keyframeEvents] list.
 * The callback receives the animated model, entity, render state, and pre-baked animations
 * use [top.katton.api.createBoneExecution] to get an ExecutionContext at a bone position.
 *
 * %zh
 * 一个带时间戳的回调，会在实体动画的特定时刻触发。
 * 它按实体类型创建，并放在 [registerAnimatedEntityRenderer] 的 [keyframeEvents] 列表中。
 * 回调会接收动画模型、实体、渲染状态和预烘焙动画；如需在骨骼位置获取 ExecutionContext，
 * 可使用 [top.katton.api.createBoneExecution]。
 * @property animName
 * %en the animation name matching a key in [registerAnimatedEntityRenderer.animations]
 * %zh 与 [registerAnimatedEntityRenderer.animations] 中某个键对应的动画名称。
 * @property timeSeconds
 * %en time in seconds from animation start when the callback should fire
 * %zh 从动画开始算起，回调触发的时间点（秒）。
 * @property action
 * %en the callback (entity, model, state, baked animations)
 * %zh 回调函数（实体、模型、状态、预烘焙动画）。
 */
data class KeyframeEvent(
    val animName: String,
    val timeSeconds: Float,
    val action: (entity: Mob, model: EntityModel<*>, state: LivingEntityRenderState, bakedAnims: Map<String, KeyframeAnimation>) -> Unit
)

/**
 * %en
 * Tracks per-entity animation timing so that each [KeyframeEvent] fires
 * exactly once per animation playback cycle.
 *
 * Internally records the [LivingEntityRenderState.ageInTicks] when an animation
 * starts playing, then computes elapsed time as `(currentAge - startAge) / 20f`.
 * When the animation stops, all tracking state for that entity+animation is cleared.
 *
 * %zh
 * 跟踪每个实体的动画时间，确保每个 [KeyframeEvent] 在一次播放周期内只触发一次。
 * 它会记录动画开始播放时的 [LivingEntityRenderState.ageInTicks]，
 * 然后按 `(currentAge - startAge) / 20f` 计算经过的秒数。
 * 动画停止时，会清除该实体与该动画组合的所有跟踪状态。
 */
private class KeyframeTracker {
    /**
 * %en
 *  "entityId:animName" -> ageInTicks when the animation started playing
 *
 * %zh
 *  "entityId:animName" -> 动画开始播放时的 ageInTicks。
 */
    private val startAges = ConcurrentHashMap<String, Float>()
    /**
 * %en
 *  "entityId:animName" -> set of timeSeconds already fired this cycle
 *
 * %zh
 *  "entityId:animName" -> 本周期已触发的 timeSeconds 集合。
 */
    private val firedKeys = ConcurrentHashMap<String, MutableSet<Float>>()

    fun isPlaying(entityId: Int, animName: String): Boolean =
        startAges.containsKey("$entityId:$animName")

    fun reset(entityId: Int, animName: String) {
        val key = "$entityId:$animName"
        startAges.remove(key)
        firedKeys.remove(key)
    }

    /**
 * %en
 * Returns `true` if the keyframe at [timeSeconds] has not yet fired for this
 * (entity, animation) pair and the animation has reached that time.
 *
 * %zh
 * 如果该（实体，动画）组合在 [timeSeconds] 这个时间点还没有触发过关键帧，
 * 且动画已经推进到该时间，则返回 `true`。
 */
    fun shouldFire(entityId: Int, animName: String, timeSeconds: Float, ageInTicks: Float): Boolean {
        val key = "$entityId:$animName"
        val startAge = startAges.getOrPut(key) { ageInTicks }
        val elapsedSeconds = (ageInTicks - startAge) / 20f

        if (elapsedSeconds < timeSeconds) return false

        val fired = firedKeys.getOrPut(key) { mutableSetOf() }
        return fired.add(timeSeconds)
    }
}

// High-level API: registerAnimatedEntityRenderer
/**
 * %en
 * Simplified entity renderer registration with animation support.
 *
 * One call handles model layer, renderer construction, and animation wiring.
 * Uses [Mob] as entity type internally to avoid ClassCastException across
 * script reloads. Animation state is shared through [KattonBridge].
 *
 * **Entity side** - publish animation states in `tick()`:
 * ```kotlin
 * KattonBridge["anim:$id:idle"] = idleAnimationState
 * KattonBridge["anim:$id:walk"] = walkAnimationState
 * ```
 *
 * **Client side** - one call:
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
 * **Custom animation logic** - pass an `animate` callback. It receives
 * the model, entity, render state, and a map of pre-baked animations:
 * ```kotlin
 * animate = { model, entity, state, baked ->
 *     model.resetPose()
 *     // your custom logic...
 *     baked["walk"]?.apply(walkAnimState, state.ageInTicks)
 * }
 * ```
 *
 * %zh
 * 简化的实体渲染器注册接口，支持动画。
 * 一次调用即可完成模型层、渲染器构造和动画绑定。
 * 内部使用 Mob 作为实体类型，避免脚本重载期间出现 ClassCastException。
 * 动画状态通过 [KattonBridge] 共享。
 *
 * **实体侧** - 在 `tick()` 中发布动画状态：
 * ```kotlin
 * KattonBridge["anim:$id:idle"] = idleAnimationState
 * KattonBridge["anim:$id:walk"] = walkAnimationState
 * ```
 *
 * **客户端侧** - 一次调用即可：
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
 * **自定义动画逻辑** - 传入 `animate` 回调即可。它会接收模型、实体、渲染状态
 * 以及预烘焙动画映射：
 * ```kotlin
 * animate = { model, entity, state, baked ->
 *     model.resetPose()
 *     // your custom logic...
 *     baked["walk"]?.apply(walkAnimState, state.ageInTicks)
 * }
 * ```
 * @param animations
 * %en map of name -> AnimationDefinition. Default logic plays
 * %zh 名称到 AnimationDefinition 的映射。默认逻辑会在移动时播放 "walk"，否则播放 "idle"。
 * %en
 *   "walk" when moving and "idle" otherwise. Animation states are read from
 *   KattonBridge["anim:<entityId>:<name>"].
 *
 * %zh
 * 名称到 AnimationDefinition 的映射。默认逻辑会在移动时播放 "walk"，否则播放 "idle"。
 * 动画状态从 `KattonBridge["anim:<entityId>:<name>"]` 中读取。
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
    animate: ((M, Mob, S, Map<String, KeyframeAnimation>) -> Unit)? = null,
    keyframeEvents: List<KeyframeEvent> = emptyList()
) {
    registerEntityModelLayer(modelLayer, bodyLayer)
    val root = getBakedModelPart(modelLayer)

    registerEntityRenderer(entityTypeId) { ctx ->
        val model = modelFactory(root)
        val bakedAnims: Map<String, KeyframeAnimation> = animations.mapValues { (_, def) ->
            def.bake(model.root())
        }
        val tracker = KeyframeTracker()

        object : MobRenderer<Mob, S, M>(ctx, model, shadowRadius) {
            override fun createRenderState(): S = renderStateFactory()

            override fun extractRenderState(entity: Mob, state: S, partialTick: Float) {
                super.extractRenderState(entity, state, partialTick)
                model.resetPose()

                if (animate != null) {
                    animate(model, entity, state, bakedAnims)
                } else {
                    // Default animation logic
                    val eId = entity.id
                    val moving = entity.deltaMovement.horizontalDistanceSqr() > 1.0e-7
                    val animName = if (moving && bakedAnims.containsKey("walk")) "walk" else "idle"
                    val animState = KattonBridge["anim:$eId:$animName"] as? AnimationState
                    if (animState != null) {
                        bakedAnims[animName]?.apply(animState, state.ageInTicks)
                    }
                }

                // Fire keyframe callbacks for registered events (after animation apply)
                for (event in keyframeEvents) {
                    val animState = KattonBridge["anim:${entity.id}:${event.animName}"] as? AnimationState
                        ?: continue
                    if (!animState.isStarted) {
                        tracker.reset(entity.id, event.animName)
                        continue
                    }
                    if (tracker.shouldFire(entity.id, event.animName, event.timeSeconds, state.ageInTicks)) {
                        event.action(entity, model, state, bakedAnims)
                    }
                }
            }

            override fun getTextureLocation(state: S): Identifier = texture
        }
    }
}
