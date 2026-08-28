package top.katton.api.inject

import top.katton.engine.InjectionManager

/** Current availability of Katton's unsafe runtime bytecode injection. */
enum class InjectionCapabilityStatus {
    SUPPORTED,
    UNSUPPORTED
}

/** How Katton acquired the JVM [java.lang.instrument.Instrumentation] instance. */
enum class InjectionAcquisitionMode {
    STARTUP_AGENT,
    DYNAMIC_ATTACH,
    NONE
}

/**
 * Structured injection capability report suitable for scripts, commands, and support logs.
 */
data class InjectionCapabilityReport(
    val status: InjectionCapabilityStatus,
    val mode: InjectionAcquisitionMode,
    val platform: String,
    val fclDetected: Boolean,
    val androidDetected: Boolean,
    val javaVersion: String,
    val javaVendor: String,
    val vmName: String,
    val os: String,
    val architecture: String,
    val instrumentationModulePresent: Boolean,
    val attachModulePresent: Boolean,
    val attachProviderCount: Int,
    val redefineClassesSupported: Boolean?,
    val retransformClassesSupported: Boolean?,
    val detail: String,
    val remediation: String?,
    val startupAgentArgument: String,
    val relevantJvmArguments: List<String>
) {
    val supported: Boolean
        get() = status == InjectionCapabilityStatus.SUPPORTED

    /** Lines formatted for `/katton capabilities injection` or support logs. */
    fun diagnosticLines(): List<String> = buildList {
        add("injection: status=$status, mode=$mode, platform=$platform")
        add("runtime: java=$javaVendor $javaVersion, vm=$vmName, os=$os, arch=$architecture")
        add(
            "modules: java.instrument=$instrumentationModulePresent, jdk.attach=$attachModulePresent, " +
                "attachProviders=$attachProviderCount, redefine=$redefineClassesSupported, " +
                "retransform=$retransformClassesSupported"
        )
        add("launcher: fcl=$fclDetected, android=$androidDetected")
        if (relevantJvmArguments.isNotEmpty()) {
            add("jvmArgs: ${relevantJvmArguments.joinToString(" ")}")
        }
        add("detail: $detail")
        remediation?.let { add("fix: $it") }
    }
}

/** Capability query for the experimental unsafe injection API. */
object InjectionCapabilities {
    /**
     * Performs the same instrumentation acquisition used by an actual injection and returns
     * an explicit supported/unsupported report. Calling this may install Byte Buddy dynamically
     * when the current JVM supports Attach.
     */
    @JvmStatic
    fun query(): InjectionCapabilityReport = InjectionManager.capabilityReport()
}
