package top.katton.client

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.logging.LogUtils
import com.mojang.serialization.JsonOps
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.PostChain
import net.minecraft.client.renderer.PostChainConfig
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import top.katton.network.ClientPostEffectPacket
import top.katton.util.ReflectUtil
import top.katton.util.ScriptExecutionContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object ClientPostEffectManager {
    private val logger = LogUtils.getLogger()
    private val mc: Minecraft = Minecraft.getInstance()
    private val postEffectResourceConverter = FileToIdConverter.json("post_effect")
    private val definitions = ConcurrentHashMap<Identifier, Definition>()
    private val postChainCache = ConcurrentHashMap<CacheKey, PostChain>()

    private data class Definition(
        val owner: String?,
        val config: PostChainConfig,
        val fragmentShaders: Map<Identifier, String>,
        val vertexShaders: Map<Identifier, String>
    )

    private data class CacheKey(
        val id: Identifier,
        val allowedTargets: Set<Identifier>
    )

    fun register(
        id: Identifier,
        postEffectJson: String,
        fragmentShaders: Map<Identifier, String> = emptyMap(),
        vertexShaders: Map<Identifier, String> = emptyMap(),
        owner: String? = ScriptExecutionContext.currentScriptOwner()
    ): Boolean {
        val config = parsePostEffectConfig(id, postEffectJson) ?: return false
        warnMissingReferencedResources(id, config, fragmentShaders, vertexShaders)
        definitions[id] = Definition(owner, config, fragmentShaders, vertexShaders)
        invalidatePostChainCache(id)
        return true
    }

    fun registerFromResourcePack(
        id: Identifier,
        owner: String? = ScriptExecutionContext.currentScriptOwner()
    ): Boolean {
        val resourceId = postEffectResourceConverter.idToFile(id)
        val resource = mc.resourceManager.getResource(resourceId)
        if (resource.isEmpty) {
            logger.warn("Client post effect resource {} was not found for {}", resourceId, id)
            return false
        }

        val postEffectJson = try {
            resource.get().openAsReader().use { reader -> reader.readText() }
        } catch (e: IOException) {
            logger.warn("Failed to read client post effect resource {} for {}", resourceId, id, e)
            return false
        }

        return register(id, postEffectJson, owner = owner)
    }

    fun unregister(id: Identifier): Boolean {
        val removed = definitions.remove(id) != null
        if (removed) {
            if (currentPostEffect() == id) {
                clearPostEffect()
            }
            invalidatePostChainCache(id)
        }
        return removed
    }

    fun clearScriptOwned() {
        val removedIds = definitions.entries
            .filter { it.value.owner != null }
            .map { it.key }
        if (removedIds.isEmpty()) return

        val current = currentPostEffect()
        removedIds.forEach(definitions::remove)
        if (current in removedIds) {
            clearPostEffect()
        }
        invalidatePostChainCache()
    }

    fun clearAll() {
        val hadDefinitions = definitions.isNotEmpty()
        val current = currentPostEffect()
        definitions.clear()
        if (hadDefinitions && current != null) {
            clearPostEffect()
        }
        invalidatePostChainCache()
    }

    fun hasDefinition(id: Identifier): Boolean = definitions.containsKey(id)

    fun setPostEffect(id: Identifier): Boolean {
        mc.execute {
            ReflectUtil.set(mc.gameRenderer, "postEffectId", id)
            ReflectUtil.set(mc.gameRenderer, "effectActive", true)
        }
        return true
    }

    fun clearPostEffect(): Boolean {
        mc.execute {
            mc.gameRenderer.clearPostEffect()
        }
        return true
    }

    fun togglePostEffect(): Boolean {
        mc.execute {
            mc.gameRenderer.togglePostEffect()
        }
        return true
    }

    fun currentPostEffect(): Identifier? = mc.gameRenderer.currentPostEffect()

    fun isPostEffectActive(): Boolean {
        return ReflectUtil.getT<Boolean>(mc.gameRenderer, "effectActive")
            .getOrNull() == true
    }

    @JvmStatic
    fun handlePacket(packet: ClientPostEffectPacket) {
        when (packet.action) {
            ClientPostEffectPacket.Action.SET -> packet.effectId?.toIdentifierOrNull()?.let(::setPostEffect)
            ClientPostEffectPacket.Action.CLEAR -> clearPostEffect()
            ClientPostEffectPacket.Action.TOGGLE -> togglePostEffect()
        }
    }

    @JvmStatic
    fun getRuntimeShaderSource(id: Identifier, type: ShaderType): String? {
        for (definition in definitions.values) {
            val source = when (type) {
                ShaderType.FRAGMENT -> definition.fragmentShaders[id]
                ShaderType.VERTEX -> definition.vertexShaders[id]
            }
            if (source != null) {
                return source
            }
        }
        return null
    }

    @JvmStatic
    fun getOrCreatePostChain(
        id: Identifier,
        allowedTargets: Set<Identifier>,
        textureManager: TextureManager,
        projection: Projection,
        projectionMatrixBuffer: ProjectionMatrixBuffer
    ): PostChain? {
        val definition = definitions[id] ?: return null
        val key = CacheKey(id, LinkedHashSet(allowedTargets))
        postChainCache[key]?.let { return it }

        return runCatching {
            PostChain.load(definition.config, textureManager, key.allowedTargets, id, projection, projectionMatrixBuffer)
        }.onSuccess { chain ->
            val previous = postChainCache.putIfAbsent(key, chain)
            if (previous != null) {
                chain.close()
            }
        }.onFailure {
            logger.warn("Failed to load runtime client post effect {}", id, it)
        }.getOrNull()?.let { postChainCache[key] ?: it }
    }

    @JvmStatic
    fun invalidatePostChainCache() {
        postChainCache.values.forEach { chain ->
            runCatching { chain.close() }
        }
        postChainCache.clear()
    }

    private fun invalidatePostChainCache(id: Identifier) {
        postChainCache.entries
            .filter { it.key.id == id }
            .forEach { entry ->
                postChainCache.remove(entry.key)?.also { chain -> runCatching { chain.close() } }
            }
    }

    private fun parsePostEffectConfig(id: Identifier, postEffectJson: String): PostChainConfig? {
        return try {
            val json = JsonParser.parseString(postEffectJson)
            PostChainConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(::JsonSyntaxException)
        } catch (e: JsonParseException) {
            logger.warn("Failed to parse runtime client post effect {}", id, e)
            null
        } catch (e: IllegalArgumentException) {
            logger.warn("Failed to parse runtime client post effect {}", id, e)
            null
        }
    }

    private fun warnMissingReferencedResources(
        effectId: Identifier,
        config: PostChainConfig,
        fragmentShaders: Map<Identifier, String>,
        vertexShaders: Map<Identifier, String>
    ) {
        for (pass in config.passes()) {
            warnMissingShaderResource(effectId, pass.fragmentShaderId(), ShaderType.FRAGMENT, fragmentShaders)
            warnMissingShaderResource(effectId, pass.vertexShaderId(), ShaderType.VERTEX, vertexShaders)

            for (input in pass.inputs()) {
                if (input is PostChainConfig.TextureInput) {
                    val textureResource = input.location().withPath { path -> "textures/effect/$path.png" }
                    if (mc.resourceManager.getResource(textureResource).isEmpty) {
                        logger.warn(
                            "Client post effect {} references missing texture resource {}",
                            effectId,
                            textureResource
                        )
                    }
                }
            }
        }
    }

    private fun warnMissingShaderResource(
        effectId: Identifier,
        shaderId: Identifier,
        shaderType: ShaderType,
        providedShaders: Map<Identifier, String>
    ) {
        if (shaderId in providedShaders) return

        val resourceId = shaderType.idConverter().idToFile(shaderId)
        if (mc.resourceManager.getResource(resourceId).isEmpty) {
            logger.warn(
                "Client post effect {} references missing {} shader resource {}",
                effectId,
                shaderType.getName(),
                resourceId
            )
        }
    }

    private fun String.toIdentifierOrNull(): Identifier? =
        runCatching { Identifier.parse(this) }.getOrNull()
}
