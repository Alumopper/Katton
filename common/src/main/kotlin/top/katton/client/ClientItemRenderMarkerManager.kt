package top.katton.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.logging.LogUtils
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
/*? if mc_26_1_2 {*/
/*import net.minecraft.client.renderer.LevelRenderer*/
/*?}*/
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndLightGetter
/*? if mc_26_2 {*/
import net.minecraft.world.level.LightLayer
/*?}*/
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import top.katton.api.ClientItemRenderAnimation
import top.katton.api.ClientItemRenderAnimationKeyframe
import top.katton.api.ClientItemRenderAnimationMode
import top.katton.api.ClientItemRenderAnimationSet
import top.katton.api.ClientItemRenderAnimationTarget
import top.katton.api.ClientItemRenderFunctionKeyframe
import top.katton.api.ClientItemRenderMarker
import top.katton.network.ClientItemRenderMarkerPacket
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/*? if mc_26_2 {*/
private fun getLightCoords(level: BlockAndLightGetter, pos: BlockPos): Int {
    val block = level.getBrightness(LightLayer.BLOCK, pos)
    val sky = level.getBrightness(LightLayer.SKY, pos)
    return (block shl 4) or (sky shl 20)
}
/*?} else {*/
/*private fun getLightCoords(level: BlockAndLightGetter, pos: BlockPos): Int = LevelRenderer.getLightCoords(level, pos)*/
/*?}*/

object ClientItemRenderMarkerManager {
    private const val FULL_BRIGHT_LIGHT = 0x00F000F0
    private const val MAX_ACTIVE_MARKERS = 4_096
    private const val MAX_ACTIVE_ANIMATION_SETS = 16_384L
    private const val MAX_ACTIVE_ANIMATIONS = 65_536L
    private const val MAX_ACTIVE_KEYFRAMES = 262_144L
    private const val CAPACITY_WARNING_INTERVAL_NANOS = 10_000_000_000L
    private val markers = ConcurrentHashMap<UUID, Entry>()
    private val mc = Minecraft.getInstance()
    private val logger = LogUtils.getLogger()
    private var activeAnimationSets = 0L
    private var activeAnimations = 0L
    private var activeKeyframes = 0L
    private var lastCapacityWarningNanos = 0L

    private data class MarkerComplexity(
        val animationSets: Long,
        val animations: Long,
        val keyframes: Long
    )

    private data class Entry(
        val marker: ClientItemRenderMarker,
        var remainingTicks: Int?,
        val complexity: MarkerComplexity,
        var ageTicks: Int = 0,
        val renderState: ItemStackRenderState = ItemStackRenderState(),
        val playingAnimationIds: MutableSet<String> = LinkedHashSet(marker.playingAnimationID),
        val animationStates: MutableMap<String, AnimationSetState> = HashMap(),
        val functionKeyframeCache: MutableMap<ClientItemRenderAnimationSet, List<ClientItemRenderFunctionKeyframe>> =
            IdentityHashMap(),
        val animationKeyframeCache: MutableMap<ClientItemRenderAnimation, List<ClientItemRenderAnimationKeyframe>> =
            IdentityHashMap()
    )

    private data class AnimationSetState(
        val startAgeTicks: Int = 0,
        var started: Boolean = false,
        var ended: Boolean = false
    )

