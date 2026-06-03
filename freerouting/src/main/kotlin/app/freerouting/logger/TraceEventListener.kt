package app.freerouting.logger

/** Listener for FRLogger trace events that were marked as interesting. */
fun interface TraceEventListener {
    fun onTraceEvent(event: TraceEvent)
}
