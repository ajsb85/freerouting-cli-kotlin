package app.freerouting.io.specctra.parser

import app.freerouting.board.BasicBoard
import app.freerouting.board.ConductionArea
import app.freerouting.board.FixedState
import app.freerouting.board.Item
import app.freerouting.board.ItemSelectionFilter
import app.freerouting.board.PolylineTrace
import app.freerouting.board.RoutingBoard
import app.freerouting.board.Trace
import app.freerouting.board.Via
import app.freerouting.core.Padstack
import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.geometry.planar.Area
import app.freerouting.geometry.planar.FloatPoint
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.IntPoint
import app.freerouting.geometry.planar.Line
import app.freerouting.geometry.planar.Point
import app.freerouting.geometry.planar.Polygon
import app.freerouting.geometry.planar.Polyline
import app.freerouting.logger.FRLogger
import app.freerouting.rules.BoardRules
import app.freerouting.rules.DefaultItemClearanceClasses
import app.freerouting.rules.NetClass
import java.io.IOException
import java.util.LinkedList

/**
 * Class for reading and writing wiring scopes from dsn-files.
 */
class Wiring : ScopeKeyword("wiring") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            try {
                next_token = p_par.scanner.next_token()
            } catch (_: IOException) {
                FRLogger.warn("Wiring.read_scope: IO error scanning file at '" + p_par.scanner.get_scope_identifier() + "'")
                return false
            }
            if (next_token == null) {
                FRLogger.warn("Wiring.read_scope: unexpected end of file at '" + p_par.scanner.get_scope_identifier() + "'")
                return false
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }
            var read_ok = true
            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.WIRE) {
                    read_wire_scope(p_par)
                } else if (next_token === Keyword.VIA) {
                    read_ok = read_via_scope(p_par)
                } else {
                    skip_scope(p_par.scanner)
                }
            }
            if (!read_ok) {
                return false
            }
        }
        val board = p_par.board_handling.get_routing_board()!!
        for (i in 1..board.rules.nets.max_net_no()) {
            try {
                board.normalize_traces(i)
            } catch (e: Exception) {
                val msg = "Wiring: normalization of net '" + (board.rules.nets.get(i)?.name ?: "") + "' failed"
                FRLogger.debug(msg)
                p_par.warnings.add(msg)
            }
        }
        return true
    }

    private fun read_wire_scope(p_par: ReadScopeParameter): Item? {
        var net_id: Net.Id? = null
        var clearance_class_name: String? = null
        var fixed = FixedState.UNFIXED
        var path: Path? = null // Used, if a trace is read.
        var border_shape: Shape? = null // Used, if a conduction area is read.
        val hole_list = LinkedList<Shape>()
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            try {
                next_token = p_par.scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e)
                return null
            }
            if (next_token == null) {
                FRLogger.warn("Wiring.read_wire_scope: unexpected end of file at '" + p_par.scanner.get_scope_identifier() + "'")
                return null
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }
            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.POLYGON_PATH) {
                    path = Shape.read_polygon_path_scope(p_par.scanner, p_par.layer_structure!!)
                } else if (next_token === Keyword.POLYLINE_PATH) {
                    path = Shape.read_polyline_path_scope(p_par.scanner, p_par.layer_structure!!)
                } else if (next_token === Keyword.RECTANGLE) {
                    border_shape = Shape.read_rectangle_scope(p_par.scanner, p_par.layer_structure!!)
                } else if (next_token === Keyword.POLYGON) {
                    border_shape = Shape.read_polygon_scope(p_par.scanner, p_par.layer_structure!!)
                } else if (next_token === Keyword.CIRCLE) {
                    border_shape = Shape.read_circle_scope(p_par.scanner, p_par.layer_structure!!)
                } else if (next_token === Keyword.WINDOW) {
                    val hole_shape = Shape.read_scope(p_par.scanner, p_par.layer_structure!!)
                    if (hole_shape != null) {
                        hole_list.add(hole_shape)
                    }
                    // overread the closing bracket
                    try {
                        next_token = p_par.scanner.next_token()
                    } catch (e: IOException) {
                        FRLogger.error("Wiring.read_wire_scope: IO error scanning file", e)
                        return null
                    }
                    if (next_token !== Keyword.CLOSED_BRACKET) {
                        FRLogger.warn("Wiring.read_wire_scope: closing bracket expected at '" + p_par.scanner.get_scope_identifier() + "'")
                        return null
                    }
                } else if (next_token === Keyword.NET) {
                    net_id = read_net_id(p_par.scanner)
                } else if (next_token === Keyword.CLEARANCE_CLASS) {
                    clearance_class_name = DsnFile.read_string_scope(p_par.scanner)
                } else if (next_token === Keyword.TYPE) {
                    fixed = calc_fixed(p_par.scanner)
                } else {
                    skip_scope(p_par.scanner)
                }
            }
        }
        if (path == null && border_shape == null) {
            val msg = "Wiring: wire has no shape at '" + p_par.scanner.get_scope_identifier() + "'"
            FRLogger.warn(msg)
            p_par.warnings.add(msg)
            return null
        }
        val board = p_par.board_handling.get_routing_board()!!

        var net_class = board.rules.get_default_net_class()
        val found_nets = get_subnets(net_id, board.rules)
        val net_no_arr = IntArray(found_nets.size)
        var curr_index = 0
        for (curr_net in found_nets) {
            net_no_arr[curr_index] = curr_net.net_number
            net_class = curr_net.get_class()
            ++curr_index
        }
        var clearance_class_no = -1
        if (clearance_class_name != null) {
            clearance_class_no = board.rules.clearance_matrix.get_no(clearance_class_name)
        }
        val layer_no: Int
        val half_width: Int
        if (path != null) {
            layer_no = path.layer.no
            half_width = Math.round(p_par.coordinate_transform!!.dsn_to_board(path.width / 2)).toInt()
        } else {
            layer_no = border_shape!!.layer.no
            half_width = 0
        }
        if (layer_no < 0 || layer_no >= board.get_layer_count()) {
            val layerName = if (path != null) path.layer.name else border_shape!!.layer.name
            val msg = "Wiring: wire ignored — unknown layer '" + layerName + "' at '" + p_par.scanner.get_scope_identifier() + "'"
            FRLogger.warn(msg)
            p_par.warnings.add(msg)
            return null
        }

        val bounding_box = board.get_bounding_box()

        var result: Item? = null
        if (border_shape != null) {
            if (clearance_class_no < 0) {
                clearance_class_no = net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.AREA)
            }
            val area = LinkedList<Shape>()
            area.add(border_shape)
            area.addAll(hole_list)
            val conduction_area = Shape.transform_area_to_board(area, p_par.coordinate_transform!!)
            result = board.insert_conduction_area(conduction_area, layer_no, net_no_arr, clearance_class_no, false, fixed)
        } else if (path is PolygonPath) {
            if (clearance_class_no < 0) {
                clearance_class_no = net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.TRACE)
            }
            val corner_arr: Array<Point>
            try {
                corner_arr = Array(path.coordinate_arr.size / 2) { i ->
                    val curr_point = doubleArrayOf(path.coordinate_arr[2 * i], path.coordinate_arr[2 * i + 1])
                    val curr_corner = p_par.coordinate_transform!!.dsn_to_board(curr_point)
                    if (!bounding_box.contains(curr_corner)) {
                        val msg = "Wiring: wire corner (" + curr_point[0].toInt() + "," + curr_point[1].toInt() +
                                ") is outside board bounds at '" + p_par.scanner.get_scope_identifier() + "'"
                        FRLogger.warn(msg)
                        p_par.warnings.add(msg)
                        throw IllegalArgumentException(msg)
                    }
                    curr_corner.round()
                }
            } catch (e: IllegalArgumentException) {
                return null
            }

            val polygon = Polygon(corner_arr)

            // if it doesn't have two different points, it's not a valid polygon, so we must skip it
            val polygonCorners = polygon.corner_array()
            // A wire is degenerate if it has fewer than 2 corners, or if all corners map to the same
            // point (zero-length trace). This covers both the 2-point identical case and the N-point
            // all-equal case (e.g. KiCad 4.0.7 exports 3 identical vertices for some degenerate shapes).
            // Such traces cause infinite normalization cycles and must be skipped.
            var hasDistinctCorner = false
            for (i in 1 until polygonCorners.size) {
                if (!polygonCorners[i].equals(polygonCorners[0])) {
                    hasDistinctCorner = true
                    break
                }
            }
            val isDegenerate = polygonCorners.size < 2 || !hasDistinctCorner
            if (!isDegenerate) {
                val trace_polyline = Polyline(polygon)
                // Traces are not yet normalized here because cycles may be removed premature.
                result = board.insert_trace_without_cleaning(trace_polyline, layer_no, half_width, net_no_arr, clearance_class_no, fixed)
            } else {
                val msg = "Wiring: degenerate wire trace skipped (all " + polygonCorners.size +
                        " corners are identical — zero-length trace) on layer '" + path.layer.name +
                        "'. This is likely a DSN export issue in your EDA tool."
                FRLogger.debug(msg)
                p_par.warnings.add(msg)
            }
        } else if (path is PolylinePath) {
            if (clearance_class_no < 0) {
                clearance_class_no = net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.TRACE)
            }
            val line_arr = Array(path.coordinate_arr.size / 4) { i ->
                var curr_point = doubleArrayOf(path.coordinate_arr[4 * i], path.coordinate_arr[4 * i + 1])
                val curr_a = p_par.coordinate_transform!!.dsn_to_board(curr_point)
                curr_point = doubleArrayOf(path.coordinate_arr[4 * i + 2], path.coordinate_arr[4 * i + 3])
                val curr_b = p_par.coordinate_transform!!.dsn_to_board(curr_point)
                Line(curr_a.round(), curr_b.round())
            }
            val trace_polyline = Polyline(line_arr)
            result = board.insert_trace_without_cleaning(trace_polyline, layer_no, half_width, net_no_arr, clearance_class_no, fixed)
        } else {
            FRLogger.warn("Wiring.read_wire_scope: unexpected Path subclass at '" + p_par.scanner.get_scope_identifier() + "'")
            return null
        }
        if (result != null && result.net_count() == 0) {
            try_correct_net(result)
        }
        return result
    }

    /**
     * Maybe trace of type turret without net in Mentor design. Try to assign the net by calculating the overlaps.
     */
    private fun try_correct_net(p_item: Item) {
        if (p_item !is Trace) {
            return
        }
        val contacts = p_item.get_normal_contacts(p_item.first_corner(), true)
        contacts.addAll(p_item.get_normal_contacts(p_item.last_corner(), true))
        var corrected_net_no = 0
        for (curr_contact in contacts) {
            if (curr_contact.net_count() == 1) {
                corrected_net_no = curr_contact.get_net_no(0)
                break
            }
        }
        if (corrected_net_no != 0) {
            p_item.assign_net_no(corrected_net_no)
        }
    }

    private fun read_via_scope(p_par: ReadScopeParameter): Boolean {
        try {
            var fixed = FixedState.UNFIXED
            // read the padstack name
            var next_token = p_par.scanner.next_token()
            if (next_token !is String) {
                FRLogger.warn("Wiring.read_via_scope: padstack name expected at '" + p_par.scanner.get_scope_identifier() + "'")
                return false
            }
            val padstack_name = next_token
            p_par.scanner.set_scope_identifier(padstack_name)
            // read the location
            val location = DoubleArray(2)
            for (i in 0 until 2) {
                next_token = p_par.scanner.next_token()
                if (next_token is Double) {
                    location[i] = next_token
                } else if (next_token is Int) {
                    location[i] = next_token.toDouble()
                } else {
                    FRLogger.warn("Wiring.read_via_scope: number expected at '" + p_par.scanner.get_scope_identifier() + "'")
                    return false
                }
            }
            var net_id: Net.Id? = null
            var clearance_class_name: String? = null
            while (true) {
                val prev_token = next_token
                next_token = p_par.scanner.next_token()
                if (next_token == null) {
                    FRLogger.warn("Wiring.read_via_scope: unexpected end of file at '" + p_par.scanner.get_scope_identifier() + "'")
                    return false
                }
                if (next_token === Keyword.CLOSED_BRACKET) {
                    // end of scope
                    break
                }
                if (prev_token === Keyword.OPEN_BRACKET) {
                    if (next_token === Keyword.NET) {
                        net_id = read_net_id(p_par.scanner)
                    } else if (next_token === Keyword.CLEARANCE_CLASS) {
                        clearance_class_name = DsnFile.read_string_scope(p_par.scanner)
                    } else if (next_token === Keyword.TYPE) {
                        fixed = calc_fixed(p_par.scanner)
                    } else {
                        skip_scope(p_par.scanner)
                    }
                }
            }
            val board = p_par.board_handling.get_routing_board()!!
            val curr_padstack = board.library.padstacks!!.get(padstack_name)
            if (curr_padstack == null) {
                val msg = "Wiring: via padstack '" + padstack_name + "' not found at '" + p_par.scanner.get_scope_identifier() + "'"
                FRLogger.warn(msg)
                p_par.warnings.add(msg)
                return false
            }
            var net_class = board.rules.get_default_net_class()
            val found_nets = get_subnets(net_id, board.rules)
            if (net_id != null && found_nets.isEmpty()) {
                val msg = "Wiring: via net '" + net_id.name + "' not found at '" + p_par.scanner.get_scope_identifier() + "'"
                FRLogger.warn(msg)
                p_par.warnings.add(msg)
            }
            val net_no_arr = IntArray(found_nets.size)
            var curr_index = 0
            for (curr_net in found_nets) {
                net_no_arr[curr_index] = curr_net.net_number
                net_class = curr_net.get_class()
                curr_index++
            }
            var clearance_class_no = -1
            if (clearance_class_name != null) {
                clearance_class_no = board.rules.clearance_matrix.get_no(clearance_class_name)
            }
            if (clearance_class_no < 0) {
                clearance_class_no = net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.VIA)
            }
            val board_location = p_par.coordinate_transform!!.dsn_to_board(location).round()
            if (via_exists(board_location, curr_padstack, net_no_arr, board)) {
                val msg = "Wiring: duplicate via skipped at (" + board_location.x + ", " + board_location.y + ")"
                FRLogger.warn(msg)
                p_par.warnings.add(msg)
            } else {
                val attach_allowed = p_par.via_at_smd_allowed && curr_padstack.attach_allowed
                board.insert_via(curr_padstack, board_location, net_no_arr, clearance_class_no, fixed, attach_allowed)
            }
            return true
        } catch (e: IOException) {
            FRLogger.error("Wiring.read_via_scope: IO error scanning file", e)
            return false
        }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter) {
            p_par.file.start_scope()
            p_par.file.write("wiring")
            // write the wires
            val board_wires = p_par.board.get_traces()
            for (curr_board_wire in board_wires) {
                write_wire_scope(p_par, curr_board_wire)
            }
            val board_vias = p_par.board.get_vias()
            for (curr_via in board_vias) {
                write_via_scope(p_par, curr_via)
            }
            // write the conduction areas
            val it2 = p_par.board.item_list.start_read_object()
            while (true) {
                val curr_ob = p_par.board.item_list.read_object(it2) ?: break
                if (curr_ob !is ConductionArea) {
                    continue
                }
                if (!p_par.board.layer_structure.arr[curr_ob.get_layer()].is_signal) {
                    // This conduction areas are written in the structure scope.
                    continue
                }
                write_conduction_area_scope(p_par, curr_ob)
            }
            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_via_scope(p_par: WriteScopeParameter, p_via: Via) {
            val via_padstack = p_via.get_padstack()
            val via_location = p_via.get_center().to_float()
            val via_coor = p_par.coordinate_transform.board_to_dsn(via_location)
            val via_net = if (p_via.net_count() > 0) {
                val net_no = p_via.get_net_no(0)
                p_par.board.rules.nets.get(net_no)
            } else {
                null
            }
            p_par.file.start_scope()
            p_par.file.write("via ")
            p_par.identifier_type.write(via_padstack.name, p_par.file)
            for (i in via_coor.indices) {
                p_par.file.write(" ")
                p_par.file.write(via_coor[i].toString())
            }
            if (via_net != null) {
                write_net(via_net, p_par.file, p_par.identifier_type)
            }
            Rule.write_item_clearance_class(
                p_par.board.rules.clearance_matrix.get_name(p_via.clearance_class_no()) ?: "",
                p_par.file,
                p_par.identifier_type
            )
            write_fixed_state(p_par.file, p_via.get_fixed_state())
            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_wire_scope(p_par: WriteScopeParameter, p_wire: Trace) {
            if (p_wire !is PolylineTrace) {
                FRLogger.warn("Wiring.write_wire_scope: trace type not yet implemented")
                return
            }
            val layer_no = p_wire.get_layer()
            val board_layer = p_par.board.layer_structure.arr[layer_no]
            val curr_layer = Layer(board_layer.name, layer_no, board_layer.is_signal)
            val wire_width = p_par.coordinate_transform.board_to_dsn((2 * p_wire.get_half_width()).toDouble())
            var wire_net: app.freerouting.rules.Net? = null
            if (p_wire.net_count() > 0) {
                wire_net = p_par.board.rules.nets.get(p_wire.get_net_no(0))
            }
            if (wire_net == null) {
                FRLogger.warn("Wiring.write_wire_scope: net not found")
                return
            }
            p_par.file.start_scope()
            p_par.file.write("wire")

            if (p_par.compat_mode) {
                val float_corner_arr = p_wire.polyline().corner_approx_arr()
                val coors = p_par.coordinate_transform.board_to_dsn(float_corner_arr)
                val curr_path = PolygonPath(curr_layer, wire_width, coors)
                curr_path.write_scope(p_par.file, p_par.identifier_type)
            } else {
                val coors = p_par.coordinate_transform.board_to_dsn(p_wire.polyline().arr)
                val curr_path = PolylinePath(curr_layer, wire_width, coors)
                curr_path.write_scope(p_par.file, p_par.identifier_type)
            }
            write_net(wire_net, p_par.file, p_par.identifier_type)
            Rule.write_item_clearance_class(
                p_par.board.rules.clearance_matrix.get_name(p_wire.clearance_class_no()) ?: "",
                p_par.file,
                p_par.identifier_type
            )
            write_fixed_state(p_par.file, p_wire.get_fixed_state())
            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_conduction_area_scope(p_par: WriteScopeParameter, p_conduction_area: ConductionArea) {
            val net_count = p_conduction_area.net_count()
            if (net_count != 1) {
                FRLogger.warn("Plane.write_scope: unexpected net count")
                return
            }
            val curr_net = p_par.board.rules.nets.get(p_conduction_area.get_net_no(0))!!
            val curr_area = p_conduction_area.get_area()
            val layer_no = p_conduction_area.get_layer()
            val board_layer = p_par.board.layer_structure.arr[layer_no]
            val conduction_layer = Layer(board_layer.name, layer_no, board_layer.is_signal)
            val boundary_shape: app.freerouting.geometry.planar.Shape
            val holes: Array<out app.freerouting.geometry.planar.Shape>
            if (curr_area is app.freerouting.geometry.planar.Shape) {
                boundary_shape = curr_area
                holes = emptyArray()
            } else {
                boundary_shape = curr_area.get_border()
                holes = curr_area.get_holes()
            }
            p_par.file.start_scope()
            p_par.file.write("wire ")
            val dsn_shape = p_par.coordinate_transform.board_to_dsn(boundary_shape, conduction_layer)
            dsn_shape?.write_scope(p_par.file, p_par.identifier_type)
            for (i in holes.indices) {
                val dsn_hole = p_par.coordinate_transform.board_to_dsn(holes[i], conduction_layer)
                dsn_hole?.write_hole_scope(p_par.file, p_par.identifier_type)
            }
            write_net(curr_net, p_par.file, p_par.identifier_type)
            Rule.write_item_clearance_class(
                p_par.board.rules.clearance_matrix.get_name(p_conduction_area.clearance_class_no()) ?: "",
                p_par.file,
                p_par.identifier_type
            )
            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_net(p_net: app.freerouting.rules.Net, p_file: IndentFileWriter, p_identifier_type: IdentifierType) {
            p_file.new_line()
            p_file.write("(")
            Net.write_net_id(p_net, p_file, p_identifier_type)
            p_file.write(")")
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_fixed_state(p_file: IndentFileWriter, p_fixed_state: FixedState) {
            if (p_fixed_state == FixedState.UNFIXED) {
                return
            }
            p_file.new_line()
            p_file.write("(type ")
            if (p_fixed_state == FixedState.SHOVE_FIXED) {
                p_file.write("shove_fixed)")
            } else if (p_fixed_state == FixedState.SYSTEM_FIXED) {
                p_file.write("fix)")
            } else {
                p_file.write("protect)")
            }
        }

        @JvmStatic
        private fun get_subnets(p_net_id: Net.Id?, p_rules: BoardRules): Collection<app.freerouting.rules.Net> {
            var found_nets: Collection<app.freerouting.rules.Net> = LinkedList()
            if (p_net_id != null) {
                if (p_net_id.subnet_number > 0) {
                    val found_net = p_rules.nets.get(p_net_id.name, p_net_id.subnet_number)
                    if (found_net != null) {
                        found_nets = LinkedList()
                        found_nets.add(found_net)
                    }
                } else {
                    found_nets = p_rules.nets.get(p_net_id.name)
                }
            }
            return found_nets
        }

        @JvmStatic
        private fun via_exists(p_location: IntPoint, p_padstack: Padstack, p_net_no_arr: IntArray, p_board: BasicBoard): Boolean {
            val filter = ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.VIAS)
            val from_layer = p_padstack.from_layer()
            val to_layer = p_padstack.to_layer()
            val picked_items = p_board.pick_items(p_location, p_padstack.from_layer(), filter)
            for (curr_item in picked_items) {
                val curr_via = curr_item as Via
                if (curr_via.nets_equal(p_net_no_arr) && curr_via.get_center().equals(p_location) && curr_via.first_layer() == from_layer && curr_via.last_layer() == to_layer) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun calc_fixed(p_scanner: IJFlexScanner): FixedState {
            try {
                var result = FixedState.UNFIXED
                var next_token = p_scanner.next_token()
                if (next_token === Keyword.SHOVE_FIXED) {
                    result = FixedState.SHOVE_FIXED
                } else if (next_token === Keyword.FIX) {
                    result = FixedState.SYSTEM_FIXED
                } else if (next_token !== Keyword.NORMAL) {
                    result = FixedState.USER_FIXED
                }
                next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn("Wiring.is_fixed: ) expected at '" + p_scanner.get_scope_identifier() + "'")
                    return FixedState.UNFIXED
                }
                return result
            } catch (e: IOException) {
                FRLogger.error("Wiring.is_fixed: IO error scanning file", e)
                return FixedState.UNFIXED
            }
        }

        /**
         * Reads a net_id. The subnet_number of the net_id will be 0, if no subnet_number was found.
         */
        @JvmStatic
        private fun read_net_id(p_scanner: IJFlexScanner): Net.Id? {
            try {
                var subnet_number = 0

                val net_name = p_scanner.next_string() ?: return null
                p_scanner.set_scope_identifier(net_name)

                var next_token = p_scanner.next_token()
                if (next_token is Int) {
                    subnet_number = next_token
                    next_token = p_scanner.next_token()
                }
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn("Wiring.read_net_id: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                }
                return Net.Id(net_name, subnet_number)
            } catch (e: IOException) {
                FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e)
                return null
            }
        }
    }
}