    @JvmStatic
    fun handlePacket(packet: ClientItemRenderMarkerPacket) {
        when (packet.action) {
            ClientItemRenderMarkerPacket.Action.ADD_OR_UPDATE -> {
                packet.markers.forEach { addOrUpdate(it) }
            }
            ClientItemRenderMarkerPacket.Action.REMOVE -> {
                packet.ids.forEach(::remove)
            }
            ClientItemRenderMarkerPacket.Action.CLEAR -> clear()
            ClientItemRenderMarkerPacket.Action.PLAY_ANIMATION -> {
                packet.ids.forEach { markerId ->
                    packet.animationSetIds.forEach { animationSetId -> playAnimationSet(markerId, animationSetId) }
                }
            }
            ClientItemRenderMarkerPacket.Action.STOP_ANIMATION -> {
                packet.ids.forEach { markerId ->
                    packet.animationSetIds.forEach { animationSetId -> stopAnimationSet(markerId, animationSetId) }
                }
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun addOrUpdate(marker: ClientItemRenderMarker) {
        if (marker.stack.isEmpty) {
            remove(marker.id)
            return
        }
        val complexity = markerComplexity(marker)
        val previous = markers[marker.id]
        val markerCount = markers.size + if (previous == null) 1 else 0
        val nextSets = activeAnimationSets - (previous?.complexity?.animationSets ?: 0L) + complexity.animationSets
        val nextAnimations = activeAnimations - (previous?.complexity?.animations ?: 0L) + complexity.animations
        val nextKeyframes = activeKeyframes - (previous?.complexity?.keyframes ?: 0L) + complexity.keyframes
        if (markerCount > MAX_ACTIVE_MARKERS ||
            nextSets > MAX_ACTIVE_ANIMATION_SETS ||
            nextAnimations > MAX_ACTIVE_ANIMATIONS ||
            nextKeyframes > MAX_ACTIVE_KEYFRAMES
        ) {
            warnCapacityExceeded()
            return
        }
        val ttl = marker.lifetimeTicks.takeIf { it > 0 }
        markers[marker.id] = Entry(marker.copy(stack = marker.stack.copy()), ttl, complexity)
        activeAnimationSets = nextSets
        activeAnimations = nextAnimations
        activeKeyframes = nextKeyframes
    }

    @JvmStatic
    @Synchronized
    fun remove(id: UUID) {
        markers.remove(id)?.let(::releaseComplexity)
    }

    @JvmStatic
    @Synchronized
    fun clear() {
        markers.clear()
        activeAnimationSets = 0L
        activeAnimations = 0L
        activeKeyframes = 0L
    }

    @JvmStatic
    fun playAnimationSet(markerId: UUID, animationSetId: String) {
        val entry = markers[markerId] ?: return
        if (!entry.marker.animations.containsKey(animationSetId)) return
        entry.playingAnimationIds.add(animationSetId)
        entry.animationStates[animationSetId] = AnimationSetState(startAgeTicks = entry.ageTicks)
    }

    @JvmStatic
    fun stopAnimationSet(markerId: UUID, animationSetId: String) {
        val entry = markers[markerId] ?: return
        entry.playingAnimationIds.remove(animationSetId)
        entry.animationStates.remove(animationSetId)
    }

    @JvmStatic
    @Synchronized
    fun tick() {
        val iterator = markers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val remaining = entry.remainingTicks
            if (remaining != null && remaining <= 1) {
                iterator.remove()
                releaseComplexity(entry)
                continue
            }

            val previousAge = entry.ageTicks
            entry.ageTicks++
            tickAnimationEvents(entry, previousAge.toFloat(), entry.ageTicks.toFloat())
            if (remaining != null) {
                entry.remainingTicks = remaining - 1
            }
        }
    }

    @JvmStatic
    fun render(camera: CameraRenderState?, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector, tickDelta: Float) {
        if (markers.isEmpty()) return
        val level = mc.level ?: return
        val cameraState = camera ?: return
        val camPos = cameraState.pos
        val currentDimension = level.dimension()

        for (entry in markers.values) {
            val marker = entry.marker
            if (marker.level != currentDimension || marker.scale <= 0.0f || marker.stack.isEmpty) {
                continue
            }

            val animationTransform = sampleAnimations(entry, entry.ageTicks + tickDelta)
            if (!animationTransform.isFinite()) continue
            val animatedPos = marker.pos.add(animationTransform.translation)
            if (!animatedPos.isFiniteVector()) continue
            // Two finite world coordinates can still overflow when subtracted (for example,
            // +Double.MAX_VALUE - -Double.MAX_VALUE). Never pass that result into PoseStack.
            val renderOffset = animatedPos.subtract(camPos)
            if (!renderOffset.isFiniteVector()) continue

            val distanceLimit = marker.maxDistance
            if (distanceLimit > 0.0 && animatedPos.distanceToSqr(camPos) > distanceLimit * distanceLimit) {
                continue
            }

            val maxScale = maxOf(animationTransform.scale.x, animationTransform.scale.y, animationTransform.scale.z).coerceAtLeast(0.001)
            val rawCullSize = marker.scale.toDouble() * maxScale
            if (!rawCullSize.isFinite()) continue
            val cullSize = rawCullSize.coerceAtLeast(0.25)
            val bounds = AABB.ofSize(animatedPos, cullSize, cullSize, cullSize)
            if (!bounds.minX.isFinite() || !bounds.minY.isFinite() || !bounds.minZ.isFinite() ||
                !bounds.maxX.isFinite() || !bounds.maxY.isFinite() || !bounds.maxZ.isFinite()
            ) continue
            if (!cameraState.cullFrustum.isVisible(bounds)) {
                continue
            }

            val renderYaw = (marker.yaw + animationTransform.rotation.y).toFloat()
            val renderPitch = (marker.pitch + animationTransform.rotation.x).toFloat()
            val renderRoll = (marker.roll + animationTransform.rotation.z).toFloat()
            val renderScaleX = (marker.scale * animationTransform.scale.x).toFloat()
            val renderScaleY = (marker.scale * animationTransform.scale.y).toFloat()
            val renderScaleZ = (marker.scale * animationTransform.scale.z).toFloat()
            if (!renderYaw.isFinite() || !renderPitch.isFinite() || !renderRoll.isFinite() ||
                !renderScaleX.isFinite() || !renderScaleY.isFinite() || !renderScaleZ.isFinite()
            ) continue

            val light = if (marker.fullBright) {
                FULL_BRIGHT_LIGHT
            } else {
                getLightCoords(level, BlockPos.containing(marker.pos))
            }

            poseStack.pushPose()
            try {
                poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z)
                poseStack.mulPose(Axis.YP.rotationDegrees(renderYaw))
                poseStack.mulPose(Axis.XP.rotationDegrees(renderPitch))
                poseStack.mulPose(Axis.ZP.rotationDegrees(renderRoll))
                poseStack.scale(
                    renderScaleX.coerceAtLeast(0.001f),
                    renderScaleY.coerceAtLeast(0.001f),
                    renderScaleZ.coerceAtLeast(0.001f)
                )

                entry.renderState.clear()
                mc.itemModelResolver.updateForTopItem(
                    entry.renderState,
                    marker.stack,
                    marker.displayContext,
                    level,
                    null,
                    marker.id.hashCode()
                )
                entry.renderState.submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, 0)
            } finally {
                poseStack.popPose()
            }
        }
    }

