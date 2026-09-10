package top.katton.dev

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import top.katton.util.ScriptExecutionContext

/** Observes existing output without replacing appenders or redirecting System.out. */
internal class DevLogAppender : AbstractAppender("KattonDevelopment", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY) {
    override fun append(event: LogEvent) {
        if (!event.level.isMoreSpecificThan(org.apache.logging.log4j.Level.INFO)) return
        val identity = ScriptExecutionContext.currentIdentity()
        val text = "[${event.loggerName}] ${event.message.formattedMessage}" + (event.thrown?.let { "\n${it.stackTraceToString()}" } ?: "")
        DevEvents.emit(event.level.name(), text, identity?.syncId, ScriptExecutionContext.currentScriptRevision())
    }
    fun attach() {
        start()
        val context = LogManager.getContext(false) as? LoggerContext ?: return
        context.configuration.rootLogger.addAppender(this, org.apache.logging.log4j.Level.INFO, null)
        context.updateLoggers()
    }
    fun detach() {
        val context = LogManager.getContext(false) as? LoggerContext
        context?.configuration?.rootLogger?.removeAppender(name)
        context?.updateLoggers()
        stop()
    }
}
