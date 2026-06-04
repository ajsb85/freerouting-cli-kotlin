package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the nets (unrouted connections) of a board.
 */
class BoardStatisticsNets : Serializable {
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null

    @SerializedName("class_count")
    @JvmField
    var classCount: Int? = null
}
