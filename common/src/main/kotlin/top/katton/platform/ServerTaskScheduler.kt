package top.katton.platform

import net.minecraft.server.MinecraftServer

/** Dispatches Katton's serialized server mutations onto the platform-safe thread. */
fun interface ServerTaskDispatcher {
    fun dispatch(server: MinecraftServer, task: Runnable)
}

/**
 * Platform hook for work that must mutate server-wide state. Vanilla mod
 * platforms use Minecraft's server executor; Paper installs its global-region
 * scheduler so the same reload path is safe on both Paper and Folia.
 */
object ServerTaskScheduler {
    @Volatile
    private var dispatcher: ServerTaskDispatcher? = null

    @JvmStatic
    fun install(dispatcher: ServerTaskDispatcher) {
        this.dispatcher = dispatcher
    }

    @JvmStatic
    fun reset() {
        dispatcher = null
    }

    @JvmStatic
    fun execute(server: MinecraftServer, task: Runnable) {
        val platformDispatcher = dispatcher
        if (platformDispatcher == null) {
            server.execute(task)
        } else {
            platformDispatcher.dispatch(server, task)
        }
    }
}
