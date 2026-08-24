package top.katton.engine

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

data class ScriptIssue(
    val title: String,
    val detail: String
)

object ScriptIssueReporter {
    private const val MAX_HISTORY = 32
    private val logger = LoggerFactory.getLogger(ScriptIssueReporter::class.java)
    private val listeners = CopyOnWriteArrayList<(ScriptIssue) -> Unit>()

    @Volatile
    private var lastIssue: ScriptIssue? = null
    private val history = ArrayDeque<ScriptIssue>()

    @JvmStatic
    fun report(title: String, detail: String) {
        val issue = ScriptIssue(title, detail.trim().ifBlank { "No details were reported." })
        lastIssue = issue
        synchronized(history) {
            history.addLast(issue)
            while (history.size > MAX_HISTORY) history.removeFirst()
        }
        listeners.forEach { listener ->
            runCatching { listener(issue) }
                .onFailure { logger.warn("Failed to notify script issue listener", it) }
        }
    }

    @JvmStatic
    fun lastIssue(): ScriptIssue? = lastIssue

    @JvmStatic
    fun history(): List<ScriptIssue> = synchronized(history) { history.toList() }

    @JvmStatic
    fun clearHistory() {
        synchronized(history) { history.clear() }
        lastIssue = null
    }

    fun addListener(listener: (ScriptIssue) -> Unit) {
        listeners += listener
    }
}
