package app.freerouting.io.specctra.parser

import app.freerouting.board.BasicBoard
import app.freerouting.board.ConductionArea
import app.freerouting.board.FixedState
import app.freerouting.board.Trace
import app.freerouting.geometry.planar.TileShape
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.LinkedList

/**
 * Class for reading and writing dsn-files.
 */
class DsnFile private constructor() {

    enum class ReadResult {
        OK, OUTLINE_MISSING, ERROR
    }

    companion object {
        const val CLASS_CLEARANCE_SEPARATOR = '-'

        /**
         * Sets contains_plane to true for nets with a conduction_area covering a large
         * part of a signal layer, if that layer does not contain any traces. This is
         * useful in case the layer type was not set correctly to plane in the dsn-file.
         * Returns true, if something was changed.
         *
         * <p>Called from {@link app.freerouting.io.specctra.DsnReader#readBoard} when the
         * DSN file contains no {@code (autoroute ...)} scope.
         */
        @JvmStatic
        fun adjustPlaneAutorouteSettings(routing_board: BasicBoard?): Boolean {
            if (routing_board == null) {
                return false
            }
            val board_layer_structure = routing_board.layer_structure
            if (board_layer_structure.arr.size <= 2) {
                return false
            }
            for (curr_layer in board_layer_structure.arr) {
                if (!curr_layer.is_signal) {
                    return false
                }
            }
            val layer_contains_wires_arr = BooleanArray(board_layer_structure.arr.size)
            val changed_layer_arr = BooleanArray(board_layer_structure.arr.size)
            for (i in layer_contains_wires_arr.indices) {
                layer_contains_wires_arr[i] = false
                changed_layer_arr[i] = false
            }
            val conduction_area_list = LinkedList<ConductionArea>()
            val item_list = routing_board.get_items()
            for (curr_item in item_list) {
                if (curr_item is Trace) {
                    val curr_layer = curr_item.get_layer()
                    layer_contains_wires_arr[curr_layer] = true
                } else if (curr_item is ConductionArea) {
                    conduction_area_list.add(curr_item)
                }
            }
            var nothing_changed = true

            val board_outline = routing_board.get_outline()
            var board_area = 0.0
            for (i in 0 until board_outline.shape_count()) {
                val curr_piece_arr = board_outline.get_shape(i).split_to_convex()
                if (curr_piece_arr != null) {
                    for (curr_piece in curr_piece_arr) {
                        board_area += curr_piece.area()
                    }
                }
            }
            for (curr_conduction_area in conduction_area_list) {
                val layer_no = curr_conduction_area.get_layer()
                if (layer_contains_wires_arr[layer_no]) {
                    continue
                }
                val curr_layer = routing_board.layer_structure.arr[layer_no]
                if (!curr_layer.is_signal || layer_no == 0 ||
                    layer_no == board_layer_structure.arr.size - 1
                ) {
                    continue
                }
                val convex_pieces = curr_conduction_area.get_area().split_to_convex()
                var curr_area = 0.0
                for (curr_piece in convex_pieces) {
                    curr_area += curr_piece.area()
                }
                if (curr_area < 0.5 * board_area) {
                    continue
                }
                for (i in 0 until curr_conduction_area.net_count()) {
                    val curr_net = routing_board.rules.nets.get(curr_conduction_area.get_net_no(i))
                    curr_net?.set_contains_plane(true)
                    nothing_changed = false
                }
                changed_layer_arr[layer_no] = true
                if (curr_conduction_area.get_fixed_state().ordinal < FixedState.USER_FIXED.ordinal) {
                    curr_conduction_area.set_fixed_state(FixedState.USER_FIXED)
                }
            }
            if (nothing_changed) {
                return false
            }
            return true
        }

        @JvmStatic
        fun read_on_off_scope(p_scanner: IJFlexScanner): Boolean {
            try {
                val next_token = p_scanner.next_token()
                var result = false
                if (next_token === Keyword.ON) {
                    result = true
                } else if (next_token !== Keyword.OFF) {
                    FRLogger.warn(
                        "DsnFile.read_boolean: Keyword.OFF expected at '" +
                                p_scanner.get_scope_identifier() + "'"
                    )
                }
                ScopeKeyword.skip_scope(p_scanner)
                return result
            } catch (e: IOException) {
                FRLogger.error("DsnFile.read_boolean: IO error scanning file", e)
                return false
            }
        }

        @JvmStatic
        fun read_integer_scope(p_scanner: IJFlexScanner): Int {
            try {
                val value: Int
                var next_token = p_scanner.next_token()
                if (next_token is Int) {
                    value = next_token
                } else {
                    FRLogger.warn(
                        "DsnFile.read_integer_scope: number expected at '" +
                                p_scanner.get_scope_identifier() + "'"
                    )
                    return 0
                }
                next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn(
                        "DsnFile.read_integer_scope: closing bracket expected at '" +
                                p_scanner.get_scope_identifier() + "'"
                    )
                    return 0
                }
                return value
            } catch (e: IOException) {
                FRLogger.error("DsnFile.read_integer_scope: IO error scanning file", e)
                return 0
            }
        }

        @JvmStatic
        fun read_float_scope(p_scanner: IJFlexScanner): Double {
            try {
                val value: Double
                var next_token = p_scanner.next_token()
                if (next_token is Double) {
                    value = next_token
                } else if (next_token is Int) {
                    value = next_token.toDouble()
                } else {
                    FRLogger.warn(
                        "DsnFile.read_float_scope: number expected at '" +
                                p_scanner.get_scope_identifier() + "'"
                    )
                    return 0.0
                }
                next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn(
                        "DsnFile.read_float_scope: closing bracket expected at '" +
                                p_scanner.get_scope_identifier() + "'"
                    )
                    return 0.0
                }
                return value
            } catch (e: IOException) {
                FRLogger.error("DsnFile.read_float_scope: IO error scanning file", e)
                return 0.0
            }
        }

        @JvmStatic
        fun read_string_scope(p_scanner: IJFlexScanner): String? {
            try {
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                val result = p_scanner.next_string()
                val next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    FRLogger.warn(
                        "DsnFile.read_string_scope: closing bracket expected at '" +
                                p_scanner.get_scope_identifier() + "'"
                    )
                }
                return result
            } catch (e: IOException) {
                FRLogger.error("DsnFile.read_string_scope: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        fun read_string_list_scope(p_scanner: IJFlexScanner): Array<String>? {
            val result = p_scanner.next_string_list()
            if (p_scanner.next_closing_bracket() != true) {
                return null
            }
            return result
        }
    }
}
