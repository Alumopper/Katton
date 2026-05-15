package top.katton.client

/**
 * GUI stub for Paper (server-only platform).
 * ScriptPackUi requires Minecraft client classes — not available on Paper.
 */
object ScriptPackUi {
    @JvmStatic fun openInWorldScreen() {}
}

/**
 * Reload progress state stub.
 * No HUD overlay on Paper since there is no client.
 */
object ReloadProgressState {
    @JvmStatic fun begin(message: String) {}
    @JvmStatic fun step(message: String) {}
    @JvmStatic fun finish(message: String) {}
}

/**
 * Reload progress tracker stub.
 * Tracks step count but does not render anything on Paper.
 */
class ReloadProgressTracker(totalSteps: Int) {
    private var currentStep = 0

    fun begin(message: String) {
        currentStep = 0
    }

    fun step(message: String) {
        currentStep++
    }

    fun finish(message: String) {}
}
