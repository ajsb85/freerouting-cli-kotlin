package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the components of a board.
 */
class BoardStatisticsComponents : Serializable {
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null
}
