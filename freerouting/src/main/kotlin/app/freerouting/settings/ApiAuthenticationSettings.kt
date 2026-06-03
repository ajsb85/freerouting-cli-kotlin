package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class ApiAuthenticationSettings : Serializable {
    @SerializedName("enabled")
    @JvmField
    var isEnabled: Boolean? = true

    @SerializedName("providers")
    @JvmField
    var providers: String = ""

    @SerializedName("google_sheets")
    @JvmField
    var googleSheets: GoogleSheetsProviderSettings = GoogleSheetsProviderSettings()
}
