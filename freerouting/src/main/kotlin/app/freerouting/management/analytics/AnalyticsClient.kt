package app.freerouting.management.analytics

import app.freerouting.management.analytics.dto.Properties
import app.freerouting.management.analytics.dto.Traits
import java.io.IOException

interface AnalyticsClient {

    /**
     * Identify a user. This is only called once at the beginning of the session.
     *
     * @param userId The user's unique identifier.
     */
    @Throws(IOException::class)
    fun identify(userId: String?, anonymousId: String?, traits: Traits?)

    /**
     * Track an event. The event can be anything that happens during the session.
     *
     * @param userId     The user's unique identifier.
     * @param event      The event name.
     * @param properties Additional properties to include with the event.
     */
    @Throws(IOException::class)
    fun track(userId: String?, anonymousId: String?, event: String?, properties: Properties?)

    /**
     * Enable or disable the client. When disabled, the client will not send any events.
     *
     * @param enabled Whether the client should be enabled.
     */
    fun setEnabled(enabled: Boolean)
}
