package app.freerouting.logger

import java.time.Instant
import java.util.Locale
import java.util.UUID

/// <summary>
/// Represents a log entry.
/// </summary>
class LogEntry(
    @JvmField val type: LogEntryType,
    @JvmField val message: String,
    @Transient @JvmField val exception: Throwable?,
    @JvmField val topic: UUID?
) {
    /// <summary>
    /// Timestamp of the log entry.
    /// </summary>
    @JvmField
    val timestamp: Instant = Instant.now()

    override fun toString(): String {
        return "%-7s".format(Locale.US, type.toString().uppercase(Locale.US)) + " " + message
    }
}
