package top.katton.util

import kotlin.test.Test
import kotlin.test.assertSame

class ResultTest {
    @Test
    fun `common immutable successes reuse wrappers`() {
        assertSame(Result.success(), Result.success(Unit))
        assertSame(Result.success(true), Result.success(true))
        assertSame(Result.success(false), Result.success(false))
        assertSame(Result.success<Any?>(null), Result.success<Any?>(null))
    }

    @Test
    fun `custom equality cannot impersonate a cached success value`() {
        val impostor = object {
            override fun equals(other: Any?): Boolean = true
        }

        assertSame(impostor, Result.success(impostor).getOrThrow())
    }
}
