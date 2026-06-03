package app.freerouting.drc

import com.google.gson.annotations.SerializedName

/**
 * Represents a single item involved in a DRC violation, matching KiCad's JSON schema.
 */
class DrcViolationItem(
    /**
     * Human-readable description of the item
     */
    @SerializedName("description")
    @JvmField
    val description: String,

    /**
     * Position of the item
     */
    @SerializedName("pos")
    @JvmField
    val pos: DrcPosition?,

    /**
     * Unique identifier of the item
     */
    @SerializedName("uuid")
    @JvmField
    val uuid: String
)
