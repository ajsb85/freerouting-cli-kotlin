package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the vias of a board.
 */
class BoardStatisticsVias : Serializable {
    // Total count of vias
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null

    // Through-hole vias are spanning all layers
    @SerializedName("through_hole_count")
    @JvmField
    var throughHoleCount: Int? = null

    // Blind vias are connecting outer to inner layers
    @SerializedName("blind_count")
    @JvmField
    var blindCount: Int? = null

    // Buried vias are connecting inner layers only
    @SerializedName("buried_count")
    @JvmField
    var buriedCount: Int? = null
}
