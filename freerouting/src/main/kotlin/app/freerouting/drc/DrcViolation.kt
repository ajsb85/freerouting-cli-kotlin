package app.freerouting.drc

import com.google.gson.annotations.SerializedName

/**
 * Represents a single DRC violation, matching KiCad's JSON schema.
 */
class DrcViolation(
    /**
     * Type of violation (e.g., "clearance", "via_dangling", etc.)
     */
    @SerializedName("type")
    @JvmField
    val type: String,

    /**
     * Human-readable description of the violation
     */
    @SerializedName("description")
    @JvmField
    val description: String,

    /**
     * Severity of the violation ("error", "warning", "ignore")
     */
    @SerializedName("severity")
    @JvmField
    val severity: String,

    /**
     * Items involved in the violation
     */
    @SerializedName("items")
    @JvmField
    val items: List<DrcViolationItem>
)
