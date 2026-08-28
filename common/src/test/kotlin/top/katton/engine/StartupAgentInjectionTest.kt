package top.katton.engine

import top.katton.api.inject.InjectionAcquisitionMode
import top.katton.api.inject.InjectionCapabilities
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class StartupAgentInjectionTest {
    @Test
    fun `startup agent performs and rolls back method injection`() {
        if (!java.lang.Boolean.getBoolean("katton.probe.expectAgent")) return

        assertNotNull(KattonAgent.findInstrumentation())
        val capability = InjectionCapabilities.query()
        assertEquals(InjectionAcquisitionMode.STARTUP_AGENT, capability.mode)

        val target = InjectionProbeTarget()
        val method = InjectionProbeTarget::class.java.getDeclaredMethod(
            "add",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        val handle = InjectionManager.injectBefore("startup-agent-probe", method) { invocation ->
            invocation.setArgument(0, 10)
        }

        assertEquals(12, target.add(1, 2))
        assertEquals(true, InjectionManager.rollback(handle.id))
        assertEquals(3, target.add(1, 2))
    }

    @Test
    fun `isolated mod classloader can find system startup instrumentation`() {
        if (!java.lang.Boolean.getBoolean("katton.probe.expectAgent")) return

        val agentJar = Path.of(System.getProperty("katton.probe.agentJar"))
        URLClassLoader(arrayOf(agentJar.toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { isolatedLoader ->
            val isolatedAgent = Class.forName(KattonAgent::class.java.name, true, isolatedLoader)
            assertNotSame(KattonAgent::class.java, isolatedAgent)
            val instrumentation = isolatedAgent.getMethod("findInstrumentation").invoke(null)
            assertNotNull(instrumentation)
        }
    }
}
