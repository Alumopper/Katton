package top.katton.engine

import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import top.katton.api.*
import top.katton.pack.ScriptPack

/** A concrete lifecycle dispatch requested by a platform or reload coordinator. */
data class ScriptInvocation(
    val environment: ScriptEnvironment,
    val phaseName: String,
    val reason: InvocationReason,
    val cause: ReloadCause,
    val server: MinecraftServer? = null,
    val client: Minecraft? = null
) {
    companion object {
        fun server(
            phase: ServerPhase,
            reason: InvocationReason,
            cause: ReloadCause,
            server: MinecraftServer? = null
        ) = ScriptInvocation(ScriptEnvironment.SERVER, phase.name, reason, cause, server = server)

        fun client(
            phase: ClientPhase,
            reason: InvocationReason,
            cause: ReloadCause,
            client: Minecraft = Minecraft.getInstance()
        ) = ScriptInvocation(ScriptEnvironment.CLIENT, phase.name, reason, cause, client = client)
    }

    fun contextFor(pack: ScriptPack): ScriptInvocationContext {
        val platform = ScriptDependencyManager.platform.serializedName
        return when (environment) {
            ScriptEnvironment.SERVER -> when (ServerPhase.valueOf(phaseName)) {
                ServerPhase.BOOTSTRAP -> BootstrapContext(pack.manifest.id, pack.scope, reason, cause, platform)
                ServerPhase.READY -> ServerReadyContext(
                    pack.manifest.id,
                    pack.scope,
                    reason,
                    cause,
                    platform,
                    requireNotNull(server) { "ServerPhase.READY requires a MinecraftServer" }
                )
            }

            ScriptEnvironment.CLIENT -> when (ClientPhase.valueOf(phaseName)) {
                ClientPhase.READY -> ClientReadyContext(pack.manifest.id, pack.scope, reason, cause, platform, requireClient())
                ClientPhase.REGISTRY_SETUP -> ClientRegistryContext(
                    pack.manifest.id,
                    pack.scope,
                    reason,
                    cause,
                    platform,
                    requireClient()
                )
                ClientPhase.JOINED -> {
                    val minecraft = requireClient()
                    ClientJoinedContext(
                        pack.manifest.id,
                        pack.scope,
                        reason,
                        cause,
                        platform,
                        minecraft,
                        requireNotNull(minecraft.player) { "ClientPhase.JOINED requires a local player" },
                        requireNotNull(minecraft.level) { "ClientPhase.JOINED requires a client level" }
                    )
                }
            }
        }
    }

    private fun requireClient(): Minecraft = requireNotNull(client) { "Client lifecycle phase requires Minecraft" }
}
