package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class GoogleSheetsProviderSettings : Serializable {
    @SerializedName("google_api_key")
    @JvmField
    var googleApiKey: String? = null

    @SerializedName("sheet_url")
    @JvmField
    var sheetUrl: String? = null
}