    private fun tickAnimationEvents(entry: Entry, previousAge: Float, currentAge: Float) {
        val marker = entry.marker
        val playingEntries = getPlayingAnimationEntries(entry)
        val validPlayingIds = playingEntries.mapTo(HashSet()) { it.first }
        entry.playingAnimationIds.retainAll(validPlayingIds)
        entry.animationStates.keys.retainAll(validPlayingIds)

        for ((id, animationSet) in playingEntries) {
            val state = entry.animationStates.getOrPut(id) { AnimationSetState() }
            tickAnimationSetEvents(
                marker = marker,
                entry = entry,
                animationSet = animationSet,
                state = state,
                previousAge = previousAge - state.startAgeTicks,
                currentAge = currentAge - state.startAgeTicks
            )
        }
    }

    private fun getPlayingAnimationEntries(entry: Entry): List<Pair<String, ClientItemRenderAnimationSet>> {
        return entry.playingAnimationIds.mapNotNull { id ->
            entry.marker.animations[id]?.let { id to it }
        }
    }

    private fun tickAnimationSetEvents(
        marker: ClientItemRenderMarker,
        entry: Entry,
        animationSet: ClientItemRenderAnimationSet,
        state: AnimationSetState,
        previousAge: Float,
        currentAge: Float
    ) {
        if (animationSet.durationTicks <= 0) return

        val previousLocalAge = previousAge - animationSet.delayTicks
        val currentLocalAge = currentAge - animationSet.delayTicks
        if (currentLocalAge < 0.0f) return

        val wasStarted = state.started
        if (!state.started) {
            state.started = true
            invokeAnimationCallback(marker, animationSet.onStart)
        }

        val eventPreviousLocalAge = if (wasStarted) previousLocalAge else -0.0001f
        triggerFunctionKeyframes(marker, entry, animationSet, eventPreviousLocalAge, currentLocalAge)

        val duration = animationSet.durationTicks.toFloat()
        if (!animationSet.loop && !state.ended && previousLocalAge < duration && currentLocalAge >= duration) {
            state.ended = true
            invokeAnimationCallback(marker, animationSet.onEnd)
        }
    }

