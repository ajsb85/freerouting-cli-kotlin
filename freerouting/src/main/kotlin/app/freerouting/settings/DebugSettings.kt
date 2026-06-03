package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Settings for debugging the routing engine.
 */
class DebugSettings : Serializable {

    @SerializedName("enable_detailed_logging")
    @JvmField
    var enableDetailedLogging: Boolean = false

    @SerializedName("single_step_execution")
    @JvmField
    var singleStepExecution: Boolean = false

    @SerializedName("trace_insertion_delay")
    @JvmField
    var traceInsertionDelay: Int = 0

    @SerializedName("filter_by_net")
    @JvmField
    var filterByNet: MutableSet<String> = HashSet()

    @SerializedName("operation_filters")
    @JvmField
    var operationFilters: Array<String> = arrayOf(
        "insert_trace_segment", "remove_trace_segment", "insert_trace_failure",
        "remove_tail", "insert_trace", "remove_trace", "insert_via", "remove_via"
    )

    /**
     * Checks if the given net number or name is permitted by the filter.
     * If the filter is empty, all nets are permitted.
     */
    fun isNetPermitted(netNo: Int, netName: String?): Boolean {
        if (filterByNet.isEmpty()) {
            return true
        }
        val netNoStr = netNo.toString()
        // Check "1", "Net #1", "Net#1"
        return filterByNet.contains(netNoStr) ||
                filterByNet.contains("Net #$netNo") ||
                filterByNet.contains("Net#$netNo") ||
                (netName != null && filterByNet.contains(netName.lowercase()))
    }
}
