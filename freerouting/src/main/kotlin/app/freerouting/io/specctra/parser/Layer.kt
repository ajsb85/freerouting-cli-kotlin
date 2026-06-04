package app.freerouting.io.specctra.parser

import java.io.IOException
import java.util.LinkedList

/**
 * Describes a layer in a Specctra dsn file.
 */
class Layer @JvmOverloads constructor(
    @JvmField val name: String,
    @JvmField val no: Int,
    @JvmField val is_signal: Boolean,
    @JvmField val net_names: Collection<String> = LinkedList<String>()
) {
    companion object {
        /**
         * all layers of the board
         */
        @JvmField val PCB = Layer("pcb", -1, false)

        /**
         * the signal layers
         */
        @JvmField val SIGNAL = Layer("signal", -1, true)

        /**
         * Writes a layer scope in the structure scope.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter, p_layer_no: Int, p_write_rule: Boolean) {
            p_par.file.start_scope()
            p_par.file.write("layer ")
            val board_layer = p_par.board.layer_structure.arr[p_layer_no]
            p_par.identifier_type.write(board_layer.name, p_par.file)
            p_par.file.new_line()
            p_par.file.write("(type ")
            if (board_layer.is_signal) {
                p_par.file.write("signal)")
            } else {
                p_par.file.write("power)")
            }
            if (p_write_rule) {
                Rule.write_default_rule(p_par, p_layer_no)
            }
            p_par.file.end_scope()
        }
    }
}
