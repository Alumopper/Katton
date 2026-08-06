package top.katton.engine

import top.katton.api.ClientPhase
import top.katton.api.InvocationReason
import top.katton.api.ServerPhase
import top.katton.pack.ScriptPackScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptLifecyclePolicyTest {
    @Test
    fun `scope and phase matrix is explicit`() {
        assertTrue(ScriptLifecyclePolicy.isValid(ScriptPackScope.GLOBAL, ScriptEnvironment.SERVER, ServerPhase.BOOTSTRAP.name))
        assertTrue(ScriptLifecyclePolicy.isValid(ScriptPackScope.WORLD, ScriptEnvironment.SERVER, ServerPhase.READY.name))
        assertFalse(ScriptLifecyclePolicy.isValid(ScriptPackScope.WORLD, ScriptEnvironment.SERVER, ServerPhase.BOOTSTRAP.name))
        assertTrue(ScriptLifecyclePolicy.isValid(ScriptPackScope.GLOBAL, ScriptEnvironment.CLIENT, ClientPhase.READY.name))
        assertTrue(ScriptLifecyclePolicy.isValid(ScriptPackScope.SERVER_CACHE, ScriptEnvironment.CLIENT, ClientPhase.JOINED.name))
        assertFalse(ScriptLifecyclePolicy.isValid(ScriptPackScope.SERVER_CACHE, ScriptEnvironment.CLIENT, ClientPhase.READY.name))
    }

    @Test
    fun `hot reload obeys world flag and forces server cache replay`() {
        assertFalse(ScriptLifecyclePolicy.shouldInvoke(ScriptPackScope.GLOBAL, InvocationReason.HOT_RELOAD, replay = true))
        assertFalse(ScriptLifecyclePolicy.shouldInvoke(ScriptPackScope.WORLD, InvocationReason.HOT_RELOAD, replay = false))
        assertTrue(ScriptLifecyclePolicy.shouldInvoke(ScriptPackScope.WORLD, InvocationReason.HOT_RELOAD, replay = true))
        assertTrue(ScriptLifecyclePolicy.shouldInvoke(ScriptPackScope.SERVER_CACHE, InvocationReason.HOT_RELOAD, replay = false))
        assertTrue(ScriptLifecyclePolicy.shouldInvoke(ScriptPackScope.WORLD, InvocationReason.INITIAL_LOAD, replay = false))
    }
}
