package app.freerouting.core

import app.freerouting.management.RoutingJobScheduler
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.UUID

/**
 * Represents a user session that contains the jobs that will be processed by the router.
 */
class Session : Serializable {

    @SerializedName("id")
    @JvmField
    val id: UUID = UUID.randomUUID()

    @SerializedName("user_id")
    @JvmField
    val userId: UUID

    @SerializedName("host")
    @JvmField
    val host: String

    @Transient
    @JvmField
    var isGuiSession: Boolean = false

    /**
     * Creates a new session.
     *
     * @param userId The user ID that the session belongs to.
     */
    constructor(userId: UUID, host: String?) {
        this.userId = userId

        // Normalise: treat null or blank as the safe default
        val tempHost = if (host == null || host.isBlank()) {
            "Unknown/0.0"
        } else {
            host
        }
        this.host = tempHost

        // check if the host value is valid (it must contain the host name and version separated by "/")
        if (tempHost.split("/").size != 2) {
            throw IllegalArgumentException("Invalid host value: '$tempHost'. It must contain the host name and version separated by '/'.")
        }
    }

    /**
     * Adds a job to the session.
     *
     * @param routingJob The job to add.
     */
    fun addJob(routingJob: RoutingJob) {
        RoutingJobScheduler.getInstance().enqueueJob(routingJob)
    }
}
