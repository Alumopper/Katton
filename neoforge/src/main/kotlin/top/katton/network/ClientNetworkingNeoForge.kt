package top.katton.network

/**
 * Client-side networking for Katton on NeoForge.
 *
 * Actual payload registration is handled by {@link ServerNetworkingNeoForge}
 * on the MOD bus, which covers both dedicated server and integrated server
 * (singleplayer) scenarios.
 */
object ClientNetworkingNeoForge
