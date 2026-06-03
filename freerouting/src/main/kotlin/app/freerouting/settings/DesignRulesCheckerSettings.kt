package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class DesignRulesCheckerSettings : Serializable, Cloneable {

    @SerializedName("enabled")
    @Transient
    @JvmField
    var enabled: Boolean = false

    @SerializedName("include_warnings")
    @JvmField
    var includeWarnings: Boolean = true

    @SerializedName("include_errors")
    @JvmField
    var includeErrors: Boolean = true

    public override fun clone(): DesignRulesCheckerSettings {
        val clone = DesignRulesCheckerSettings()
        clone.enabled = this.enabled
        clone.includeWarnings = this.includeWarnings
        clone.includeErrors = this.includeErrors
        return clone
    }
}
