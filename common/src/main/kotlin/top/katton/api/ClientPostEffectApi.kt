@file:Suppress("unused")

package top.katton.api

import net.minecraft.resources.Identifier
import top.katton.client.ClientPostEffectManager

/**
 * %en
 * Register a runtime client post effect from Minecraft 26.x post-effect JSON.
 *
 * The JSON is the same format used by resource packs at
 * `assets/<namespace>/post_effect/<path>.json`. Custom shader ids referenced
 * from that JSON can be supplied through [fragmentShaders] and [vertexShaders].
 *
 * %zh
 * 注册一个运行时客户端 post effect，读取 Minecraft 26.x 的 post-effect JSON。
 *
 * 该 JSON 与资源包中的 `assets/<namespace>/post_effect/<path>.json` 格式一致。
 * JSON 中引用的自定义 shader id 可以通过 [fragmentShaders] 和 [vertexShaders] 提供。
 */
fun registerClientPostEffect(
    id: Identifier,
    postEffectJson: String,
    fragmentShaders: Map<Identifier, String> = emptyMap(),
    vertexShaders: Map<Identifier, String> = emptyMap()
): Boolean = ClientPostEffectManager.register(id, postEffectJson, fragmentShaders, vertexShaders)

/**
 * %en
 * Register a runtime client post effect by reading
 * `assets/<namespace>/post_effect/<path>.json` from the active resource packs.
 *
 * Shader and texture resources referenced by the JSON are also checked. Missing
 * resources are logged as warnings and the post effect JSON must exist for this
 * function to return true.
 *
 * %zh
 * 从当前启用的资源包中读取 `assets/<namespace>/post_effect/<path>.json` 并注册一个运行时客户端 post effect。
 *
 * JSON 中引用的 shader 和纹理资源也会一并检查。缺失资源会记录为警告；只有 post effect JSON 本身存在时，这个函数才会返回 true。
 */
fun registerClientPostEffect(id: Identifier): Boolean =
    ClientPostEffectManager.registerFromResourcePack(id)

fun registerClientPostEffect(
    id: String,
    postEffectJson: String,
    fragmentShaders: Map<String, String> = emptyMap(),
    vertexShaders: Map<String, String> = emptyMap()
): Boolean {
    val effectId = Identifier.tryParse(id) ?: return false
    return registerClientPostEffect(
        id = effectId,
        postEffectJson = postEffectJson,
        fragmentShaders = fragmentShaders.parseIdentifierMap(),
        vertexShaders = vertexShaders.parseIdentifierMap()
    )
}

fun registerClientPostEffect(id: String): Boolean {
    val effectId = Identifier.tryParse(id) ?: return false
    return registerClientPostEffect(effectId)
}

fun registerClientPostEffectFromResourcePack(id: Identifier): Boolean =
    registerClientPostEffect(id)

fun registerClientPostEffectFromResourcePack(id: String): Boolean =
    registerClientPostEffect(id)

/**
 * %en
 * Build and register a simple one-pass full-screen effect.
 *
 * [fragmentShaderSource] should define a post-processing fragment shader that
 * samples `InSampler` and writes `fragColor`. The generated chain renders
 * `minecraft:main -> swap -> minecraft:main`.
 *
 * %zh
 * 构建并注册一个简单的单通道全屏效果。
 *
 * [fragmentShaderSource] 应该定义一个后处理 fragment shader，负责采样 `InSampler` 并写入 `fragColor`。
 * 生成的渲染链为 `minecraft:main -> swap -> minecraft:main`。
 */
fun registerSimpleClientPostEffect(
    id: String,
    fragmentShaderSource: String,
    fragmentShaderId: String? = null,
    uniformsJson: String = "{}"
): Boolean {
    val effectId = Identifier.tryParse(id) ?: return false
    val effectIdString = effectId.toString()
    val defaultFragmentShaderId = "${effectIdString.substringBefore(':')}:post/${effectIdString.substringAfter(':')}"
    val fragmentId = Identifier.tryParse(fragmentShaderId ?: defaultFragmentShaderId) ?: return false
    val uniforms = uniformsJson.trim().ifBlank { "{}" }
    val json = """
        {
          "targets": {
            "swap": {}
          },
          "passes": [
            {
              "vertex_shader": "minecraft:core/screenquad",
              "fragment_shader": "$fragmentId",
              "inputs": [
                {
                  "sampler_name": "In",
                  "target": "minecraft:main"
                }
              ],
              "output": "swap",
              "uniforms": $uniforms
            },
            {
              "vertex_shader": "minecraft:core/screenquad",
              "fragment_shader": "minecraft:post/blit",
              "inputs": [
                {
                  "sampler_name": "In",
                  "target": "swap"
                }
              ],
              "uniforms": {
                "BlitConfig": [
                  {
                    "name": "ColorModulate",
                    "type": "vec4",
                    "value": [1.0, 1.0, 1.0, 1.0]
                  }
                ]
              },
              "output": "minecraft:main"
            }
          ]
        }
    """.trimIndent()

    return registerClientPostEffect(
        id = effectId,
        postEffectJson = json,
        fragmentShaders = mapOf(fragmentId to fragmentShaderSource)
    )
}

fun unregisterClientPostEffect(id: Identifier): Boolean =
    ClientPostEffectManager.unregister(id)

fun unregisterClientPostEffect(id: String): Boolean =
    Identifier.tryParse(id)?.let(::unregisterClientPostEffect) ?: false

fun clearClientPostEffects() {
    ClientPostEffectManager.clearScriptOwned()
}

fun clearAllClientPostEffects() {
    ClientPostEffectManager.clearAll()
}

fun setClientPostEffect(id: Identifier): Boolean =
    ClientPostEffectManager.setPostEffect(id)

fun setClientPostEffect(id: String): Boolean =
    Identifier.tryParse(id)?.let(::setClientPostEffect) ?: false

fun clearClientPostEffect(): Boolean =
    ClientPostEffectManager.clearPostEffect()

fun toggleClientPostEffect(): Boolean =
    ClientPostEffectManager.togglePostEffect()

fun currentClientPostEffect(): Identifier? =
    ClientPostEffectManager.currentPostEffect()

fun currentClientPostEffectId(): String? =
    currentClientPostEffect()?.toString()

fun isClientPostEffectActive(): Boolean =
    ClientPostEffectManager.isPostEffectActive()

fun hasClientPostEffect(id: Identifier): Boolean =
    ClientPostEffectManager.hasDefinition(id)

fun hasClientPostEffect(id: String): Boolean =
    Identifier.tryParse(id)?.let(::hasClientPostEffect) ?: false

private fun Map<String, String>.parseIdentifierMap(): Map<Identifier, String> {
    if (isEmpty()) return emptyMap()
    val parsed = LinkedHashMap<Identifier, String>(size)
    for ((key, value) in this) {
        val id = Identifier.tryParse(key) ?: continue
        parsed[id] = value
    }
    return parsed
}
