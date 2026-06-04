package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the bends (of traces) of a board.
 */
class BoardStatisticsBends : Serializable {
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null

    @SerializedName("90_degree_count")
    @JvmField
    var ninetyDegreeCount: Int? = null

    @SerializedName("45_degree_count")
    @JvmField
    var fortyFiveDegreeCount: Int? = null

    @SerializedName("other_angle_count")
    @JvmField
    var otherAngleCount: Int? = null
}
