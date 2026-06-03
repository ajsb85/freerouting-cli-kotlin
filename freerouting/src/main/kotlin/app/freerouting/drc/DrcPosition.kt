package app.freerouting.drc

import com.google.gson.annotations.SerializedName

/**
 * Represents a position in the DRC report, matching KiCad's JSON schema.
 */
class DrcPosition(
    /**
     * X coordinate in the coordinate units specified in the report
     */
    @SerializedName("x")
    @JvmField
    val x: Double,

    /**
     * Y coordinate in the coordinate units specified in the report
     */
    @SerializedName("y")
    @JvmField
    val y: Double
)
