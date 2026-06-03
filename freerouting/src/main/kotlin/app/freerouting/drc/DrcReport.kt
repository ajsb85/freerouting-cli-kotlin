package app.freerouting.drc

import com.google.gson.annotations.SerializedName
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Represents a complete DRC report in KiCad's JSON schema format. Based on https://schemas.kicad.org/drc.v1.json
 */
class DrcReport(
    @SerializedName("coordinate_units")
    @JvmField
    val coordinate_units: String,

    @SerializedName("source")
    @JvmField
    val source: String,

    @SerializedName("freerouting_version")
    @JvmField
    val freerouting_version: String
) {
    /**
     * JSON schema URL
     */
    @SerializedName("\$schema")
    @JvmField
    val `$schema`: String = "https://schemas.kicad.org/drc.v1.json"

    /**
     * Date and time when the report was generated
     */
    @SerializedName("date")
    @JvmField
    val date: String = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /**
     * Version of KiCad that generated the report (this is "N/A" for Freerouting)
     */
    @SerializedName("kicad_version")
    @JvmField
    val kicad_version: String = "N/A"

    /**
     * List of unconnected items (empty for now)
     */
    @SerializedName("unconnected_items")
    @JvmField
    val unconnected_items: MutableList<DrcViolation> = ArrayList()

    /**
     * List of violations found
     */
    @SerializedName("violations")
    @JvmField
    val violations: MutableList<DrcViolation> = ArrayList()

    /**
     * Schematic parity issues (empty for now)
     */
    @SerializedName("schematic_parity")
    @JvmField
    val schematic_parity: MutableList<Any> = ArrayList()

    /**
     * Add a violation to the report
     */
    fun addViolation(violation: DrcViolation) {
        this.violations.add(violation)
    }

    /**
     * Add an unconnected item to the report
     */
    fun addUnconnectedItem(item: DrcViolation) {
        this.unconnected_items.add(item)
    }
}
