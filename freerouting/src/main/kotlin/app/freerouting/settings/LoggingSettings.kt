package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class LoggingSettings : Serializable {

    @SerializedName("console")
    @JvmField
    val console: ConsoleLoggingSettings = ConsoleLoggingSettings()

    @SerializedName("file")
    @JvmField
    val file: FileLoggingSettings = FileLoggingSettings()

    class ConsoleLoggingSettings : Serializable {
        @SerializedName("enabled")
        @JvmField
        var enabled: Boolean = true

        @SerializedName("level")
        @JvmField
        var level: String = "INFO"
    }

    class FileLoggingSettings : Serializable {
        @SerializedName("enabled")
        @JvmField
        var enabled: Boolean = true

        @SerializedName("level")
        @JvmField
        var level: String = "INFO"

        @SerializedName("location")
        @JvmField
        var location: String? = null
    }
}