    private fun triggerFunctionKeyframes(
        marker: ClientItemRenderMarker,
        entry: Entry,
        animationSet: ClientItemRenderAnimationSet,
        previousLocalAge: Float,
        currentLocalAge: Float
    ) {
        val duration = animationSet.durationTicks.toFloat()
        val keyframes = entry.functionKeyframeCache.getOrPut(animationSet) {
            animationSet.animations
                .flatMap { it.keyframes }
                .filterIsInstance<ClientItemRenderFunctionKeyframe>()
                .sortedBy { normalizeKeyframeTime(it.time) }
        }
        if (keyframes.isEmpty()) return

        if (!animationSet.loop) {
            val clampedCurrentAge = currentLocalAge.coerceAtMost(duration)
            for (keyframe in keyframes) {
                val keyframeTick = normalizeKeyframeTime(keyframe.time) * duration
                if (previousLocalAge < keyframeTick && clampedCurrentAge >= keyframeTick) {
                    invokeFunctionKeyframe(marker, keyframe)
                }
            }
            return
        }

        val startCycle = floor(previousLocalAge.coerceAtLeast(0.0f) / duration).toInt()
        val endCycle = floor(currentLocalAge / duration).toInt()
        for (cycle in startCycle..endCycle) {
            val cycleOffset = cycle * duration
            for (keyframe in keyframes) {
                val keyframeTick = cycleOffset + normalizeKeyframeTime(keyframe.time) * duration
                if (previousLocalAge < keyframeTick && currentLocalAge >= keyframeTick) {
                    invokeFunctionKeyframe(marker, keyframe)
                }
            }
        }
    }

    private fun invokeAnimationCallback(marker: ClientItemRenderMarker, callback: ((ClientItemRenderMarker) -> Unit)?) {
        if (callback == null) return
        runCatching { callback(marker) }
            .onFailure { logger.warn("Client item render animation callback failed", it) }
    }

    private fun invokeFunctionKeyframe(marker: ClientItemRenderMarker, keyframe: ClientItemRenderFunctionKeyframe) {
        runCatching { keyframe.function(marker) }
            .onFailure { logger.warn("Client item render function keyframe failed", it) }
    }

    private data class AnimationTransform(
        val translation: Vec3 = Vec3.ZERO,
        val rotation: Vec3 = Vec3.ZERO,
        val scale: Vec3 = Vec3(1.0, 1.0, 1.0)
    ) {
        fun isFinite(): Boolean =
            translation.isFiniteVector() && rotation.isFiniteVector() && scale.isFiniteVector()
    }

