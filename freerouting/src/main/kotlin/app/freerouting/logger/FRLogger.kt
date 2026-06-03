package app.freerouting.logger

import app.freerouting.Freerouting
import app.freerouting.board.BasicBoard
import app.freerouting.debug.DebugControl
import app.freerouting.geometry.planar.Point
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.util.HashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Provides centralized logging functionality for the application.
 * Wraps Log4j2 and maintains an internal list of log entries for UI display.
 */
object FRLogger {

    @JvmField
    val defaultFloatFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))

    @JvmField
    val defaultSignedFloatFormat = DecimalFormat("+0.00;-0.00", DecimalFormatSymbols(Locale.US))

    private val perfData = HashMap<Int, Instant>()
    private val logEntries = LogEntries()
    private val traceEventListeners = CopyOnWriteArrayList<TraceEventListener>()

    @JvmField
    var granularTraceEnabled = false

    private var logger: Logger? = null
    private var enabled = true

    /**
     * Enables or disables logging globally.
     *
     * @param value true to enable logging, false to disable.
     */
    @JvmStatic
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /**
     * Formats a duration in seconds into a human-readable string (hours, minutes,
     * seconds).
     *
     * @param totalSeconds The total duration in seconds.
     * @return A formatted string representing the duration.
     */
    @JvmStatic
    fun formatDuration(totalSeconds: Double): String {
        var seconds = totalSeconds
        var minutes = seconds / 60.0
        var hours = minutes / 60.0

        hours = Math.floor(hours)
        minutes = Math.floor(minutes % 60.0)
        seconds = seconds % 60.0

        val hoursText = if (hours > 0) "${hours.toInt()} ${if (hours == 1.0) "hour" else "hours"} " else ""
        val minutesText = if (minutes > 0) "${minutes.toInt()} ${if (minutes == 1.0) "minute" else "minutes"} " else ""

        return "$hoursText$minutesText${defaultFloatFormat.format(seconds)} seconds"
    }

    /**
     * Formats a score with details about incomplete items and violations.
     *
     * @param score      The routing score.
     * @param incomplete The number of unrouted items.
     * @param violations The number of design rule violations.
     * @return A formatted string representing the score and any issues.
     */
    @JvmStatic
    fun formatScore(score: Float, incomplete: Int, violations: Int): String {
        val sb = StringBuilder(defaultFloatFormat.format(score.toDouble()))

        // Only include unrouted and violations if they exist
        if (incomplete > 0 || violations > 0) {
            sb.append(" (")

            // Add unrouted info only if there are any
            if (incomplete > 0) {
                sb.append(incomplete).append(" unrouted")
            }

            // Add separator if both unrouted and violations exist
            if (incomplete > 0 && violations > 0) {
                sb.append(" and ")
            }

            // Add violations info only if there are any
            if (violations > 0) {
                sb.append(violations).append(if (violations == 1) " violation" else " violations")
            }

            sb.append(")")
        }

        return sb.toString()
    }

    @JvmStatic
    fun buildTracePayload(event: String, phase: String?, action: String?, kvPairs: String?): String {
        val sb = StringBuilder()
        sb.append("event=").append(event)
        if (!phase.isNullOrEmpty()) {
            sb.append(" phase=").append(phase)
        }
        if (!action.isNullOrEmpty()) {
            sb.append(" action=").append(action)
        }
        if (!kvPairs.isNullOrEmpty()) {
            sb.append(" ").append(kvPairs)
        }
        return sb.toString()
    }

    @JvmStatic
    fun formatNetLabel(board: BasicBoard?, netNoArr: IntArray?): String {
        if (netNoArr == null || netNoArr.isEmpty()) {
            return "No net"
        }
        val sb = StringBuilder()
        for (i in netNoArr.indices) {
            if (i > 0) {
                sb.append(", ")
            }
            sb.append(formatNetLabel(board, netNoArr[i]))
        }
        return sb.toString()
    }

    @JvmStatic
    fun formatNetLabel(board: BasicBoard?, netNo: Int): String {
        if (board == null || board.rules == null || board.rules.nets == null) {
            return "Net #$netNo (Unknown)"
        }
        if (netNo <= board.rules.nets.max_net_no()) {
            return board.rules.nets.get(netNo).toString()
        }
        return "Net #$netNo (Unknown)"
    }

    /**
     * Records the start time for a performance trace.
     *
     * @param perfId A unique identifier for the operation being traced (often the
     *               method name).
     */
    @JvmStatic
    fun traceEntry(perfId: String) {
        if (!enabled) {
            return
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }

        perfData[perfId.hashCode()] = Instant.now()
    }

    /**
     * Records the end of a performance trace and logs the duration.
     *
     * @param perfId A unique identifier for the operation being traced.
     * @return The duration of the operation in seconds.
     */
    @JvmStatic
    fun traceExit(perfId: String): Double {
        if (!enabled) {
            return 0.0
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }

        return traceExit(perfId, null)
    }

    /**
     * Records the end of a performance trace with an optional result object and
     * logs the duration.
     *
     * @param perfId A unique identifier for the operation being traced.
     * @param result An optional result object to include in the log message.
     * @return The duration of the operation in seconds.
     */
    @JvmStatic
    fun traceExit(perfId: String, result: Any?): Double {
        if (!enabled) {
            return 0.0
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }

        var timeElapsed: Long = 0
        try {
            val startInstant = perfData[perfId.hashCode()]
            if (startInstant != null) {
                timeElapsed = Duration.between(startInstant, Instant.now()).toMillis()
            }
        } catch (_: Exception) {
            // we can ignore this exception
        }

        perfData.remove(perfId.hashCode())
        if (timeElapsed < 0) {
            timeElapsed = 0
        }

        val replacement = result?.toString() ?: "(null)"
        val logMessage = "Method '${perfId.replace("{}", replacement)}' was performed in ${formatDuration(timeElapsed / 1000.0)}."

        trace(logMessage)

        return timeElapsed / 1000.0
    }

    /**
     * Logs an INFO message.
     *
     * @param msg   The message to log.
     * @param topic An optional topic UUID associated with the message.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun info(msg: String, topic: UUID?): LogEntry? {
        val logEntry = logEntries.add(LogEntryType.Info, msg, topic)

        if (!enabled) {
            return null
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }

        logger?.info(msg)

        return logEntry
    }

    /**
     * Logs an INFO message without a topic.
     *
     * @param msg The message to log.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun info(msg: String): LogEntry? {
        return info(msg, null)
    }

    /**
     * Logs a WARNING message.
     *
     * @param msg   The message to log.
     * @param topic An optional topic UUID associated with the message.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun warn(msg: String, topic: UUID?): LogEntry? {
        val logEntry = logEntries.add(LogEntryType.Warning, msg, topic)

        if (!enabled) {
            return null
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }

        logger?.warn(msg)

        return logEntry
    }

    /**
     * Logs a WARNING message without a topic.
     *
     * @param msg The message to log.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun warn(msg: String): LogEntry? {
        return warn(msg, null)
    }

    /**
     * Logs a DEBUG message.
     *
     * @param msg   The message to log.
     * @param topic An optional topic UUID associated with the message.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun debug(msg: String, topic: UUID?): LogEntry? {
        if (!enabled) {
            return null
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }

        logger?.debug(msg)

        return null
    }

    /**
     * Logs a DEBUG message without a topic.
     *
     * @param msg The message to log.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun debug(msg: String): LogEntry? {
        return debug(msg, null)
    }

    /**
     * Logs an ERROR message with an exception.
     *
     * @param msg       The message to log.
     * @param topic     An optional topic UUID associated with the message.
     * @param exception The exception to log.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun error(msg: String, topic: UUID?, exception: Throwable?): LogEntry? {
        val logEntry = logEntries.add(LogEntryType.Error, msg, topic, exception)

        if (!enabled) {
            return null
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }

        if (exception == null) {
            logger?.error(msg)
        } else {
            logger?.error(msg, exception)
        }

        return logEntry
    }

    /**
     * Logs an ERROR message with an exception, but without a topic.
     *
     * @param msg       The message to log.
     * @param exception The exception to log.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun error(msg: String, exception: Throwable?): LogEntry? {
        return error(msg, null, exception)
    }

    /**
     * Checks if TRACE level logging is enabled.
     *
     * @return true if TRACE logging is enabled, false otherwise.
     */
    @JvmStatic
    fun isTraceEnabled(): Boolean {
        if (!enabled) {
            return false
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }
        return logger?.isTraceEnabled ?: false
    }

    /**
     * Logs a TRACE message.
     *
     * @param msg The message to log.
     * @return The created LogEntry.
     */
    @JvmStatic
    fun trace(msg: String): LogEntry? {
        if (!enabled) {
            return null
        }
        if (logger == null) {
            logger = LogManager.getLogger(Freerouting::class.java)
        }

        logger?.trace(msg)

        return null
    }

    @JvmStatic
    fun trace(method: String, operation: String, message: String, impactedItems: String): Boolean {
        return trace(method, operation, message, impactedItems, null)
    }

    /**
     * Logs a granular TRACE message and triggers a debug check.
     *
     * @param method        The method name where the log originates (e.g.
     *                      "InsertFoundConnectionAlgo").
     * @param operation     The operation type (e.g. "insertion", "removal").
     * @param message       The details of the log message.
     * @param impactedItems A string describing the impacted items, separated by comma
     *                      (e.g. "Net #1,Trace #123").
     *                      This string is used by DebugControl to filter execution.
     * @param impactedPoints List of points that the operation focused on
     */
    @JvmStatic
    fun trace(method: String, operation: String, message: String, impactedItems: String, impactedPoints: Array<Point>?): Boolean {
        if (enabled) {
            if (logger == null) {
                logger = LogManager.getLogger(Freerouting::class.java)
            }

            if (granularTraceEnabled && (impactedItems.isEmpty() || DebugControl.getInstance().isInterested(impactedItems))) {
                val formattedMessage = String.format("[%s] [%s] %s: %s", method, operation, message, impactedItems)
                logger?.trace(formattedMessage)
            }
        }

        val wasInterestingTraceEvent = DebugControl.getInstance().check(operation, impactedItems)
        if (wasInterestingTraceEvent) {
            publishTraceEvent(TraceEvent(method, operation, message, impactedItems, impactedPoints, Instant.now()))
        }

        return wasInterestingTraceEvent
    }

    /**
     * Disables logging.
     */
    @JvmStatic
    fun disableLogging() {
        enabled = false
    }

    /**
     * Gets the collection of log entries recorded by this logger.
     *
     * @return The LogEntries collection.
     */
    @JvmStatic
    fun getLogEntries(): LogEntries {
        return logEntries
    }

    /**
     * Gets the underlying Log4j2 Logger instance.
     *
     * @return The Logger instance.
     */
    @JvmStatic
    fun getLogger(): Logger {
        return logger ?: LogManager.getLogger(Freerouting::class.java).also { logger = it }
    }

    /** Adds a listener that will be notified of interesting trace events. */
    @JvmStatic
    fun addTraceEventListener(listener: TraceEventListener) {
        traceEventListeners.add(listener)
    }

    /** Removes a listener from the list of trace event listeners. */
    @JvmStatic
    fun removeTraceEventListener(listener: TraceEventListener) {
        traceEventListeners.remove(listener)
    }

    private fun publishTraceEvent(event: TraceEvent) {
        for (listener in traceEventListeners) {
            listener.onTraceEvent(event)
        }
    }
}
