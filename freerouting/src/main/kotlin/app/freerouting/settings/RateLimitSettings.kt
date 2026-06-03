package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Generic fixed-window rate-limit settings.
 */
class RateLimitSettings : Serializable {
    @SerializedName("enabled")
    @JvmField
    var enabled: Boolean? = false

    @SerializedName("requests_per_window")
    @JvmField
    var requestsPerWindow: Int? = 120

    @SerializedName("window_seconds")
    @JvmField
    var windowSeconds: Int? = 60
}
