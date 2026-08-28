package top.katton.engine

import top.katton.api.inject.InjectionAcquisitionMode
import top.katton.api.inject.InjectionCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicAttachInjectionTest {
    @Test
    fun `dynamic attach performs and rolls back method injection`() {
        if (!java.lang.Boolean.getBoolean("katton.probe.expectDynamicAttach")) return

        val capability = InjectionCapabilities.query()
        assertEquals(InjectionAcquisitionMode.DYNAMIC_ATTACH, capability.mode)

        val target = InjectionProbeTarget()
        val method = InjectionProbeTarget::class.java.getDeclaredMethod(
            "add",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        val handle = InjectionManager.injectBefore("dynamic-attach-probe", method) { invocation ->
            invocation.setArgument(0, 10)
        }

        assertEquals(12, target.add(1, 2))
        assertEquals(true, InjectionManager.rollback(handle.id))
        assertEquals(3, target.add(1, 2))
    }
}
