package top.katton.client

/**
 * Centralized progress tracker for script reload operations.
 *
 * Replaces scattered [ReloadProgressState] calls with a simple step counter
 * that computes progress automatically as `currentStep / estimatedSteps`.
 *
 * Usage:
 * ```
 * val tracker = ReloadProgressTracker(22)
 * tracker.begin("Reloading server scripts")
 * // ... do work ...
 * tracker.step("Setting game directory")
 * // ... do more work ...
 * tracker.finish("Server scripts reloaded")
 * ```
 */
class ReloadProgressTracker(private val estimatedSteps: Int) {

    private var currentStep = 0

    /**
     * Begins tracking with the given title and 0% progress.
     * Must be called before any [step] calls.
     */
    fun begin(title: String) {
        currentStep = 0
        ReloadProgressState.begin(title, 0f)
    }

    /**
     * Advances to the next step, updating the progress bar with the given label.
     * Progress is computed as `currentStep / estimatedSteps`.
     */
    fun step(label: String) {
        currentStep++
        val progress = (currentStep.toFloat() / estimatedSteps).coerceAtMost(0.99f)
        ReloadProgressState.update(label, progress)
    }

    /**
     * Finishes tracking, showing the final message at 100% for a brief period.
     */
    fun finish(message: String) {
        ReloadProgressState.finish(message)
    }
}
