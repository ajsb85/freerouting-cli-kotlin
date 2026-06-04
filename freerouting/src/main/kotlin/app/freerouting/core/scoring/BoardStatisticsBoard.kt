package app.freerouting.core.scoring

import com.google.gson.annotations.SerializedName
import java.awt.geom.Rectangle2D
import java.io.Serializable

/**
 * Basic parameters of a board.
 */
class BoardStatisticsBoard : Serializable {
    @SerializedName("bounding_box")
    @JvmField
    var boundingBox: Rectangle2D.Float? = null

    @SerializedName("size")
    @JvmField
    var size: Rectangle2D.Float? = null
}
