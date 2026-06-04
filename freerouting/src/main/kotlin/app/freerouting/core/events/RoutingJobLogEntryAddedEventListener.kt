package app.freerouting.core.events

fun interface RoutingJobLogEntryAddedEventListener {
    fun onLogEntryAdded(event: RoutingJobLogEntryAddedEvent)
}
