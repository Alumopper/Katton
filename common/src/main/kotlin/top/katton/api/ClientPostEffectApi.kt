@file:Suppress("unused")

package top.katton.api

import net.minecraft.resources.Identifier
import top.katton.client.ClientPostEffectManager

/**
 * Register a runtime client post effect from Minecraft 26.1 post-effect JSON.
 *
 * The JSON is the same format used by resource packs at
 * `assets/<namespace>/post_effect/<path>.json`. Custom shader ids referenced
 * from that JSON can be supplied through [fragmentShaders] and [vertexShaders].
 */
fun registerClientPostEffect(
    id: Identifier,
    postEffectJson: String,
    fragmentShaders: Map<Identifier, String> = emptyMap(),
    vertexShaders: Map<Identifier, String> = emptyMap()
): Boolean = ClientPostEffectManager.register(id, postEffectJson, fragmentShaders, vertexShaders)

/**
 * Register a runtime client post effect by reading
 * `assets/<namespace>/post_effect/<path>.json` from the active resource packs.
 *
 * Shader and texture resources referenced by the JSON are also checked. Missing
 * resources are logged as warnings and the post effect JSON must exist for this
 * function to return true.
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
 * Build and register a simple one-pass full-screen effect.
 *
 * [fragmentShaderSource] should define a post-processing fragment shader that
 * samples `InSampler` and writes `fragColor`. The generated chain renders
 * `minecraft:main -> swap -> minecraft:main`.
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
