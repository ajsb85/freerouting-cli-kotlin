package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the layers of a board.
 */
class BoardStatisticsLayers : Serializable {
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null

    @SerializedName("signal_count")
    @JvmField
    var signalCount: Int? = null
}
