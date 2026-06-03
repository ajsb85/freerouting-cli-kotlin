package app.freerouting.settings

import app.freerouting.autoroute.BoardUpdateStrategy
import app.freerouting.autoroute.ItemSelectionStrategy
import com.google.gson.annotations.SerializedName
import java.io.Serializable

class RouterOptimizerSettings : Serializable, Cloneable {

    @SerializedName("enabled")
    @JvmField
    var enabled: Boolean? = null

    @SerializedName("algorithm")
    @JvmField
    var algorithm: String? = null

    @SerializedName("max_passes")
    @JvmField
    var maxPasses: Int? = null

    @SerializedName("max_threads")
    @JvmField
    var maxThreads: Int? = null

    @SerializedName("improvement_threshold")
    @JvmField
    var optimizationImprovementThreshold: Float? = null

    @Transient
    @JvmField
    var boardUpdateStrategy: BoardUpdateStrategy? = null

    @Transient
    @JvmField
    var hybridRatio: String? = null

    @Transient
    @JvmField
    var itemSelectionStrategy: ItemSelectionStrategy? = null

    /**
     * Creates a deep copy of this RouterOptimizerSettings object.
     * All fields including transient ones are cloned.
     *
     * @return A new RouterOptimizerSettings instance with the same values
     */
    public override fun clone(): RouterOptimizerSettings {
        try {
            val result = super.clone() as RouterOptimizerSettings
            result.boardUpdateStrategy = this.boardUpdateStrategy
            result.hybridRatio = this.hybridRatio
            result.itemSelectionStrategy = this.itemSelectionStrategy
            return result
        } catch (e: CloneNotSupportedException) {
            throw AssertionError("Clone not supported", e)
        }
    }
}
