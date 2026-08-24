package top.katton.api.event

import top.katton.engine.PlatformDependencyResolver
import top.katton.engine.ScriptDependencyManager
import top.katton.pack.ScriptPlatform
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventCapabilitiesTest {
    @AfterTest
    fun resetPlatform() {
        ScriptDependencyManager.install(ScriptPlatform.UNKNOWN, PlatformDependencyResolver { null })
    }

    @Test
    fun `neoForge placeholder bridge reports unsupported`() {
        ScriptDependencyManager.install(ScriptPlatform.NEOFORGE, PlatformDependencyResolver { null })

        val capability = EventCapabilities.query("ItemComponentEvent.onModifyComponent")

        assertEquals(EventCapabilityStatus.UNSUPPORTED, capability.status)
        assertFalse(capability.supported)
        assertTrue(capability.detail.startsWith("不支持"))
    }

    @Test
    fun `neoForge unhooked living behavior placeholders report unsupported`() {
        ScriptDependencyManager.install(ScriptPlatform.NEOFORGE, PlatformDependencyResolver { null })

        listOf(
            "LivingBehaviorEvent.onSetBedOccupationState",
            "LivingBehaviorEvent.onModifyWakeUpPosition"
        ).forEach { eventId ->
            val capability = EventCapabilities.query(eventId)
            assertEquals(EventCapabilityStatus.UNSUPPORTED, capability.status, eventId)
            assertFalse(capability.supported, eventId)
            assertTrue(capability.detail.startsWith("不支持"), eventId)
        }
    }

    @Test
    fun `paper raw enchant event reports partial support`() {
        ScriptDependencyManager.install(ScriptPlatform.PAPER, PlatformDependencyResolver { null })

        val capability = EventCapabilities.query("ItemComponentEvent.onAllowEnchanting")

        assertEquals(EventCapabilityStatus.PARTIAL, capability.status)
        assertTrue(capability.supported)
    }
}
