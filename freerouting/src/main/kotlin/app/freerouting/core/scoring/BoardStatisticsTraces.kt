package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Statistics of the traces (routed connections) of a board.
 */
class BoardStatisticsTraces : Serializable {
    // The total number of traces.
    @SerializedName("total_count")
    @JvmField
    var totalCount: Int? = null

    // The total number of segments in all traces.
    @SerializedName("total_segment_count")
    @JvmField
    var totalSegmentCount: Int? = null

    // The total length of all traces.
    @SerializedName("total_length")
    @JvmField
    var totalLength: Float? = null

    /**
     * Total trace length normalised to millimetres, regardless of the DSN internal coordinate
     * system. [totalLength] is stored in raw board units (whose scale depends on the DSN
     * resolution), making it unsuitable for the board-score formula which uses mm-denominated
     * weights. [totalLengthMm] is derived from [totalLength] during BoardStatistics
     * construction and should be used everywhere a real-world mm value is needed.
     */
    @SerializedName("total_length_mm")
    @JvmField
    var totalLengthMm: Float? = null

    @SerializedName("total_weighted_length")
    @JvmField
    var totalWeightedLength: Float? = null

    // The average length of the traces.
    @SerializedName("average_length")
    @JvmField
    var averageLength: Float? = null

    // The total vertical length of all traces.
    @SerializedName("total_vertical_length")
    @JvmField
    var totalVerticalLength: Float? = null

    // The total horizontal length of all traces.
    @SerializedName("total_horizontal_length")
    @JvmField
    var totalHorizontalLength: Float? = null

    // The total angled (non-horizontal and non-vertical) length of all traces.
    @SerializedName("total_angled_length")
    @JvmField
    var totalAngledLength: Float? = null
}
