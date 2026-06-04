package app.freerouting.management.analytics.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Analytics tracking payload for user events and identification")
class Payload {

    @Schema(description = "Unique identifier for the authenticated user", example = "user_12345")
    @JvmField var userId: String? = null

    @Schema(description = "Anonymous identifier for tracking users without authentication", example = "anon_67890")
    @JvmField var anonymousId: String? = null

    @Schema(description = "Context information about the tracking event")
    @JvmField var context: Context? = null

    @Schema(description = "Name of the event being tracked", example = "job_started")
    @JvmField var event: String? = null

    @Schema(description = "User traits for identification")
    @JvmField var traits: Traits? = null

    @Schema(description = "Additional properties associated with the event")
    @JvmField var properties: Properties? = null
}
