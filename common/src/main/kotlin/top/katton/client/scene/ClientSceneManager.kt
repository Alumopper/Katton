package top.katton.client.scene

import com.mojang.logging.LogUtils
import java.util.UUID
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import top.katton.api.scene.*
import top.katton.compat.SceneRenderCompat
import top.katton.network.ClientScenePacket
import top.katton.network.SceneRequestGate
import top.katton.pack.ScriptPackScope
import top.katton.scene.*

/** Physical-client adapter; only client entrypoints and client mixins initialize it. */
object ClientSceneManager {
    private val logger = LogUtils.getLogger()
    private val mc
        get() = Minecraft.getInstance()

    private data class Definition(
        val revision: String,
        val scene: SceneDefinition,
        val owner: SceneOwner,
    )

    private val definitions = linkedMapOf<String, Definition>()
    private val instances = mutableMapOf<UUID, String>()
    private val requests = SceneRequestGate()
    private var retainedRevisions = emptySet<String>()
    private var level: ClientLevel? = null
    private var warnedAt = 0L
    private val emptyFrame = SceneFrame(null, null, 0f, 0f, 0f, emptyList())
    private var sampled = emptyFrame

    private data class RenderFrame(
        val meshes: List<SceneMesh>,
        val camera: Vec3,
        val view: Matrix4f,
    )

    @Volatile private var renderFrame = RenderFrame(emptyList(), Vec3.ZERO, Matrix4f())
    private val runtime =
        SceneRuntime(
            object : SceneBackend {
                override fun resolve(
                    anchor: EffectAnchor,
                    context: SceneContext,
                    partialTick: Float,
                ): Vec3? =
                    when (anchor) {
                        is EffectAnchor.Position -> anchor.position
                        is EffectAnchor.Origin -> context.origin.add(anchor.offset)
                        is EffectAnchor.Entity -> {
                            val id = anchor.id ?: context.target
                            mc.level
                                ?.entitiesForRendering()
                                ?.firstOrNull { it.uuid == id && it.isAlive }
                                ?.getPosition(partialTick)
                                ?.add(anchor.offset)
                        }
                    }

                override fun particle(type: ParticleOptions, position: Vec3, velocity: Vec3) {
                    mc.level?.addParticle(
                        type,
                        position.x,
                        position.y,
                        position.z,
                        velocity.x,
                        velocity.y,
                        velocity.z,
                    )
                }

                override fun diagnostic(message: String, failure: Throwable?) {
                    logger.warn(message, failure)
                }
            }
        )

    private fun checkThread() {
        check(mc.isSameThread) { "Scene APIs must be called on the client thread" }
    }

    fun register(id: String, revision: String, scene: SceneDefinition) {
        checkThread()
        require(Identifier.tryParse(id) != null && id.length <= 256 && revision.length in 1..128)
        val previous = definitions[id]
        if (previous != null && previous.owner.revision in retainedRevisions) return
        top.katton.engine.ManagedResources.put(definitions, id, Definition(revision, scene, SceneOwner.capture()), exclusive = true)
    }

    fun unregister(id: String): Boolean {
        checkThread()
        instances.filterValues { it == id }.keys.toList().forEach(runtime::stop)
        return definitions.remove(id) != null
    }

    fun play(id: String, context: SceneContext): SceneHandle {
        checkThread()
        syncWorld()
        val definition = requireNotNull(definitions[id]) { "Unknown client scene: $id" }
        return playDefinition(id, definition, context, UUID.randomUUID())
    }

    fun play(scene: SceneDefinition, context: SceneContext): SceneHandle {
        checkThread()
        syncWorld()
        check(mc.level != null && mc.player?.isAlive == true) { "A live client world is required" }
        return runtime.play(scene, context)
    }

    private fun playDefinition(
        id: String,
        definition: Definition,
        context: SceneContext,
        instance: UUID,
    ): SceneHandle {
        check(mc.level != null && mc.player?.isAlive == true) { "A live client world is required" }
        val handle = runtime.play(definition.scene, context, definition.owner, instance)
        if (handle.isActive) {
            instances[instance] = id
            handle.onEnd { instances.remove(instance) }
        }
        return handle
    }

    fun setBudgets(budgets: EffectBudgets) {
        checkThread()
        runtime.budgets = budgets
    }

    @JvmStatic
    fun tick() {
        checkThread()
        syncWorld()
        if (mc.level == null || mc.player?.isAlive != true) {
            runtime.clear()
            resetFrame()
            SceneRenderCompat.close()
            return
        }
        if (!mc.isPaused) runtime.tick()
        if (runtime.inputLocked && mc.player?.isUsingItem == true)
            mc.gameMode?.releaseUsingItem(mc.player!!)
        if (runtime.activeScenes == 0) {
            resetFrame()
            SceneRenderCompat.close()
        }
    }

