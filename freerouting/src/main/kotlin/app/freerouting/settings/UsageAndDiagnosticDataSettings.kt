package app.freerouting.settings

import app.freerouting.management.TextManager
import com.google.gson.annotations.SerializedName
import java.io.Serializable

class UsageAndDiagnosticDataSettings : Serializable {
    @SerializedName("disable_analytics")
    @JvmField
    var disableAnalytics: Boolean = false

    @SerializedName("bigquery_service_account_key")
    @Transient
    @JvmField
    var bigqueryServiceAccountKey: String? = null

    @SerializedName("logger_key")
    @Transient
    @JvmField
    var loggerKey: String = TextManager.generateRandomAlphanumericString(32)
}