    private fun Vec3.isFiniteVector(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    private fun markerComplexity(marker: ClientItemRenderMarker): MarkerComplexity {
        var animations = 0L
        var keyframes = 0L
        marker.animations.values.forEach { animationSet ->
            animations += animationSet.animations.size
            animationSet.animations.forEach { animation -> keyframes += animation.keyframes.size }
        }
        return MarkerComplexity(marker.animations.size.toLong(), animations, keyframes)
    }

    private fun releaseComplexity(entry: Entry) {
        activeAnimationSets = (activeAnimationSets - entry.complexity.animationSets).coerceAtLeast(0L)
        activeAnimations = (activeAnimations - entry.complexity.animations).coerceAtLeast(0L)
        activeKeyframes = (activeKeyframes - entry.complexity.keyframes).coerceAtLeast(0L)
    }

    private fun warnCapacityExceeded() {
        val now = System.nanoTime()
        if (lastCapacityWarningNanos == 0L || now - lastCapacityWarningNanos >= CAPACITY_WARNING_INTERVAL_NANOS) {
            lastCapacityWarningNanos = now
            logger.warn(
                "Ignoring client item-render marker because the active marker/animation safety budget is exhausted"
            )
        }
    }

    private fun sampleAnimations(entry: Entry, ageTicks: Float): AnimationTransform {
        var translation = Vec3.ZERO
        var rotation = Vec3.ZERO
        var scale = Vec3(1.0, 1.0, 1.0)

        for ((animationSetId, animationSet) in getPlayingAnimationEntries(entry)) {
            val state = entry.animationStates.getOrPut(animationSetId) { AnimationSetState() }
            val progress = sampleAnimationSetProgress(animationSet, ageTicks - state.startAgeTicks) ?: continue
            for (animation in animationSet.animations) {
                val sampled = sampleAnimation(entry, animation, progress) ?: continue
                when (animation.target) {
                    ClientItemRenderAnimationTarget.TRANSLATE -> translation = translation.add(sampled)
                    ClientItemRenderAnimationTarget.ROTATE -> rotation = rotation.add(sampled)
                    ClientItemRenderAnimationTarget.SCALE -> scale = scale.multiply(sampled)
                }
            }
        }

        return AnimationTransform(translation, rotation, scale)
    }

    private fun sampleAnimationSetProgress(animationSet: ClientItemRenderAnimationSet, ageTicks: Float): Float? {
        if (animationSet.durationTicks <= 0) return null
        val localAge = ageTicks - animationSet.delayTicks
        if (localAge < 0.0f) return null

        val duration = animationSet.durationTicks.toFloat()
        if (!animationSet.loop && localAge > duration) {
            return 1.0f
        }

        return if (animationSet.loop) {
            (localAge % duration) / duration
        } else {
            (localAge / duration).coerceIn(0.0f, 1.0f)
        }
    }

    private fun sampleAnimation(entry: Entry, animation: ClientItemRenderAnimation, progress: Float): Vec3? {
        val frames = entry.animationKeyframeCache.getOrPut(animation) {
            animation.keyframes
                .filterIsInstance<ClientItemRenderAnimationKeyframe>()
                .sortedBy { it.time }
        }
        if (frames.isEmpty()) return null
        return normalizeAnimationValue(animation, sampleKeyframes(animation, frames, progress), frames)
    }

    private fun normalizeAnimationValue(
        animation: ClientItemRenderAnimation,
        value: Vec3,
        frames: List<ClientItemRenderAnimationKeyframe>
    ): Vec3 {
        val first = frames.firstOrNull()?.value ?: Vec3.ZERO
        return when (animation.target) {
            ClientItemRenderAnimationTarget.SCALE -> {
                if (animation.mode == ClientItemRenderAnimationMode.RELATIVE) {
                    Vec3(
                        1.0 + value.x - first.x,
                        1.0 + value.y - first.y,
                        1.0 + value.z - first.z
                    )
                } else {
                    value
                }
            }
            else -> {
                if (animation.mode == ClientItemRenderAnimationMode.RELATIVE) {
                    value.subtract(first)
                } else {
                    value
                }
            }
        }
    }

    private fun sampleKeyframes(
        animation: ClientItemRenderAnimation,
        frames: List<ClientItemRenderAnimationKeyframe>,
        progress: Float
    ): Vec3 {
        val first = frames.first()
        val last = frames.last()
        val normalizedProgress = normalizeKeyframeTime(progress)
        if (normalizedProgress <= normalizeKeyframeTime(first.time)) return first.value
        if (normalizedProgress >= normalizeKeyframeTime(last.time)) return last.value

        for (index in 0 until frames.lastIndex) {
            val from = frames[index]
            val to = frames[index + 1]
            val fromTime = normalizeKeyframeTime(from.time)
            val toTime = normalizeKeyframeTime(to.time)
            if (normalizedProgress in fromTime..toTime) {
                val span = (toTime - fromTime).coerceAtLeast(0.0001f)
                val segmentProgress = ((normalizedProgress - fromTime) / span).coerceIn(0.0f, 1.0f)
                val eased = to.easing.apply(segmentProgress).toDouble()
                return lerp(from.value, to.value, eased)
            }
        }
        return last.value
    }

    private fun normalizeKeyframeTime(time: Float): Float {
        return if (time > 1.0f) (time / 100.0f).coerceIn(0.0f, 1.0f) else time.coerceIn(0.0f, 1.0f)
    }

    private fun lerp(from: Vec3, to: Vec3, progress: Double): Vec3 = Vec3(
        from.x + (to.x - from.x) * progress,
        from.y + (to.y - from.y) * progress,
        from.z + (to.z - from.z) * progress
    )
}
