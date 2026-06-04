package app.freerouting.core.events

fun interface RoutingJobUpdatedEventListener {
    fun onRoutingJobUpdated(event: RoutingJobUpdatedEvent)
}