    private fun syncWorld() {
        if (level !== mc.level) {
            runtime.clear()
            resetFrame()
            SceneRenderCompat.close()
            level = mc.level
        }
    }

    @JvmStatic
    fun sample(partialTick: Float): SceneFrame {
        syncWorld()
        sampled =
            if (mc.level == null || mc.player?.isAlive != true) emptyFrame
            else runtime.frame(if (mc.isPaused) 0f else partialTick)
        return sampled
    }

    @JvmStatic fun fov(original: Float): Float = sampled.fov ?: original

    /** Called during extraction, after the camera has received its visual pose. */
    @JvmStatic
    fun prepareGeometry(camera: Camera) {
        val meshes =
            SceneGeometry.build(
                sampled.geometry,
                camera.position(),
                runtime.budgets.verticesPerFrame,
            )
        if (
            meshes.sumOf { it.vertices.size } >= runtime.budgets.verticesPerFrame / 3 * 3 &&
                sampled.geometry.isNotEmpty()
        )
            warn("Scene vertex budget reached")
        renderFrame =
            RenderFrame(meshes, camera.position(), camera.getViewRotationMatrix(Matrix4f()))
    }

    /** Drawing consumes only the immutable snapshot, never live world data. */
    @JvmStatic
    fun render() {
        val frame = renderFrame
        try {
            SceneRenderCompat.render(frame.meshes, frame.camera, frame.view)
        } catch (failure: Exception) {
            warn("Scene drawing failed: ${failure.message}", failure)
            SceneRenderCompat.close()
        }
    }

    @JvmStatic fun inputLocked(): Boolean = runtime.inputLocked

    @JvmStatic fun cameraActive(): Boolean = sampled.camera != null

    @JvmStatic fun skipCamera(): Boolean = runtime.skipCamera().also { if (it) resetFrame() }

    @JvmStatic
    fun disconnect() {
        runtime.clear()
        definitions.entries.removeIf { it.value.owner.scope != ScriptPackScope.GLOBAL }
        instances.clear()
        requests.clear()
        level = null
        resetFrame()
        SceneRenderCompat.close()
    }

    @JvmStatic
    fun clearForReload(preserveRevisions: Set<String> = emptySet()) {
        retainedRevisions = preserveRevisions.toSet()
        val scopes = setOf(ScriptPackScope.WORLD, ScriptPackScope.SERVER_CACHE)
        fun affected(owner: SceneOwner) =
            owner.scope in scopes && owner.revision !in retainedRevisions
        runtime.clear(EffectEndReason.RELOAD, ::affected)
        definitions.entries.removeIf { affected(it.value.owner) }
        resetFrame()
        SceneRenderCompat.close()
    }

    @JvmStatic
    fun finishReload() {
        retainedRevisions = emptySet()
    }

    @JvmStatic
    fun resourceReload() {
        resetFrame()
        SceneRenderCompat.close()
    }

    @JvmStatic
    fun handlePacket(packet: ClientScenePacket) {
        checkThread()
        syncWorld()
        val start = packet.start
        if (
            !requests.accept(
                packet,
                mc.level?.dimension()?.identifier()?.toString(),
                { definitions[it]?.revision },
                { id ->
                    mc.level?.entitiesForRendering()?.any { it.uuid == id && it.isAlive } == true
                },
            )
        ) {
            warn("Ignored duplicate, stopped, unavailable or outdated scene ${start?.definitionId}")
            return
        }
        if (start == null) {
            runtime.stop(packet.instanceId)
            return
        }
        if (mc.player?.isAlive != true) return
        val definition = definitions.getValue(start.definitionId)
        playDefinition(start.definitionId, definition, start.context, packet.instanceId)
    }

    /** For diagnostics and soak-test assertions. */
    fun stats(): Map<String, Int> =
        mapOf(
            "definitions" to definitions.size,
            "scenes" to runtime.activeScenes,
            "effects" to runtime.activeEffects,
            "buffers" to SceneRenderCompat.bufferCount,
        )

    private fun resetFrame() {
        sampled = emptyFrame
        renderFrame = RenderFrame(emptyList(), Vec3.ZERO, Matrix4f())
    }

    private fun warn(message: String, failure: Throwable? = null) {
        val now = System.nanoTime()
        if (now - warnedAt > 5_000_000_000L) {
            warnedAt = now
            logger.warn(message, failure)
        }
    }
}
