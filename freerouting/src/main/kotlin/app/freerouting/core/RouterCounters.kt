package app.freerouting.core

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Router-related counters that reflect the progress of the router.
 */
class RouterCounters : Serializable {
    // The number of items on the board that are in the queue to be routed in the current pass
    @SerializedName("pass_count")
    @JvmField
    var passCount: Int? = null

    // The number of items on the board that are in the queue to be routed in the current pass
    @SerializedName("queued_to_be_routed_count")
    @JvmField
    var queuedToBeRoutedCount: Int? = null

    // The number of items on the board that got successfully routed in this pass
    @SerializedName("routed_count")
    @JvmField
    var routedCount: Int? = null

    // The number of items on the board that were skipped in this pass
    @SerializedName("skipped_count")
    @JvmField
    var skippedCount: Int? = null

    // The number of items on the board that were ripped in this pass
    @SerializedName("ripped_count")
    @JvmField
    var rippedCount: Int? = null

    // The number of items on the board that were failed to be routed in this pass
    @SerializedName("failed_to_be_routed_count")
    @JvmField
    var failedToBeRoutedCount: Int? = null

    // The number of items on the board that are still in the ratsnest (so they are not yet routed)
    @SerializedName("incomplete_count")
    @JvmField
    var incompleteCount: Int? = null

    // Optional phase marker (for example "autoroute" or "fanout") used by GUI progress rendering.
    @SerializedName("phase")
    @JvmField
    var phase: String? = null

    // Optional fanout-only counter: number of additional vias inserted in the current pass.
    @SerializedName("fanout_extra_vias_count")
    @JvmField
    var fanoutExtraViasCount: Int? = null
}
