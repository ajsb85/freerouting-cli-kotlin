package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Configuration for the dedicated MCP server.
 */
class McpServerSettings : Serializable {
    @SerializedName("enabled")
    @JvmField
    var isEnabled: Boolean? = false

    @SerializedName("running")
    @Transient
    @JvmField
    var isRunning: Boolean? = false

    @SerializedName("http_allowed")
    @JvmField
    var isHttpAllowed: Boolean? = true

    @SerializedName("endpoints")
    @JvmField
    var endpoints: Array<String> = arrayOf("http://127.0.0.1:37964")

    @SerializedName("authentication")
    @JvmField
    var authentication: ApiAuthenticationSettings = ApiAuthenticationSettings()

    @SerializedName("cors_origins")
    @JvmField
    var cors_origins: String = ""

    @SerializedName("rate_limit")
    @JvmField
    var rateLimit: RateLimitSettings = RateLimitSettings()

    @SerializedName("target_api_base_url")
    @JvmField
    var targetApiBaseUrl: String = "http://127.0.0.1:37864"
}
