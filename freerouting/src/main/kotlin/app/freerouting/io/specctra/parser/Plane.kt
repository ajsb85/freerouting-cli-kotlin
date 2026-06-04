package app.freerouting.io.specctra.parser

import app.freerouting.board.ConductionArea
import app.freerouting.geometry.planar.Area
import app.freerouting.logger.FRLogger
import java.io.IOException

/**
 * Class for reading and writing plane scopes from dsn-files.
 */
class Plane : ScopeKeyword("plane") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        // read the net name
        val net_name: String
        val skip_window_scopes = "allegro".equals(p_par.host_cad, ignoreCase = true)
        // Cadence Allegro cutouts the pins on power planes, which leads to performance problems
        // when dividing a conduction area into convex pieces.
        val conduction_area: Shape.ReadAreaScopeResult?
        try {
            val next_token = p_par.scanner.next_token()
            if (next_token !is String) {
                FRLogger.warn("Plane.read_scope: String expected at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            net_name = next_token
            p_par.scanner.set_scope_identifier(net_name)
            conduction_area = Shape.read_area_scope(p_par.scanner, p_par.layer_structure, skip_window_scopes)
        } catch (e: IOException) {
            FRLogger.error("Plane.read_scope: IO error scanning file", e)
            return false
        }
        if (conduction_area == null) {
            return false
        }
        val plane_info = ReadScopeParameter.PlaneInfo(conduction_area, net_name)
        p_par.plane_list.add(plane_info)
        return true
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter, p_conduction: ConductionArea) {
            val net_count = p_conduction.net_count()
            if (net_count != 1) {
                FRLogger.warn("Plane.write_scope: unexpected net count at '${p_conduction.name}'")
                return
            }
            val net_name = p_par.board.rules.nets.get(p_conduction.get_net_no(0))?.name ?: ""
            val curr_area = p_conduction.get_area()
            val layer_no = p_conduction.get_layer()
            val board_layer = p_par.board.layer_structure.arr[layer_no]
            val plane_layer = Layer(board_layer.name, layer_no, board_layer.is_signal)
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
            p_par.file.write("plane ")
            p_par.identifier_type.write(net_name, p_par.file)
            val dsn_shape = p_par.coordinate_transform.board_to_dsn(boundary_shape, plane_layer)
            if (dsn_shape != null) {
                dsn_shape.write_scope(p_par.file, p_par.identifier_type)
            }
            for (i in holes.indices) {
                val dsn_hole = p_par.coordinate_transform.board_to_dsn(holes[i], plane_layer)
                dsn_hole?.write_hole_scope(p_par.file, p_par.identifier_type)
            }
            p_par.file.end_scope()
        }
    }
}
