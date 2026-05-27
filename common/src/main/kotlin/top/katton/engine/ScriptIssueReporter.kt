package top.katton.engine

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

data class ScriptIssue(
    val title: String,
    val detail: String
)

object ScriptIssueReporter {
    private val logger = LoggerFactory.getLogger(ScriptIssueReporter::class.java)
    private val listeners = CopyOnWriteArrayList<(ScriptIssue) -> Unit>()

    @Volatile
    private var lastIssue: ScriptIssue? = null

    @JvmStatic
    fun report(title: String, detail: String) {
        val issue = ScriptIssue(title, detail.trim().ifBlank { "No details were reported." })
        lastIssue = issue
        listeners.forEach { listener ->
            runCatching { listener(issue) }
                .onFailure { logger.warn("Failed to notify script issue listener", it) }
        }
    }

    @JvmStatic
    fun lastIssue(): ScriptIssue? = lastIssue

    fun addListener(listener: (ScriptIssue) -> Unit) {
        listeners += listener
    }
}
