package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the components of a board.
 */
class BoardStatisticsItems : Serializable {
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null

    @SerializedName("trace_count")
    @JvmField
    var traceCount: Int? = null

    @SerializedName("via_count")
    @JvmField
    var viaCount: Int? = null

    @SerializedName("conduction_area_count")
    @JvmField
    var conductionAreaCount: Int? = null

    @SerializedName("drill_item_count")
    @JvmField
    var drillItemCount: Int? = null

    @SerializedName("pin_count")
    @JvmField
    var pinCount: Int? = null

    @SerializedName("component_count")
    @JvmField
    var componentOutlineCount: Int? = null

    @SerializedName("other_count")
    @JvmField
    var otherCount: Int? = null
}
