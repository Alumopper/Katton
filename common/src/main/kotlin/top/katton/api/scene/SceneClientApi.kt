@file:Suppress("unused")

package top.katton.api.scene

import top.katton.client.scene.ClientSceneManager
import top.katton.util.ScriptExecutionContext

/**
 * %en
 * Register a client definition, owned by the current script. Version defaults to the script pack's code hash.
 * %zh
 * 注册归属于当前脚本的客户端定义；版本默认使用脚本包代码哈希。
 */
fun registerClientScene(
    id: String,
    revision: String = ScriptExecutionContext.currentScriptRevision() ?: "1",
    block: SceneBuilder.() -> Unit,
) = ClientSceneManager.register(id, revision, clientScene(block))

/**
 * %en
 * Stop instances and unregister this definition.
 * %zh
 * 停止此定义的实例并注销定义。
 */
fun unregisterClientScene(id: String): Boolean = ClientSceneManager.unregister(id)

/**
 * %en
 * Play locally on the client thread.
 * %zh
 * 在客户端线程本地播放。
 */
fun playClientScene(id: String, context: SceneContext = SceneContext()): SceneHandle =
    ClientSceneManager.play(id, context)

/**
 * %en
 * Every standalone effect uses the same ownership and cancellation rules as scenes.
 * %zh
 * 独立效果与演出共用归属和取消规则。
 */
fun playClientEffect(effect: SceneEffect, context: SceneContext = SceneContext()): EffectHandle =
    ClientSceneManager.play(clientScene { effect(effect) }, context)

/**
 * %en
 * Play a world-space camera path; keyframe times are ticks and angles are degrees.
 * %zh
 * 播放世界坐标镜头路径；关键帧时间使用 tick，角度使用度。
 */
fun playCameraPath(
    keyframes: List<CameraKeyframe>,
    options: CameraOptions = CameraOptions(),
): EffectHandle = playClientEffect(CameraPath(keyframes, options))

/**
 * %en
 * Follow an anchor until the duration expires or the target becomes unavailable.
 * %zh
 * 跟随锚点，直到时长结束或目标失效。
 */
fun followCamera(
    anchor: EffectAnchor,
    durationTicks: Int,
    options: CameraOptions = CameraOptions(),
    context: SceneContext = SceneContext(),
): EffectHandle = playClientEffect(CameraFollow(anchor, durationTicks, options), context)

/**
 * %en
 * Add a decaying angular shake without locking player input.
 * %zh
 * 叠加随时间衰减的角度震动，不锁定玩家输入。
 */
fun shakeCamera(
    durationTicks: Int = 10,
    strength: Float = 2f,
    frequency: Float = 0.7f,
): EffectHandle = playClientEffect(CameraShake(durationTicks, strength, frequency))

/**
 * %en
 * Interpolate the rendered FOV in degrees without changing saved video options.
 * %zh
 * 以度为单位插值渲染 FOV，不修改持久化视频设置。
 */
fun transitionCameraFov(
    from: Float,
    to: Float,
    durationTicks: Int,
    easing: SceneEasing = SceneEasing.SMOOTH,
): EffectHandle = playClientEffect(CameraFov(from, to, durationTicks, easing))

/**
 * %en
 * Set shared client limits for active effects, emitted particles, and geometry vertices.
 * %zh
 * 设置客户端共享的活动效果、粒子发射和几何顶点预算。
 */
fun setClientEffectBudgets(budgets: EffectBudgets) = ClientSceneManager.setBudgets(budgets)
