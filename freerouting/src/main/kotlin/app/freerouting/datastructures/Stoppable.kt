package app.freerouting.datastructures

/**
 * Interface for stoppable threads.
 */
interface Stoppable {

    /**
     * Requests this thread to be stopped.
     */
    fun requestStop()

    /**
     * Returns true, if this thread is requested to be stopped.
     */
    val isStopRequested: Boolean
}
