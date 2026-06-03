package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class UserProfileSettings : Serializable {
    @SerializedName("id")
    @JvmField
    var userId: String? = null

    @SerializedName("email")
    @JvmField
    var userEmail: String = ""

    @SerializedName("allow_telemetry")
    @JvmField
    var isTelemetryAllowed: Boolean? = true

    @SerializedName("allow_contact")
    @JvmField
    var isContactAllowed: Boolean? = true
}
