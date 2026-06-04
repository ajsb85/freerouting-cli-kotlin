package app.freerouting.core

/**
 * Defines the stop state of a StoppableThread.
 */
enum class StopRequestState {
    /**
     * No stop is requested.
     */
    NONE,
    /**
     * Only the auto-router is requested to stop.
     */
    AUTO_ROUTER_ONLY,
    /**
     * The entire thread is requested to stop.
     */
    ALL
}
