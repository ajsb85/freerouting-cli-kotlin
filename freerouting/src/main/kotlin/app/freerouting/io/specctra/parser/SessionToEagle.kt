package app.freerouting.io.specctra.parser

import app.freerouting.board.BasicBoard
import app.freerouting.board.Pin
import app.freerouting.board.Unit
import app.freerouting.core.Padstack
import app.freerouting.geometry.planar.Circle
import app.freerouting.geometry.planar.IntBox
import app.freerouting.geometry.planar.IntOctagon
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.swing.JFrame

/**
 * Transforms a Specctra session file into an Eagle script file.
 */
class SessionToEagle(
    private val scanner: IJFlexScanner,
    private val out_file: OutputStreamWriter,
    private val board: BasicBoard,
    p_unit: Unit,
    p_session_file_scale_dominator: Double,
    p_board_scale_factor: Double
) : JFrame() {

    private val specctra_layer_structure: LayerStructure = LayerStructure(board.layer_structure)
    private val unit: Unit = p_unit
    private val session_file_scale_denominator: Double = p_session_file_scale_dominator
    private val board_scale_factor: Double = p_board_scale_factor

    /**
     * Processes the outmost scope of the session file. Returns false, if an error occurred.
     */
    @Throws(IOException::class)
    private fun process_session_scope(): Boolean {
        // read the first line of the session file
        var next_token: Any? = null
        for (i in 0 until 3) {
            next_token = this.scanner.next_token()
            var keyword_ok = true
            if (i == 0) {
                keyword_ok = next_token === Keyword.OPEN_BRACKET
            } else if (i == 1) {
                keyword_ok = next_token === Keyword.SESSION
                this.scanner.yybegin(SpecctraDsnStreamReader.NAME) // to overread the name of the pcb for i = 2
            }
            if (!keyword_ok) {
                FRLogger.warn("SessionToEagle.process_session_scope specctra session file format expected")
                return false
            }
        }

        // Write the header of the eagle script file.
        this.out_file.write("GRID ")
        this.out_file.write(this.unit.toString())
        this.out_file.write("\n")
        this.out_file.write("SET WIRE_BEND 2\n")
        this.out_file.write("SET OPTIMIZING OFF\n")

        // Activate all layers in Eagle.
        for (i in this.board.layer_structure.arr.indices) {
            this.out_file.write("LAYER " + this.get_eagle_layer_string(i) + ";\n")
        }

        this.out_file.write("LAYER 17;\n")
        this.out_file.write("LAYER 18;\n")
        this.out_file.write("LAYER 19;\n")
        this.out_file.write("LAYER 20;\n")
        this.out_file.write("LAYER 23;\n")
        this.out_file.write("LAYER 24;\n")

        // Generate Code to remove the complete route.
        // Write a bounding rectangle with GROUP (Min_X-1 Min_Y-1) (Max_X+1 Max_Y+1);
        val board_bounding_box = this.board.get_bounding_box()

        val min_x = (this.board_scale_factor * (board_bounding_box.ll.x - 1)).toFloat()
        val min_y = (this.board_scale_factor * (board_bounding_box.ll.y - 1)).toFloat()
        val max_x = (this.board_scale_factor * (board_bounding_box.ur.x + 1)).toFloat()
        val max_y = (this.board_scale_factor * (board_bounding_box.ur.y + 1)).toFloat()

        this.out_file.write("GROUP (")
        this.out_file.write(min_x.toString())
        this.out_file.write(" ")
        this.out_file.write(min_y.toString())
        this.out_file.write(") (")
        this.out_file.write(max_x.toString())
        this.out_file.write(" ")
        this.out_file.write(max_y.toString())
        this.out_file.write(");\n")
        this.out_file.write("RIPUP;\n")

        // read the direct subscopes of the session scope
        while (true) {
            val prev_token = next_token
            next_token = this.scanner.next_token()
            if (next_token == null) {
                // end of file
                return true
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }

            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.ROUTES) {
                    if (!process_routes_scope()) {
                        return false
                    }
                } else if (next_token === Keyword.PLACEMENT_SCOPE) {
                    if (!process_placement_scope()) {
                        return false
                    }
                } else {
                    // overread all scopes except the routes scope for the time being
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
        // Wird nur einmal am Ende benoetigt!
        this.out_file.write("RATSNEST\n")
        return true
    }

    @Throws(IOException::class)
    private fun process_placement_scope(): Boolean {
        // read the component scopes
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            next_token = this.scanner.next_token()
            if (next_token == null) {
                // unexpected end of file
                return false
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }

            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.COMPONENT_SCOPE) {
                    if (!process_component_placement()) {
                        return false
                    }
                } else {
                    // skip unknown scope
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
        process_swapped_pins()
        return true
    }

    @Throws(IOException::class)
    private fun process_component_placement(): Boolean {
        val component_placement = Component.read_scope(this.scanner) ?: return false
        for (curr_location in component_placement.locations) {
            this.out_file.write("ROTATE =")
            val rotation = Math.round(curr_location.rotation).toInt()
            val rotation_string = if (curr_location.is_front) {
                "R$rotation"
            } else {
                "MR$rotation"
            }
            this.out_file.write(rotation_string)
            this.out_file.write(" '")
            this.out_file.write(curr_location.name)
            this.out_file.write("';\n")
            this.out_file.write("move '")
            this.out_file.write(curr_location.name)
            this.out_file.write("' (")
            val x_coor = curr_location.coor[0] / this.session_file_scale_denominator
            this.out_file.write(x_coor.toString())
            this.out_file.write(" ")
            val y_coor = curr_location.coor[1] / this.session_file_scale_denominator
            this.out_file.write(y_coor.toString())
            this.out_file.write(");\n")
        }
        return true
    }

    @Throws(IOException::class)
    private fun process_routes_scope(): Boolean {
        // read the direct subscopes of the routes scope
        var result = true
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            next_token = this.scanner.next_token()
            if (next_token == null) {
                // unexpected end of file
                return false
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }

            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.NETWORK_OUT) {
                    result = process_network_scope()
                } else {
                    // skip unknown scope
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
        return result
    }

    @Throws(IOException::class)
    private fun process_network_scope(): Boolean {
        var result = true
        var next_token: Any? = null
        // read the net scopes
        while (true) {
            val prev_token = next_token
            next_token = this.scanner.next_token()
            if (next_token == null) {
                // unexpected end of file
                return false
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }

            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.NET) {
                    result = process_net_scope()
                } else {
                    // skip unknown scope
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
        return result
    }

    @Throws(IOException::class)
    private fun process_net_scope(): Boolean {
        // read the net name
        var next_token = this.scanner.next_token()
        if (next_token !is String) {
            FRLogger.warn("SessionToEagle.process_net_scope: String expected at '" + this.scanner.get_scope_identifier() + "'")
            return false
        }
        val net_name = next_token
        this.scanner.set_scope_identifier(net_name)

        // read the wires and vias of this net
        while (true) {
            val prev_token = next_token
            next_token = this.scanner.next_token()
            if (next_token == null) {
                // end of file
                return true
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }

            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.WIRE) {
                    if (!process_wire_scope(net_name)) {
                        return false
                    }
                } else if (next_token === Keyword.VIA) {
                    if (!process_via_scope(net_name)) {
                        return false
                    }
                } else {
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun process_wire_scope(p_net_name: String): Boolean {
        var wire_path: PolygonPath? = null
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            next_token = this.scanner.next_token()
            if (next_token == null) {
                FRLogger.warn("SessionToEagle.process_wire_scope: unexpected end of file at '" + this.scanner.get_scope_identifier() + "'")
                return false
            }
            if (next_token === Keyword.CLOSED_BRACKET) {
                // end of scope
                break
            }
            if (prev_token === Keyword.OPEN_BRACKET) {
                if (next_token === Keyword.POLYGON_PATH) {
                    wire_path = Shape.read_polygon_path_scope(this.scanner, this.specctra_layer_structure)
                } else {
                    ScopeKeyword.skip_scope(this.scanner)
                }
            }
        }
        if (wire_path == null) {
            // conduction areas are skipped
            return true
        }

        this.out_file.write("CHANGE LAYER ")
        this.out_file.write(wire_path.layer.name)
        this.out_file.write(";\n")

        this.out_file.write("WIRE '")
        this.out_file.write(p_net_name)
        this.out_file.write("' ")
        val wire_width = wire_path.width / this.session_file_scale_denominator
        this.out_file.write(wire_width.toString())
        this.out_file.write(" (")
        for (i in wire_path.coordinate_arr.indices) {
            val wire_coor = wire_path.coordinate_arr[i] / this.session_file_scale_denominator
            this.out_file.write(wire_coor.toString())
            if (i % 2 == 0) {
                this.out_file.write(" ")
            } else {
                if (i == wire_path.coordinate_arr.size - 1) {
                    this.out_file.write(")")
                } else {
                    this.out_file.write(") (")
                }
            }
        }
        this.out_file.write(";\n")

        return true
    }

    @Throws(IOException::class)
    private fun process_via_scope(p_net_name: String): Boolean {
        // read the padstack name
        var next_token = this.scanner.next_token()
        if (next_token !is String) {
            FRLogger.warn("SessionToEagle.process_via_scope: padstack name expected at '" + this.scanner.get_scope_identifier() + "'")
            return false
        }
        val padstack_name = next_token
        this.scanner.set_scope_identifier(padstack_name)
        // read the location
        val location = DoubleArray(2)
        for (i in 0 until 2) {
            next_token = this.scanner.next_token()
            if (next_token is Double) {
                location[i] = next_token
            } else if (next_token is Int) {
                location[i] = next_token.toDouble()
            } else {
                FRLogger.warn("SessionToEagle.process_via_scope: number expected at '" + this.scanner.get_scope_identifier() + "'")
                return false
            }
        }
        next_token = this.scanner.next_token()
        while (next_token === Keyword.OPEN_BRACKET) {
            // skip unknown scopes
            ScopeKeyword.skip_scope(this.scanner)
            next_token = this.scanner.next_token()
        }
        if (next_token !== Keyword.CLOSED_BRACKET) {
            FRLogger.warn("SessionToEagle.process_via_scope: closing bracket expected at '" + this.scanner.get_scope_identifier() + "'")
            return false
        }

        val via_padstack = this.board.library.padstacks!!.get(padstack_name)
        if (via_padstack == null) {
            FRLogger.warn("SessionToEagle.process_via_scope: via padstack not found at '" + this.scanner.get_scope_identifier() + "'")
            return false
        }

        val via_shape = via_padstack.get_shape(via_padstack.from_layer())
        val via_diameter = (via_shape?.max_width() ?: 0.0) * this.board_scale_factor

        // The Padstack name is of the form Name$drill_diameter$from_layer-to_layer
        val name_parts = via_padstack.name.split("\\$".toRegex(), 3).toTypedArray()

        this.out_file.write("CHANGE DRILL ")
        if (name_parts.size > 1) {
            this.out_file.write(name_parts[1])
        } else {
            // create a default drill, because it is needed in Eagle
            this.out_file.write("0.1")
        }
        this.out_file.write(";\n")

        this.out_file.write("VIA '")
        this.out_file.write(p_net_name)
        this.out_file.write("' ")

        // Durchmesser aus Padstack
        this.out_file.write(via_diameter.toString())

        // Shape lesen und einsetzen Square / Round / Octagon
        if (via_shape is Circle) {
            this.out_file.write(" round ")
        } else if (via_shape is IntOctagon) {
            this.out_file.write(" octagon ")
        } else {
            this.out_file.write(" square ")
        }
        this.out_file.write(get_eagle_layer_string(via_padstack.from_layer()))
        this.out_file.write("-")
        this.out_file.write(get_eagle_layer_string(via_padstack.to_layer()))
        this.out_file.write(" (")
        val x_coor = location[0] / this.session_file_scale_denominator
        this.out_file.write(x_coor.toString())
        this.out_file.write(" ")
        val y_coor = location[1] / this.session_file_scale_denominator
        this.out_file.write(y_coor.toString())
        this.out_file.write(");\n")

        return true
    }

    private fun get_eagle_layer_string(p_layer_no: Int): String {
        if (p_layer_no < 0 || p_layer_no >= specctra_layer_structure.arr.size) {
            return "0"
        }
        val name_pieces = this.specctra_layer_structure.arr[p_layer_no].name.split("#".toRegex(), 2).toTypedArray()
        return name_pieces[0]
    }

    @Throws(IOException::class)
    private fun process_swapped_pins(): Boolean {
        for (i in 1..this.board.components.count()) {
            if (!process_swapped_pins(i)) {
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun process_swapped_pins(p_component_no: Int): Boolean {
        val component_pins = this.board.get_component_pins(p_component_no)
        var component_has_swapped_pins = false
        for (curr_pin in component_pins) {
            if (curr_pin.get_changed_to() !== curr_pin) {
                component_has_swapped_pins = true
                break
            }
        }
        if (!component_has_swapped_pins) {
            return true
        }
        val pin_info_arr = component_pins.map { PinInfo(it) }.toTypedArray()
        for (i in pin_info_arr.indices) {
            val curr_pin_info = pin_info_arr[i]
            if (curr_pin_info.curr_changed_to !== curr_pin_info.pin.get_changed_to()) {
                var other_pin_info: PinInfo? = null
                for (j in i + 1 until pin_info_arr.size) {
                    if (pin_info_arr[j].pin.get_changed_to() === curr_pin_info.pin) {
                        other_pin_info = pin_info_arr[j]
                    }
                }
                if (other_pin_info == null) {
                    FRLogger.warn("SessionToEagle.process_swapped_pins: other_pin_info not found at '" + this.scanner.get_scope_identifier() + "'")
                    return false
                }
                write_pin_swap(curr_pin_info.pin, other_pin_info.pin)
                curr_pin_info.curr_changed_to = other_pin_info.pin
                other_pin_info.curr_changed_to = curr_pin_info.pin
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun write_pin_swap(p_pin_1: Pin, p_pin_2: Pin) {
        val layer_no = Math.max(p_pin_1.first_layer(), p_pin_2.first_layer())
        val layer_name = board.layer_structure.arr[layer_no].name

        this.out_file.write("CHANGE LAYER ")
        this.out_file.write(layer_name)
        this.out_file.write(";\n")

        val location_1 = this.board.communication.coordinate_transform.board_to_dsn(p_pin_1.get_center().to_float())
        val location_2 = this.board.communication.coordinate_transform.board_to_dsn(p_pin_2.get_center().to_float())

        this.out_file.write("PINSWAP ")
        this.out_file.write(" (")
        var curr_coor = location_1[0]
        this.out_file.write(curr_coor.toString())
        this.out_file.write(" ")
        curr_coor = location_1[1]
        this.out_file.write(curr_coor.toString())
        this.out_file.write(") (")
        curr_coor = location_2[0]
        this.out_file.write(curr_coor.toString())
        this.out_file.write(" ")
        curr_coor = location_2[1]
        this.out_file.write(curr_coor.toString())
        this.out_file.write(");\n")
    }

    private class PinInfo(val pin: Pin) {
        var curr_changed_to: Pin = pin
    }

    companion object {
        @JvmStatic
        fun get_instance(p_session: InputStream, p_output_stream: OutputStream?, p_board: BasicBoard): Boolean {
            if (p_output_stream == null) {
                return false
            }

            // create a scanner for reading the session_file.
            val scanner = SpecctraDsnStreamReader(p_session)

            // create a file_writer for the eagle script file.
            val file_writer = OutputStreamWriter(p_output_stream)

            val board_scale_factor = p_board.communication.coordinate_transform.board_to_dsn(1.0)
            val new_instance = SessionToEagle(
                scanner,
                file_writer,
                p_board,
                p_board.communication.unit,
                p_board.communication.resolution.toDouble(),
                board_scale_factor
            )

            var result: Boolean
            try {
                result = new_instance.process_session_scope()
            } catch (e: IOException) {
                FRLogger.error("unable to process session scope", e)
                result = false
            }

            // close files
            try {
                p_session.close()
                file_writer.close()
            } catch (e: IOException) {
                FRLogger.error("unable to close files", e)
            }
            return result
        }
    }
}
