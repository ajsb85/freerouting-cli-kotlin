package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the clearance violations of a board.
 */
class BoardStatisticsClearanceViolations : Serializable {
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null
}
