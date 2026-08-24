package top.katton.util

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventConcurrencyTest {
    private class TestArgument(val cancelThisInvocation: Boolean) : CancellableEventArg()

    @Test
    fun `cancellation state is isolated across concurrent dispatches`() {
        val event = createCancellableUnit<TestArgument>()
        val entered = CyclicBarrier(2)
        val leaving = CyclicBarrier(2)
        event += { argument ->
            entered.await(5, TimeUnit.SECONDS)
            if (argument.cancelThisInvocation) argument.cancel()
            leaving.await(5, TimeUnit.SECONDS)
        }

        Executors.newFixedThreadPool(2).use { executor ->
            val cancelled = executor.submit<Boolean> {
                event(TestArgument(cancelThisInvocation = true))
                event.isCanceled()
            }
            val allowed = executor.submit<Boolean> {
                event(TestArgument(cancelThisInvocation = false))
                event.isCanceled()
            }

            assertTrue(cancelled.get(10, TimeUnit.SECONDS))
            assertFalse(allowed.get(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `nested dispatch does not erase outer cancellation`() {
        val event = createCancellableUnit<TestArgument>()
        var nested = false
        event += { argument ->
            if (!nested) {
                argument.cancel()
                nested = true
                event(TestArgument(cancelThisInvocation = false))
                nested = false
            }
        }

        event(TestArgument(cancelThisInvocation = true))
        assertTrue(event.isCanceled())
    }

    @Test
    fun `dispatcher is rebuilt on mutation rather than every invocation`() {
        var dispatcherBuilds = 0
        val event = create<Int, Int> { handlers ->
            dispatcherBuilds++
            { argument -> handlers.sumOf { handler -> handler(argument) } }
        }
        event += { it + 1 }

        assertEquals(3, event(2).getOrThrow())
        assertEquals(5, event(4).getOrThrow())
        assertEquals(1, dispatcherBuilds)

        event += { it * 2 }
        assertEquals(7, event(2).getOrThrow())
        assertEquals(2, dispatcherBuilds)
    }
}
