package top.katton.api

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.server.MinecraftServer
import top.katton.pack.ScriptPackScope

/** Execution stages available to server entrypoints. */
enum class ServerPhase {
    /** Early, process-lifetime initialization. Only GLOBAL packs may use this phase. */
    BOOTSTRAP,

    /** A server and its worlds are available. GLOBAL and WORLD packs may use this phase. */
    READY
}

/** Execution stages available to client entrypoints. */
enum class ClientPhase {
    /** One-time client initialization. Only GLOBAL packs may use this phase. */
    READY,

    /** Client content registration before remote registry validation. */
    REGISTRY_SETUP,

    /** A client connection, player, and world are available. */
    JOINED
}

/** Whether an entrypoint is running for the first time or being replayed after a reload. */
enum class InvocationReason {
    /** The entrypoint is running for the first time at its lifecycle boundary. */
    INITIAL_LOAD,

    /** The entrypoint is replaying after an accepted script reload or synchronized revision. */
    HOT_RELOAD
}

/** The operation that caused an entrypoint invocation. */
enum class ReloadCause {
    /** A server reached its ready lifecycle boundary. */
    SERVER_START,

    /** A Katton command requested a script reload. */
    COMMAND,

    /** Minecraft's datapack reload flow requested a script reload. */
    DATAPACK_RELOAD,

    /** A client activated a server-authoritative script-pack snapshot. */
    SERVER_PACK_SYNC,

    /** A client connection gained its local player and level. */
    CLIENT_JOIN
}

/** Common metadata supplied to phase-aware entrypoints. */
sealed interface ScriptInvocationContext {
    val packId: String
    val scope: ScriptPackScope
    val reason: InvocationReason
    val cause: ReloadCause
    val platform: String
}

/** Invocation metadata for [ServerPhase.BOOTSTRAP]. */
data class BootstrapContext(
    override val packId: String,
    override val scope: ScriptPackScope,
    override val reason: InvocationReason,
    override val cause: ReloadCause,
    override val platform: String
) : ScriptInvocationContext

/** Invocation metadata for [ServerPhase.READY], including the available [server]. */
data class ServerReadyContext(
    override val packId: String,
    override val scope: ScriptPackScope,
    override val reason: InvocationReason,
    override val cause: ReloadCause,
    override val platform: String,
    val server: MinecraftServer
) : ScriptInvocationContext

/** Invocation metadata for [ClientPhase.READY], including the available [client]. */
data class ClientReadyContext(
    override val packId: String,
    override val scope: ScriptPackScope,
    override val reason: InvocationReason,
    override val cause: ReloadCause,
    override val platform: String,
    val client: Minecraft
) : ScriptInvocationContext

/** Invocation metadata for [ClientPhase.REGISTRY_SETUP], before remote registry validation. */
data class ClientRegistryContext(
    override val packId: String,
    override val scope: ScriptPackScope,
    override val reason: InvocationReason,
    override val cause: ReloadCause,
    override val platform: String,
    val client: Minecraft
) : ScriptInvocationContext

/** Invocation metadata for [ClientPhase.JOINED], including the available [player] and [level]. */
data class ClientJoinedContext(
    override val packId: String,
    override val scope: ScriptPackScope,
    override val reason: InvocationReason,
    override val cause: ReloadCause,
    override val platform: String,
    val client: Minecraft,
    val player: LocalPlayer,
    val level: ClientLevel
) : ScriptInvocationContext

/** Marks a top-level function as a client script entrypoint. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ClientScriptEntrypoint(
    val phase: ClientPhase = ClientPhase.READY,
    /** Honored for WORLD packs. SERVER_CACHE always replays and GLOBAL never replays. */
    val replay: Boolean = true
)

/** Marks a top-level function as a server script entrypoint. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ServerScriptEntrypoint(
    val phase: ServerPhase = ServerPhase.BOOTSTRAP,
    /** Honored for WORLD packs. GLOBAL entrypoints never replay. */
    val replay: Boolean = true
)
