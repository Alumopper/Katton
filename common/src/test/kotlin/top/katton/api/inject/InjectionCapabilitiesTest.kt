package top.katton.api.inject

import top.katton.Katton
import top.katton.engine.InjectionEnvironment
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InjectionCapabilitiesTest {
    private val originalDynamicAttach = System.getProperty(InjectionEnvironment.DYNAMIC_ATTACH_PROPERTY)
    private val originalLauncherBrand = System.getProperty("minecraft.launcher.brand")
    private val originalOsVersion = System.getProperty("os.version")

    @AfterTest
    fun restoreRuntimeState() {
        restoreProperty(InjectionEnvironment.DYNAMIC_ATTACH_PROPERTY, originalDynamicAttach)
        restoreProperty("minecraft.launcher.brand", originalLauncherBrand)
        restoreProperty("os.version", originalOsVersion)
        Katton.registrationEnabled = true
        Katton.hasClient = true
    }

    @Test
    fun `disabled dynamic attach reports explicit startup agent remediation`() {
        System.setProperty(InjectionEnvironment.DYNAMIC_ATTACH_PROPERTY, "false")
        System.setProperty("minecraft.launcher.brand", "Fold Craft Launcher")
        System.setProperty("os.version", "Android-simulated")

        val report = InjectionCapabilities.query()

        assertEquals(InjectionCapabilityStatus.UNSUPPORTED, report.status)
        assertEquals(InjectionAcquisitionMode.NONE, report.mode)
        assertFalse(report.supported)
        assertTrue(report.fclDetected)
        assertTrue(report.androidDetected)
        assertTrue(report.detail.startsWith("不支持"))
        assertTrue(report.remediation?.contains("-javaagent:") == true)
    }

    @Test
    fun `paper reports injection as unsupported without attaching`() {
        Katton.registrationEnabled = false
        Katton.hasClient = false

        val report = InjectionCapabilities.query()

        assertEquals(InjectionCapabilityStatus.UNSUPPORTED, report.status)
        assertEquals("PAPER", report.platform)
        assertTrue(report.detail.startsWith("不支持"))
    }

    private fun restoreProperty(name: String, value: String?) {
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    }
}
