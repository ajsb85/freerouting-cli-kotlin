package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.io.Serializable

class BoardStatisticsConnections : Serializable {
    @SerializedName("maximum_count")
    @JvmField
    var maximumCount: Int? = null

    @SerializedName("incomplete_count")
    @JvmField
    var incompleteCount: Int? = null
}
