package top.katton.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerRuntimeCapabilitiesTest {
    @AfterTest
    fun resetCapabilities() {
        ServerRuntimeCapabilities.reset()
    }

    @Test
    fun `script pack data reload is enabled by default`() {
        assertTrue(ServerRuntimeCapabilities.supportsScriptPackDataReload())
    }

    @Test
    fun `platform can disable script pack data reload`() {
        ServerRuntimeCapabilities.configureScriptPackDataReload(false)

        assertFalse(ServerRuntimeCapabilities.supportsScriptPackDataReload())
    }
}
