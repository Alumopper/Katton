package top.katton.client

/**
 * GUI stub for Paper (server-only platform).
 * ScriptPackUi requires Minecraft client classes — not available on Paper.
 */
object ScriptPackUi {
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun openInWorldScreen() {}
    @JvmStatic fun installErrorReporter() {}
    @JvmStatic fun openScriptIssueScreen(issue: top.katton.engine.ScriptIssue) {}
}

/**
 * Reload progress state stub.
 * No HUD overlay on Paper since there is no client.
 */
object ReloadProgressState {
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun begin(message: String) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun update(message: String, progress: Float) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun step(message: String) {}
    @Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
    @JvmStatic fun finish(message: String) {}
}

/**
 * Reload progress tracker stub.
 * Tracks step count but does not render anything on Paper.
 */
@Deprecated("Client api is unavailable in paper plugin", level = DeprecationLevel.ERROR)
class ReloadProgressTracker(totalSteps: Int) {
    private var currentStep = 0

    fun begin(message: String) {
        currentStep = 0
    }

    fun step(message: String) {
        currentStep++
    }

    fun update(message: String) {}

    fun finish(message: String) {}
}
