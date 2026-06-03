package app.freerouting.logger

import java.time.Instant
import java.util.ArrayList
import java.util.UUID

class LogEntries {
    private val entries: MutableList<LogEntry> = ArrayList()
    private val listeners: MutableList<LogEntryAddedListener> = ArrayList()

    val warningCount: Int
        get() {
            synchronized(entries) {
                return entries.count { it.type == LogEntryType.Warning }
            }
        }

    val errorCount: Int
        get() {
            synchronized(entries) {
                return entries.count { it.type == LogEntryType.Error }
            }
        }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }

    val asString: String
        get() {
            synchronized(entries) {
                return entries.joinToString(separator = "\n", postfix = "\n") { it.toString() }
            }
        }

    fun get(): Array<String> {
        synchronized(entries) {
            return entries.map { it.toString() }.toTypedArray()
        }
    }

    fun getEntries(entriesSince: Instant?, topic: UUID?): Array<LogEntry> {
        synchronized(entries) {
            return entries.filter { e ->
                (entriesSince == null || e.timestamp.isAfter(entriesSince)) &&
                (topic == null || (e.topic != null && e.topic == topic))
            }.toTypedArray()
        }
    }

    fun add(type: LogEntryType, message: String, topic: UUID?): LogEntry {
        return add(type, message, topic, null)
    }

    fun add(type: LogEntryType, message: String, topic: UUID?, exception: Throwable?): LogEntry {
        val logEntry = LogEntry(type, message, exception, topic)
        synchronized(entries) {
            entries.add(logEntry)
        }
        for (listener in listeners) {
            listener.logEntryAdded(logEntry)
        }
        return logEntry
    }

    fun addLogEntryAddedListener(listener: LogEntryAddedListener) {
        listeners.add(listener)
    }

    fun removeLogEntryAddedListener(listener: LogEntryAddedListener) {
        listeners.remove(listener)
    }

    fun interface LogEntryAddedListener {
        fun logEntryAdded(logEntry: LogEntry)
    }
}
