package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the pads of a board.
 */
class BoardStatisticsPads : Serializable {
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null
}
