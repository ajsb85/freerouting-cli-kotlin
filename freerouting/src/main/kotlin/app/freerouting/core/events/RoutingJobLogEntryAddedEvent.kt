package app.freerouting.core.events

import app.freerouting.core.RoutingJob
import app.freerouting.logger.LogEntry
import java.util.EventObject

class RoutingJobLogEntryAddedEvent(
    source: Any,
    @JvmField val job: RoutingJob,
    @JvmField val logEntry: LogEntry
) : EventObject(source) {

    fun getJob(): RoutingJob = job

    fun getLogEntry(): LogEntry = logEntry
}
