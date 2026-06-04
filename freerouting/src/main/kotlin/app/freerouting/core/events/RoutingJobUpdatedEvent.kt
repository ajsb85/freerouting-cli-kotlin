package app.freerouting.core.events

import app.freerouting.core.RoutingJob
import java.util.EventObject

class RoutingJobUpdatedEvent(
    source: Any,
    @JvmField val job: RoutingJob
) : EventObject(source) {

    fun getJob(): RoutingJob = job
}
