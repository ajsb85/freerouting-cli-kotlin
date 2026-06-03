package app.freerouting.settings

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Weights that control how the autorouter scores a board state.
 *
 * <p>All fields are nullable so that the {@code SettingsMerger} / {@code ReflectionUtil.copyFields}
 * pipeline can distinguish "this source has no opinion" (null) from "this source explicitly sets
 * a value".  Hard-coded defaults must live exclusively in
 * {@link app.freerouting.settings.sources.DefaultSettings}.
 *
 * <p>Naming conventions:
 * <ul>
 *   <li><b>penalty</b> — subtracted when a quality constraint is violated (unrouted net, DRC
 *       violation, bend).  These appear in the board-score formula.</li>
 *   <li><b>costs</b> — subtracted as an absolute cost proportional to resource usage (via count,
 *       trace length).  These also appear in the board-score formula.</li>
 *   <li><b>startRipupCosts</b> — a routing-control parameter passed to the maze-search engine;
 *       it does <em>not</em> appear in the board-score formula.</li>
 * </ul>
 */
class RouterScoringSettings : Serializable, Cloneable {

  // The cost of 1 mm of trace length if the trace is routed in the preferred
  // direction, defined for each layer.
  @Transient
  @JvmField
  var preferredDirectionTraceCost: DoubleArray? = null

  // The cost of 1 mm of trace length if the trace is routed in the undesired
  // direction, defined for each layer.
  @Transient
  @JvmField
  var undesiredDirectionTraceCost: DoubleArray? = null

  // The cost of 1 mm of trace length if the trace is routed in the preferred
  // direction.
  @SerializedName("default_preferred_direction_trace_cost")
  @JvmField
  var defaultPreferredDirectionTraceCost: Double? = null

  // The cost of 1 mm of trace length if the trace is routed in the undesired
  // direction.
  @SerializedName("default_undesired_direction_trace_cost")
  @JvmField
  var defaultUndesiredDirectionTraceCost: Double? = null

  // The cost of a via on a regular (non-plane) net.
  @SerializedName("via_costs")
  @JvmField
  var viaCosts: Int? = null

  // The cost of a via if the via is placed on a plane.
  @SerializedName("plane_via_costs")
  @JvmField
  var planeViaCosts: Int? = null

  /**
   * Base ripup cost for the first ripup-and-reroute pass.
   * This is a routing-control parameter multiplied by the pass number inside
   * {@code BatchAutorouter}; it does NOT appear in the board-score formula.
   */
  @SerializedName("start_ripup_costs")
  @JvmField
  var startRipupCosts: Int? = null

  // The penalty for an unrouted net.
  @SerializedName("unrouted_net_penalty")
  @JvmField
  var unroutedNetPenalty: Float? = null

  // The penalty for a clearance violation.
  @SerializedName("clearance_violation_penalty")
  @JvmField
  var clearanceViolationPenalty: Float? = null

  // The penalty for a bend.
  @SerializedName("bend_penalty")
  @JvmField
  var bendPenalty: Float? = null

  /**
   * Creates a deep copy of this RouterScoringSettings object.
   * All fields including arrays are cloned.
   *
   * @return A new RouterScoringSettings instance with the same values
   */
  public override fun clone(): RouterScoringSettings {
    try {
      val result = super.clone() as RouterScoringSettings
      // Clone array fields to ensure deep copy
      if (this.preferredDirectionTraceCost != null) {
        result.preferredDirectionTraceCost = this.preferredDirectionTraceCost!!.clone()
      }
      if (this.undesiredDirectionTraceCost != null) {
        result.undesiredDirectionTraceCost = this.undesiredDirectionTraceCost!!.clone()
      }
      return result
    } catch (e: CloneNotSupportedException) {
      throw AssertionError("Clone not supported", e)
    }
  }
}
