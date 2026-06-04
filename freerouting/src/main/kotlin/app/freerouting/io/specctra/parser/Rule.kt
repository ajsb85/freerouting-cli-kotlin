package app.freerouting.io.specctra.parser

import app.freerouting.board.Layer
import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.logger.FRLogger
import app.freerouting.rules.BoardRules
import app.freerouting.rules.ClearanceMatrix
import app.freerouting.rules.NetClass
import java.io.IOException
import java.util.LinkedList

/**
 * Class for reading and writing rule scopes from dsn-files.
 */
abstract class Rule {

    class WidthRule(@JvmField val value: Double) : Rule()

    class ClearanceRule(
        @JvmField val value: Double,
        @JvmField val clearance_class_pairs: Collection<String>
    ) : Rule()

    class LayerRule(
        @JvmField val layer_names: Collection<String>,
        @JvmField val rules: Collection<Rule>
    )

    companion object {
        /**
         * Returns a collection of objects of class Rule.
         */
        @JvmStatic
        fun read_scope(p_scanner: IJFlexScanner): Collection<Rule>? {
            val result = LinkedList<Rule>()
            var current_token: Any? = null
            while (true) {
                val prev_token = current_token
                try {
                    current_token = p_scanner.next_token()
                } catch (e: IOException) {
                    FRLogger.error("Rule.read_scope: IO error scanning file", e)
                    return null
                }
                if (current_token == null) {
                    FRLogger.warn("Rule.read_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                if (current_token === Keyword.CLOSED_BRACKET) {
                    // end of scope
                    break
                }

                if (prev_token === Keyword.OPEN_BRACKET) {
                    // every rule starts with a "("
                    var curr_rule: Rule? = null
                    if (current_token === Keyword.WIDTH) {
                        // this is a "(width" rule
                        curr_rule = read_width_rule(p_scanner)
                    } else if (current_token === Keyword.CLEARANCE) {
                        // this is a "(clear" rule
                        curr_rule = read_clearance_rule(p_scanner)
                    } else {
                        ScopeKeyword.skip_scope(p_scanner)
                    }

                    if (curr_rule != null) {
                        result.add(curr_rule)
                    }
                }
            }
            return result
        }

        /**
         * Reads a LayerRule from dsn-file.
         */
        @JvmStatic
        fun read_layer_rule_scope(p_scanner: IJFlexScanner): LayerRule? {
            try {
                val layer_names = LinkedList<String>()
                val rule_list = LinkedList<Rule>()
                while (true) {
                    p_scanner.yybegin(SpecctraDsnStreamReader.LAYER_NAME)
                    val next_token = p_scanner.next_token()
                    if (next_token === Keyword.OPEN_BRACKET) {
                        break
                    }
                    if (next_token !is String) {
                        FRLogger.warn("Rule.read_layer_rule_scope: string expected at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                    layer_names.add(next_token)
                }
                while (true) {
                    val next_token = p_scanner.next_token()
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        break
                    }
                    if (next_token !== Keyword.RULE) {
                        FRLogger.warn("Rule.read_layer_rule_scope: rule expected at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                    val parsed = read_scope(p_scanner) ?: return null
                    rule_list.addAll(parsed)
                }
                return LayerRule(layer_names, rule_list)
            } catch (e: IOException) {
                FRLogger.error("Rule.read_layer_rule_scope: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        fun read_width_rule(p_scanner: IJFlexScanner): WidthRule? {
            val value = p_scanner.next_double() ?: return null

            if (p_scanner.next_closing_bracket() != true) {
                return null
            }

            return WidthRule(value)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_net_class: NetClass, p_par: WriteScopeParameter) {
            p_par.file.start_scope()
            p_par.file.write("rule")

            // write the trace width
            val default_trace_half_width = p_net_class.get_trace_half_width(0)
            val trace_width = 2 * p_par.coordinate_transform.board_to_dsn(default_trace_half_width.toDouble())
            p_par.file.new_line()
            p_par.file.write("(width ")
            p_par.file.write(trace_width.toString())
            p_par.file.write(")")
            p_par.file.end_scope()
            for (i in 1 until p_par.board.layer_structure.arr.size) {
                if (p_net_class.get_trace_half_width(i) != default_trace_half_width) {
                    write_layer_rule(p_net_class, i, p_par)
                }
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_layer_rule(p_net_class: NetClass, p_layer_no: Int, p_par: WriteScopeParameter) {
            p_par.file.start_scope()
            p_par.file.write("layer_rule ")

            val curr_board_layer = p_par.board.layer_structure.arr[p_layer_no]

            p_par.file.write(curr_board_layer.name)
            p_par.file.start_scope()
            p_par.file.write("rule ")

            val curr_trace_half_width = p_net_class.get_trace_half_width(p_layer_no)

            // write the trace width
            val trace_width = 2 * p_par.coordinate_transform.board_to_dsn(curr_trace_half_width.toDouble())
            p_par.file.new_line()
            p_par.file.write("(width ")
            p_par.file.write(trace_width.toString())
            p_par.file.write(") ")
            p_par.file.end_scope()
            p_par.file.end_scope()
        }

        /**
         * Writes the default rule as a scope to an output dsn-file.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun write_default_rule(p_par: WriteScopeParameter, p_layer: Int) {
            p_par.file.start_scope()
            p_par.file.write("rule")
            // write the trace width
            val trace_width = 2 * p_par.coordinate_transform.board_to_dsn(
                p_par.board.rules.get_default_net_class().get_trace_half_width(0).toDouble()
            )
            p_par.file.new_line()
            p_par.file.write("(width ")
            p_par.file.write(trace_width.toString())
            p_par.file.write(")")
            // write the default clearance rule
            val default_cl_no = BoardRules.default_clearance_class()
            val default_board_clearance =
                p_par.board.rules.clearance_matrix.get_value(default_cl_no, default_cl_no, p_layer, false)
            val default_clearance = p_par.coordinate_transform.board_to_dsn(default_board_clearance.toDouble())
            p_par.file.new_line()
            // write the default clearance
            p_par.file.write("(clearance ")
            p_par.file.write(default_clearance.toString())
            p_par.file.write(")")
            // write the smd_to_turn_gap
            val smd_to_turn_dist = p_par.coordinate_transform.board_to_dsn(p_par.board.rules.get_pin_edge_to_turn_dist().toDouble())
            p_par.file.new_line()
            p_par.file.write("(clearance ")
            p_par.file.write(smd_to_turn_dist.toString())
            p_par.file.write(" (type smd_to_turn_gap))")

            // write the named clearance rules from the clearance matrix
            write_named_clearance_rules(p_par, p_layer)
            p_par.file.end_scope()
        }

        /**
         * Write the clearance rules for the named classes in the clearance matrix.
         */
        @JvmStatic
        @Throws(IOException::class)
        private fun write_named_clearance_rules(p_par: WriteScopeParameter, p_layer: Int) {
            val cl_matrix = p_par.board.rules.clearance_matrix
            val cl_count = p_par.board.rules.clearance_matrix.get_class_count()

            for (i in 1 until cl_count) {
                if (cl_matrix.get_name(i) == "default") {
                    continue
                }

                val curr_board_clearance = cl_matrix.get_value(i, i, p_layer, false)
                val curr_clearance = p_par.coordinate_transform.board_to_dsn(curr_board_clearance.toDouble())

                p_par.file.new_line()
                p_par.file.write("(clearance ")
                p_par.file.write(curr_clearance.toString())
                p_par.file.write(" (type ")
                p_par.identifier_type.write(cl_matrix.get_name(i) ?: "", p_par.file)
                p_par.file.write("))")
            }
        }

        @JvmStatic
        fun read_clearance_rule(p_scanner: IJFlexScanner): ClearanceRule? {
            try {
                val value = p_scanner.next_double() ?: return null

                val class_pairs = LinkedList<String>()
                var next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    // look for "(type"
                    if (next_token !== Keyword.OPEN_BRACKET) {
                        FRLogger.warn("Rule.read_clearance_rule: ( expected at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                    next_token = p_scanner.next_token()
                    if (next_token !== Keyword.TYPE) {
                        FRLogger.warn("Rule.read_clearance_rule: type expected at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }

                    val list = p_scanner.next_string_list(DsnFile.CLASS_CLEARANCE_SEPARATOR)
                    if (list != null) {
                        class_pairs.addAll(list.toList())
                    }

                    // check the closing ")" of "(type"
                    if (p_scanner.next_closing_bracket() != true) {
                        FRLogger.warn("Rule.read_clearance_rule: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }

                    // check the closing ")" of "(clear"
                    if (p_scanner.next_closing_bracket() != true) {
                        FRLogger.warn("Rule.read_clearance_rule: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                }

                return ClearanceRule(value, class_pairs)
            } catch (e: IOException) {
                FRLogger.error("Rule.read_clearance_rule: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_item_clearance_class(
            p_name: String,
            p_file: IndentFileWriter,
            p_identifier_type: IdentifierType
        ) {
            p_file.new_line()
            p_file.write("(clearance_class ")
            p_identifier_type.write(p_name, p_file)
            p_file.write(")")
        }
    }
}
