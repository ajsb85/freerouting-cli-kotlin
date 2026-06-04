package app.freerouting.core

import app.freerouting.datastructures.Stoppable

/**
 * Used for running an interactive action in a separate thread, that can be stopped by the user.
 */
abstract class StoppableThread : Thread, Stoppable {

    private var stopRequestState = StopRequestState.NONE

    /**
     * Creates a new instance of InteractiveActionThread
     */
    protected constructor() : super()

    protected abstract fun thread_action()

    override fun run() {
        thread_action()
    }

    // Request the thread to stop including the fanout, auto-router and optimizer tasks
    @Synchronized
    override fun requestStop() {
        this.stopRequestState = StopRequestState.ALL
    }

    override val isStopRequested: Boolean
        @Synchronized
        get() = this.stopRequestState == StopRequestState.ALL

    // Request the thread to stop the auto-router, but continue with the optimizer and other tasks
    @Synchronized
    fun request_stop_auto_router() {
        if (this.stopRequestState == StopRequestState.NONE) {
            this.stopRequestState = StopRequestState.AUTO_ROUTER_ONLY
        }
    }

    // Check if the thread should stop the auto router
    @Synchronized
    fun is_stop_auto_router_requested(): Boolean {
        return this.stopRequestState != StopRequestState.NONE
    }
}
