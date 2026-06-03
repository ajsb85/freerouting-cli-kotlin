package app.freerouting.logger

import app.freerouting.geometry.planar.Point
import java.time.Instant

/** Payload describing an interesting trace event. */
class TraceEvent(
    val method: String,
    val operation: String,
    val message: String,
    val impactedItems: String,
    val impactedPoints: Array<Point>?,
    val timestamp: Instant
)
