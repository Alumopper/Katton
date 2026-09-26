package top.katton.engine

import top.katton.api.LOGGER
import java.util.concurrent.TimeUnit

/** Wall-clock timings for the stages that can delay world loading or hot reload. */
internal object ScriptTiming {
    fun <T> measure(stage: String, detail: String, action: () -> T): T {
        val started = System.nanoTime()
        var completed = false
        try {
            return action().also { completed = true }
        } finally {
            LOGGER.info(
                "Katton script timing stage={} detail={} elapsedMs={} outcome={} thread={}",
                stage,
                detail,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                if (completed) "ok" else "failed",
                Thread.currentThread().name
            )
        }
    }
}
