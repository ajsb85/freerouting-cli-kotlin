package app.freerouting.io.specctra.parser

import app.freerouting.board.Communication
import app.freerouting.board.LayerStructure
import app.freerouting.board.RoutingBoard
import app.freerouting.core.RoutingJob
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.PolylineShape
import app.freerouting.rules.BoardRules

/**
 * Narrow callback interface used internally by the DSN parser to delegate board creation and
 * board-lifecycle events. This interface deliberately has *no* dependency on the
 * `interactive` or `gui` packages so that the parser stays self-contained.
 *
 * The only production implementation is the package-private `MinimalBoardManager`
 * nested inside [ReadScopeParameter].
 */
internal interface BoardParserCallback {

    /** Returns the board that was created, or `null` if the board has not been constructed yet. */
    fun get_routing_board(): RoutingBoard?

    /**
     * Called by the structure reader once it has parsed the board outline, layers, and rules.
     * Implementations should create and store a new [RoutingBoard] from the given parameters.
     */
    fun create_board(
        p_bounding_box: IntBox,
        p_layer_structure: LayerStructure,
        p_outline_shapes: Array<PolylineShape>,
        p_outline_clearance_class_name: String?,
        p_rules: BoardRules,
        p_board_communication: Communication
    )

    /**
     * Called after board creation to populate per-layer manual trace widths from the default net
     * class. Implementations that have no interactive settings may provide a no-op body.
     */
    fun initialize_manual_trace_half_widths()

    /**
     * Returns the active [RoutingJob] associated with this parse context, or `null`
     * when operating in isolation (e.g. pure DSN-reader mode without a routing job).
     */
    fun getCurrentRoutingJob(): RoutingJob?
}
