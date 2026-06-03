package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class GuiSettings : Serializable {
    @SerializedName("enabled")
    @JvmField
    var isEnabled: Boolean? = true

    @SerializedName("running")
    @Transient
    @JvmField
    var isRunning: Boolean? = false

    @SerializedName("input_directory")
    @JvmField
    var inputDirectory: String = ""

    @SerializedName("dialog_confirmation_timeout")
    @JvmField
    var dialogConfirmationTimeout: Int = 5

    @Transient
    @JvmField
    var exitWhenFinished: Boolean = false
}
