package app.freerouting.io.specctra.parser

import app.freerouting.board.AngleRestriction
import app.freerouting.board.BasicBoard
import app.freerouting.board.BoardObservers
import app.freerouting.board.Communication
import app.freerouting.board.RoutingBoard
import app.freerouting.board.Unit
import app.freerouting.core.RoutingJob
import app.freerouting.datastructures.IdentificationNumberGenerator
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.PolylineShape
import app.freerouting.rules.BoardRules
import app.freerouting.rules.DefaultItemClearanceClasses
import app.freerouting.settings.RouterSettings
import java.util.ArrayList
import java.util.LinkedList

/**
 * Helper class that contains some structured properties and helper functions for the DSN parser.
 */
class ReadScopeParameter(
    @JvmField val scanner: IJFlexScanner,
    @JvmField val observers: BoardObservers?,
    @JvmField val item_id_no_generator: IdentificationNumberGenerator?
) {

    @JvmField internal val board_handling: BoardParserCallback = MinimalBoardManager()
    @JvmField val netlist: NetList = NetList()

    /**
     * Warnings collected during DSN parsing (e.g. skipped wires, missing padstacks, degenerate
     * geometry). Callers can retrieve these via [getWarnings] after the read completes.
     */
    @JvmField val warnings: MutableList<String> = ArrayList()

    /**
     * Collection of elements of class PlaneInfo. The plane cannot be inserted directly into the boards, because the layers may not be read completely.
     */
    @JvmField val plane_list: MutableCollection<PlaneInfo> = LinkedList()

    /**
     * Component placement information. It is filled while reading the placement scope and can be evaluated after reading the library and network scope.
     */
    @JvmField val placement_list: MutableCollection<ComponentPlacement> = LinkedList()

    @JvmField val constants: MutableCollection<Array<String>> = LinkedList()

    /**
     * The names of the via padstacks filled while reading the structure scope and evaluated after reading the library scope.
     */
    @JvmField var via_padstack_names: MutableCollection<String>? = null

    @JvmField var via_at_smd_allowed: Boolean = false

    @JvmField var snap_angle: AngleRestriction = AngleRestriction.FORTYFIVE_DEGREE

    /**
     * The logical parts are used for pin and gate swaw
     */
    @JvmField val logical_part_mappings: MutableCollection<PartLibrary.LogicalPartMapping> = LinkedList()

    @JvmField val logical_parts: MutableCollection<PartLibrary.LogicalPart> = LinkedList()

    /**
     * The following objects are from the parser scope.
     */
    @JvmField var string_quote: String = "\""

    @JvmField var host_cad: String? = null
    @JvmField var host_version: String? = null

    @JvmField var dsn_file_generated_by_host: Boolean = true

    /** Set to `false` by the structure reader when the board outline is absent.  */
    @JvmField var board_outline_ok: Boolean = true

    @JvmField var write_resolution: Communication.SpecctraParserInfo.WriteResolution? = null

    /**
     * The following objects will be initialised when the structure scope is read.
     */
    @JvmField var coordinate_transform: CoordinateTransform? = null
    @JvmField var layer_structure: LayerStructure? = null

    /** Nullable — only populated when an `(autoroute ...)` scope is present in the DSN file.  */
    @JvmField var autoroute_settings: RouterSettings? = null

    @JvmField var unit: Unit = Unit.MIL

    @JvmField var resolution: Int = 100 // default resolution

    /**
     * Returns the board that was created during parsing, or `null` if parsing
     * has not yet reached the board-construction step.
     */
    val board: BasicBoard?
        get() = board_handling.get_routing_board()



    /**
     * Returns an unmodifiable view of the warnings collected during DSN parsing.
     * The list is populated as the file is read; call this method after the read completes.
     */
    fun getWarnings(): List<String> {
        return java.util.Collections.unmodifiableList(warnings)
    }

    // -------------------------------------------------------------------------
    // Minimal internal shim — satisfies the BoardParserCallback contract during
    // parsing without requiring a HeadlessBoardManager or a RoutingJob.
    // -------------------------------------------------------------------------
    private class MinimalBoardManager : BoardParserCallback {
        private var board: RoutingBoard? = null

        override fun get_routing_board(): RoutingBoard? {
            return board
        }

        override fun create_board(
            p_bounding_box: IntBox,
            p_layer_structure: app.freerouting.board.LayerStructure,
            p_outline_shapes: Array<PolylineShape>,
            p_outline_clearance_class_name: String?,
            p_rules: BoardRules,
            p_board_communication: Communication
        ) {
            val outlineClearanceNo: Int
            if (p_outline_clearance_class_name != null && p_rules.clearance_matrix != null) {
                outlineClearanceNo = Math.max(
                    0,
                    p_rules.clearance_matrix.get_no(p_outline_clearance_class_name)
                )
            } else {
                outlineClearanceNo = p_rules.get_default_net_class()
                    .default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.AREA)
            }
            board = RoutingBoard(
                p_bounding_box,
                p_layer_structure,
                p_outline_shapes,
                outlineClearanceNo,
                p_rules,
                p_board_communication
            )
        }

        override fun initialize_manual_trace_half_widths() {
            // no-op: no InteractiveSettings in headless shim
        }

        override fun getCurrentRoutingJob(): RoutingJob? {
            return null
        }
    }

    // -------------------------------------------------------------------------
    /**
     * Information for inserting a plane
     */
    class PlaneInfo(
        @JvmField val area: Shape.ReadAreaScopeResult?,
        @JvmField val net_name: String?
    )
}
