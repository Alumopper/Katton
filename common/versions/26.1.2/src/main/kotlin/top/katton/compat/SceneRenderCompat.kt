package top.katton.compat

import com.mojang.blaze3d.pipeline.*
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexFormat
import java.util.Optional
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import top.katton.api.scene.*
import top.katton.scene.SceneMesh

internal object SceneRenderCompat {
    private var buffer: ByteBufferBuilder? = null
    private val pipelines = mutableMapOf<Pair<Boolean, EffectBlend>, RenderPipeline>()
    private val types = linkedMapOf<EffectMaterial, RenderType>()
    private val white = Identifier.fromNamespaceAndPath("katton", "textures/effect/white.png")
    val bufferCount
        get() = if (buffer == null) 0 else 1

    private fun type(material: EffectMaterial): RenderType =
        types.getOrPut(material) {
            if (types.size >= 256) types.remove(types.keys.first())
            val pipeline =
                pipelines.getOrPut(material.throughWalls to material.blend) {
                    val base = RenderPipelines.BEACON_BEAM_TRANSLUCENT
                    val builder =
                        RenderPipeline.builder()
                            .withLocation(
                                Identifier.fromNamespaceAndPath(
                                    "katton",
                                    "pipeline/scene_${material.throughWalls}_${material.blend.name.lowercase()}",
                                )
                            )
                            .withVertexShader(base.vertexShader)
                            .withFragmentShader(base.fragmentShader)
                            .withVertexFormat(base.vertexFormat, VertexFormat.Mode.TRIANGLES)
                            .withCull(false)
                            .withColorTargetState(
                                ColorTargetState(
                                    if (material.blend == EffectBlend.ADDITIVE)
                                        BlendFunction.LIGHTNING
                                    else BlendFunction.TRANSLUCENT
                                )
                            )
                            .withDepthStencilState(
                                if (material.throughWalls) Optional.empty()
                                else Optional.ofNullable(base.depthStencilState)
                            )
                    base.samplers.forEach(builder::withSampler)
                    base.uniforms.forEach { uniform ->
                        builder.withUniform(uniform.name(), uniform.type())
                    }
                    builder.build()
                }
            RenderType.create(
                "katton_scene",
                RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", material.texture ?: white)
                    .createRenderSetup(),
            )
        }

    fun render(meshes: List<SceneMesh>, camera: Vec3, view: Matrix4f) {
        if (meshes.isEmpty()) return
        val bytes = buffer ?: ByteBufferBuilder(1 shl 20).also { buffer = it }
        val matrix = RenderSystem.getModelViewStack()
        matrix.pushMatrix().set(view)
        try {
            for (mesh in meshes) {
                val type = type(mesh.material)
                val builder = BufferBuilder(bytes, type.mode(), type.format())
                for (v in mesh.vertices) builder
                    .addVertex(
                        (v.position.x - camera.x).toFloat(),
                        (v.position.y - camera.y).toFloat(),
                        (v.position.z - camera.z).toFloat(),
                    )
                    .setColor(v.color)
                    .setUv(v.u, v.v)
                    .setUv2(240, 240)
                    .setNormal(0f, 1f, 0f)
                type.draw(builder.buildOrThrow())
            }
        } finally {
            matrix.popMatrix()
        }
    }

    fun close() {
        buffer?.close()
        buffer = null
        types.clear()
    }
}
