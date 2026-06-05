package app.freerouting.io.specctra.parser

import app.freerouting.board.BasicBoard
import app.freerouting.board.FixedState
import app.freerouting.board.Pin
import app.freerouting.board.RoutingBoard
import app.freerouting.core.LogicalPart
import app.freerouting.core.Package
import app.freerouting.core.Padstack
import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.geometry.planar.IntPoint
import app.freerouting.geometry.planar.Point
import app.freerouting.geometry.planar.Vector
import app.freerouting.logger.FRLogger
import app.freerouting.rules.BoardRules
import app.freerouting.rules.ClearanceMatrix
import app.freerouting.rules.DefaultItemClearanceClasses
import app.freerouting.rules.DefaultItemClearanceClasses.ItemClass
import app.freerouting.rules.ViaInfo
import app.freerouting.rules.ViaRule
import java.io.IOException
import java.util.LinkedList
import java.util.TreeSet

/**
 * Class for reading and writing net network from dsn-files.
 */
class Network : ScopeKeyword("network") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        val board = p_par.board_handling.get_routing_board() ?: return false
        val coordinate_transform = p_par.coordinate_transform ?: return false
        val layer_structure = p_par.layer_structure ?: return false

        val classes = LinkedList<NetClass>()
        val class_class_list = LinkedList<NetClass.ClassClass>()
        val via_infos = LinkedList<ViaInfo>()
        val via_rules = LinkedList<Collection<String>>()
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            try {
                next_token = p_par.scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("Network.read_scope: IO error scanning file", e)
                return false
            }
            if (next_token == null) {
                FRLogger.warn("Network.read_scope: unexpected end of file at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            if (next_token === CLOSED_BRACKET) {
                // end of scope
                break
            }
            if (prev_token === OPEN_BRACKET) {
                if (next_token === Keyword.NET) {
                    read_net_scope(p_par.scanner, p_par.netlist, board, coordinate_transform, layer_structure)
                } else if (next_token === Keyword.VIA) {
                    val curr_via_info = read_via_info(p_par.scanner, board)
                        ?: return false
                    via_infos.add(curr_via_info)
                } else if (next_token === Keyword.VIA_RULE) {
                    val curr_via_rule = read_via_rule(p_par.scanner, board)
                        ?: return false
                    via_rules.add(curr_via_rule)
                } else if (next_token === Keyword.CLASS) {
                    val curr_class = NetClass.read_scope(p_par.scanner)
                        ?: return false
                    classes.add(curr_class)
                } else if (next_token === Keyword.CLASS_CLASS) {
                    val curr_class_class = NetClass.read_class_class_scope(p_par.scanner)
                        ?: return false
                    class_class_list.add(curr_class_class)
                } else {
                    skip_scope(p_par.scanner)
                }
            }
        }

        // Add any vias defined in the Netclasses to the list of vias to be instantiated
        for (n in classes) {
            val viaNames = p_par.via_padstack_names
            if (viaNames != null) {
                viaNames.addAll(n.use_via)
            } else {
                p_par.via_padstack_names = n.use_via.toMutableList()
            }
        }

        // Set the via padstacks after network parsing, so that named vias from both structure and
        // network DSN sections are properly instantiated .
        val viaPadstackNames = p_par.via_padstack_names
        if (viaPadstackNames != null) {
            var via_padstacks = arrayOfNulls<Padstack>(viaPadstackNames.size)
            val it = viaPadstackNames.iterator()
            var found_padstack_count = 0
            for (i in via_padstacks.indices) {
                val curr_padstack_name = it.next()
                val curr_padstack = board.library.padstacks?.get(curr_padstack_name)
                if (curr_padstack != null) {
                    via_padstacks[found_padstack_count] = curr_padstack
                    ++found_padstack_count
                } else {
                    FRLogger.warn("Library.read_scope: via padstack with name '$curr_padstack_name' not found at '${p_par.scanner.get_scope_identifier()}'")
                }
            }
            if (found_padstack_count != via_padstacks.size) {
                // Some via padstacks were not found in the padstacks scope of the dsn-file.
                val corrected_padstacks = arrayOfNulls<Padstack>(found_padstack_count)
                System.arraycopy(via_padstacks, 0, corrected_padstacks, 0, found_padstack_count)
                via_padstacks = corrected_padstacks
            }
            board.library.set_via_padstacks(via_padstacks.filterNotNull().toTypedArray())
        }

        insert_via_infos(via_infos, board, p_par.via_at_smd_allowed)
        insert_via_rules(via_rules, board)
        insert_net_classes(classes, p_par)
        insert_class_pairs(class_class_list, p_par)
        insert_components(p_par)
        insert_logical_parts(p_par)
        return true
    }

    private fun read_net_scope(
        p_scanner: IJFlexScanner,
        p_net_list: NetList,
        p_board: RoutingBoard,
        p_coordinate_transform: CoordinateTransform,
        p_layer_structure: LayerStructure
    ): Boolean {
        // read the net name
        val net_name = p_scanner.next_string() ?: ""

        var next_token: Any?
        var subnet_number = 1
        try {
            next_token = p_scanner.next_token()
        } catch (e: IOException) {
            FRLogger.error("Network.read_net_scope: IO error while scanning file", e)
            return false
        }
        val scope_is_empty = next_token === CLOSED_BRACKET
        if (next_token is Int) {
            subnet_number = next_token
        }
        var pin_order_found = false
        val pin_list = LinkedList<Net.Pin>()
        val net_rules = LinkedList<Rule>()
        var subnet_pin_lists: MutableCollection<Collection<Net.Pin>> = LinkedList()
        if (!scope_is_empty) {
            while (true) {
                val prev_token = next_token
                try {
                    next_token = p_scanner.next_token()
                } catch (e: IOException) {
                    FRLogger.error("Network.read_net_scope: IO error scanning file", e)
                    return false
                }
                if (next_token == null) {
                    FRLogger.warn("Network.read_net_scope: unexpected end of file at '${p_scanner.get_scope_identifier()}'")
                    return false
                }
                if (next_token === CLOSED_BRACKET) {
                    // end of scope
                    break
                }
                if (prev_token === OPEN_BRACKET) {
                    if (next_token === Keyword.PINS) {
                        if (!read_net_pins(p_scanner, pin_list)) {
                            return false
                        }
                    } else if (next_token === Keyword.ORDER) {
                        pin_order_found = true
                        if (!read_net_pins(p_scanner, pin_list)) {
                            return false
                        }
                    } else if (next_token === Keyword.FROMTO) {
                        val curr_subnet_pin_list = TreeSet<Net.Pin>()
                        if (!read_net_pins(p_scanner, curr_subnet_pin_list)) {
                            return false
                        }
                        subnet_pin_lists.add(curr_subnet_pin_list)
                    } else if (next_token === Keyword.RULE) {
                        val rules = Rule.read_scope(p_scanner)
                        if (rules != null) {
                            net_rules.addAll(rules)
                        }
                    } else if (next_token === Keyword.LAYER_RULE) {
                        FRLogger.warn("Network.read_net_scope: layer_rule not yet implemented at '${p_scanner.get_scope_identifier()}'")
                        skip_scope(p_scanner)
                    } else {
                        skip_scope(p_scanner)
                    }
                }
            }
        }
        if (subnet_pin_lists.isEmpty()) {
            if (pin_order_found) {
                subnet_pin_lists = create_ordered_subnets(pin_list) as MutableCollection<Collection<Net.Pin>>
            } else {
                subnet_pin_lists.add(pin_list)
            }
        }
        for (curr_pin_list in subnet_pin_lists) {
            val net_id = Net.Id(net_name, subnet_number)
            if (!p_net_list.contains(net_id)) {
                val new_net = p_net_list.add_net(net_id)
                val contains_plane = p_layer_structure.contains_plane(net_name)
                if (new_net != null) {
                    p_board.rules.nets.add(new_net.id.name, new_net.id.subnet_number, contains_plane)
                }
            }
            val curr_subnet = p_net_list.get_net(net_id)
            if (curr_subnet == null) {
                FRLogger.warn("Network.read_net_scope: net not found in netlist at '${p_scanner.get_scope_identifier()}'")
                return false
            }
            curr_subnet.set_pins(curr_pin_list)
            if (net_rules.isNotEmpty()) {
                // Evaluate the net rules.
                val board_net = p_board.rules.nets.get(curr_subnet.id.name, curr_subnet.id.subnet_number)
                if (board_net == null) {
                    FRLogger.warn("Network.read_net_scope: board net not found at '${p_scanner.get_scope_identifier()}'")
                    return false
                }
                for (curr_ob in net_rules) {
                    if (curr_ob is Rule.WidthRule) {
                        val default_net_rule = p_board.rules.get_default_net_class()
                        val wire_width = curr_ob.value
                        val trace_halfwidth = Math.round(p_coordinate_transform.dsn_to_board(wire_width) / 2).toInt()
                        var net_rule = p_board.rules.net_classes.find(trace_halfwidth, default_net_rule.get_trace_clearance_class(), default_net_rule.get_via_rule())
                        if (net_rule == null) {
                            // create a new net rule
                            net_rule = p_board.rules.get_new_net_class()
                        }
                        net_rule.set_trace_half_width(trace_halfwidth)
                        board_net.set_class(net_rule)
                    } else {
                        FRLogger.warn("Network.read_net_scope: Rule not yet implemented at '${p_scanner.get_scope_identifier()}'")
                    }
                }
            }
            ++subnet_number
        }
        return true
    }

    companion object {

        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter) {
            val board = p_par.board ?: return
            p_par.file.start_scope()
            p_par.file.write("network")
            val boardPins = board.get_pins()
            for (i in 1..board.rules.nets.max_net_no()) {
                val curr_net = board.rules.nets.get(i)
                if (curr_net != null) {
                    Net.write_scope(p_par, curr_net, boardPins)
                }
            }
            write_via_infos(board.rules, p_par.file, p_par.identifier_type)
            write_via_rules(board.rules, p_par.file, p_par.identifier_type)
            write_net_classes(p_par)
            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_via_infos(p_rules: BoardRules, p_file: IndentFileWriter, p_identifier_type: IdentifierType) {
            for (i in 0 until p_rules.via_infos.count()) {
                val currVia = p_rules.via_infos.get(i)
                p_file.start_scope()
                p_file.write("via ")
                p_file.new_line()
                p_identifier_type.write(currVia.get_name(), p_file)
                p_file.write(" ")
                p_identifier_type.write(currVia.get_padstack().name, p_file)
                p_file.write(" ")
                val clearance_name = p_rules.clearance_matrix.get_name(currVia.get_clearance_class()) ?: ""
                p_identifier_type.write(clearance_name, p_file)
                if (currVia.attach_smd_allowed()) {
                    p_file.write(" attach")
                }
                p_file.end_scope()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_via_rules(p_rules: BoardRules, p_file: IndentFileWriter, p_identifier_type: IdentifierType) {
            for (currRule in p_rules.via_rules) {
                p_file.start_scope()
                p_file.write("via_rule")
                p_file.new_line()
                p_identifier_type.write(currRule.name, p_file)
                for (i in 0 until currRule.via_count()) {
                    p_file.write(" ")
                    p_identifier_type.write(currRule.get_via(i).get_name(), p_file)
                }
                p_file.end_scope()
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_net_classes(p_par: WriteScopeParameter) {
            val board = p_par.board ?: return
            for (i in 0 until board.rules.net_classes.count()) {
                write_net_class(board.rules.net_classes.get(i), p_par)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun write_net_class(p_net_class: app.freerouting.rules.NetClass, p_par: WriteScopeParameter) {
            val board = p_par.board ?: return
            p_par.file.start_scope()
            p_par.file.write("class ")
            p_par.identifier_type.write(p_net_class.get_name(), p_par.file)
            val nets_per_row = 8
            var net_counter = 0
            for (i in 1..board.rules.nets.max_net_no()) {
                val curr_net = board.rules.nets.get(i)
                if (curr_net != null && curr_net.get_class() === p_net_class) {
                    if (net_counter % nets_per_row == 0) {
                        p_par.file.new_line()
                    } else {
                        p_par.file.write(" ")
                    }
                    p_par.identifier_type.write(curr_net.name, p_par.file)
                    ++net_counter
                }
            }

            // write the trace clearance class
            val trace_clearance_class_name = board.rules.clearance_matrix.get_name(p_net_class.get_trace_clearance_class()) ?: ""
            Rule.write_item_clearance_class(
                trace_clearance_class_name,
                p_par.file,
                p_par.identifier_type
            )

            val viaRule = p_net_class.get_via_rule()
            if (viaRule != null) {
                // write the via rule
                p_par.file.new_line()
                p_par.file.write("(via_rule ")
                p_par.identifier_type.write(viaRule.name, p_par.file)
                p_par.file.write(")")
            }

            // write the rules, if they are different from the default rule.
            Rule.write_scope(p_net_class, p_par)

            write_circuit(p_net_class, p_par)

            if (!p_net_class.get_pull_tight()) {
                p_par.file.new_line()
                p_par.file.write("(pull_tight off)")
            }

            if (p_net_class.is_shove_fixed()) {
                p_par.file.new_line()
                p_par.file.write("(shove_fixed on)")
            }

            p_par.file.end_scope()
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun write_circuit(p_net_class: app.freerouting.rules.NetClass, p_par: WriteScopeParameter) {
            val min_trace_length = p_net_class.get_minimum_trace_length()
            val max_trace_length = p_net_class.get_maximum_trace_length()
            p_par.file.start_scope()
            p_par.file.write("circuit ")
            p_par.file.new_line()
            p_par.file.write("(use_layer")
            val layer_count = p_net_class.layer_count()
            for (i in 0 until layer_count) {
                if (p_net_class.is_active_routing_layer(i)) {
                    p_par.file.write(" ")
                    p_par.file.write(p_par.board.layer_structure.arr[i].name)
                }
            }
            p_par.file.write(")")
            if (min_trace_length > 0 || max_trace_length > 0) {
                p_par.file.new_line()
                p_par.file.write("(length ")
                val transformed_max_length = if (max_trace_length <= 0) {
                    -1.0
                } else {
                    p_par.coordinate_transform.board_to_dsn(max_trace_length)
                }
                p_par.file.write(transformed_max_length.toString())
                p_par.file.write(" ")
                val transformed_min_length = if (min_trace_length <= 0) {
                    0.0
                } else {
                    p_par.coordinate_transform.board_to_dsn(min_trace_length)
                }
                p_par.file.write(transformed_min_length.toString())
                p_par.file.write(")")
            }
            p_par.file.end_scope()
        }

        @JvmStatic
        private fun create_ordered_subnets(p_pin_list: Collection<Net.Pin>): Collection<Collection<Net.Pin>> {
            val result = LinkedList<Collection<Net.Pin>>()
            if (p_pin_list.isEmpty()) {
                return result
            }

            val it = p_pin_list.iterator()
            var prev_pin = it.next()
            while (it.hasNext()) {
                val next_pin = it.next()
                val curr_subnet_pin_list = TreeSet<Net.Pin>()
                curr_subnet_pin_list.add(prev_pin)
                curr_subnet_pin_list.add(next_pin)
                result.add(curr_subnet_pin_list)
                prev_pin = next_pin
            }
            return result
        }

        @JvmStatic
        private fun read_net_pins(p_scanner: IJFlexScanner, p_pin_list: MutableCollection<Net.Pin>): Boolean {
            var next_token: Any?
            var component_name: String
            while (true) {
                component_name = (p_scanner as SpecctraDsnStreamReader).next_string(true, '-') ?: ""
                if (component_name.isEmpty()) {
                    break
                }
                try {
                    p_scanner.yybegin(SpecctraDsnStreamReader.SPEC_CHAR)
                    next_token = p_scanner.next_token() // overread the hyphen
                } catch (e: IOException) {
                    FRLogger.error("Network.read_net_pins: IO error while scanning file", e)
                    return false
                }

                val pin_name = p_scanner.next_string(true) ?: ""
                val curr_entry = Net.Pin(component_name, pin_name)
                p_pin_list.add(curr_entry)
            }

            try {
                next_token = p_scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("Network.read_net_pins: IO error scanning file", e)
                return false
            }
            if (next_token == null) {
                FRLogger.warn("Network.read_net_pins: unexpected end of file at '${p_scanner.get_scope_identifier()}'")
                return false
            }
            if (next_token !== CLOSED_BRACKET) {
                // not end of scope
                FRLogger.warn("Network.read_net_pins: expected closed bracket is missing at '${p_scanner.get_scope_identifier()}'")
            }

            return true
        }

        @JvmStatic
        fun read_via_info(p_scanner: IJFlexScanner, p_board: BasicBoard): ViaInfo? {
            try {
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                var next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("Network.read_via_info: string expected at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                val name = next_token
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("Network.read_via_info: string expected at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                val padstack_name = next_token
                p_scanner.set_scope_identifier(padstack_name)
                var via_padstack = p_board.library.get_via_padstack(padstack_name)
                if (via_padstack == null) {
                    // The padstack may not yet be inserted into the list of via padstacks
                    via_padstack = p_board.library.padstacks?.get(padstack_name)
                    if (via_padstack == null) {
                        FRLogger.warn("Network.read_via_info: padstack not found at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                    p_board.library.add_via_padstack(via_padstack)
                }
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("Network.read_via_info: string expected at '${p_scanner.get_scope_identifier()}'")
                    return null
                }
                var clearance_class = p_board.rules.clearance_matrix.get_no(next_token)
                if (clearance_class < 0) {
                    // Clearance class not stored, because it is identical to the default clearance class.
                    clearance_class = BoardRules.default_clearance_class()
                }
                var attach_allowed = false
                next_token = p_scanner.next_token()
                if (next_token !== Keyword.CLOSED_BRACKET) {
                    if (next_token !== Keyword.ATTACH) {
                        FRLogger.warn("Network.read_via_info: Keyword.ATTACH expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                    attach_allowed = true
                    next_token = p_scanner.next_token()
                    if (next_token !== Keyword.CLOSED_BRACKET) {
                        FRLogger.warn("Network.read_via_info: closing bracket expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                }
                return ViaInfo(name, via_padstack, clearance_class, attach_allowed, p_board.rules)
            } catch (e: IOException) {
                FRLogger.error("Network.read_via_info: IO error while scanning file", e)
                return null
            }
        }

        @JvmStatic
        fun read_via_rule(p_scanner: IJFlexScanner, p_board: BasicBoard): Collection<String>? {
            try {
                val result = LinkedList<String>()
                while (true) {
                    p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                    val next_token = p_scanner.next_token()
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        break
                    }
                    if (next_token !is String) {
                        FRLogger.warn("Network.read_via_rule: string expected at '${p_scanner.get_scope_identifier()}'")
                        return null
                    }
                    result.add(next_token)
                }
                return result
            } catch (e: IOException) {
                FRLogger.error("Network.read_via_rule: IO error while scanning file", e)
                return null
            }
        }

        @JvmStatic
        private fun insert_via_infos(p_via_infos: Collection<ViaInfo>, p_board: RoutingBoard, p_attach_allowed: Boolean) {
            if (p_via_infos.isNotEmpty()) {
                for (curr_info in p_via_infos) {
                    p_board.rules.via_infos.add(curr_info)
                }
            } else { // no via infos found, create default via infos from the via padstacks.
                create_default_via_infos(p_board, p_board.rules.get_default_net_class(), p_attach_allowed)
            }
        }

        @JvmStatic
        private fun create_default_via_infos(p_board: BasicBoard, p_net_class: app.freerouting.rules.NetClass, p_attach_allowed: Boolean) {
            val cl_class = p_net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.VIA)
            val is_default_class = p_net_class === p_board.rules.get_default_net_class()
            for (i in 0 until p_board.library.via_padstack_count()) {
                val curr_padstack = p_board.library.get_via_padstack(i) ?: continue
                val attach_allowed = p_attach_allowed && curr_padstack.attach_allowed
                val via_name = if (is_default_class) {
                    curr_padstack.name
                } else {
                    curr_padstack.name + DsnFile.CLASS_CLEARANCE_SEPARATOR + p_net_class.get_name()
                }
                val found_via_info = ViaInfo(via_name, curr_padstack, cl_class, attach_allowed, p_board.rules)
                p_board.rules.via_infos.add(found_via_info)
            }
        }

        @JvmStatic
        private fun insert_via_rules(p_via_rules: Collection<Collection<String>>, p_board: BasicBoard) {
            var rule_found = false
            for (curr_list in p_via_rules) {
                if (curr_list.size < 2) {
                    continue
                }
                if (add_via_rule(curr_list, p_board)) {
                    rule_found = true
                }
            }
            if (!rule_found) {
                p_board.rules.create_default_via_rule(p_board.rules.get_default_net_class(), "default")
            }
            for (i in 0 until p_board.rules.net_classes.count()) {
                p_board.rules.net_classes.get(i).set_via_rule(p_board.rules.get_default_via_rule())
            }
        }

        @JvmStatic
        fun add_via_rule(p_name_list: Collection<String>, p_board: BasicBoard): Boolean {
            val it = p_name_list.iterator()
            if (!it.hasNext()) return false
            val rule_name = it.next()
            val existing_rule = p_board.rules.get_via_rule(rule_name)
            val curr_rule = ViaRule(rule_name)
            var rule_ok = true
            while (it.hasNext()) {
                val curr_via = p_board.rules.via_infos.get(it.next())
                if (curr_via != null) {
                    curr_rule.append_via(curr_via)
                } else {
                    FRLogger.warn("Network.insert_via_rules: via_info not found")
                    rule_ok = false
                }
            }
            if (rule_ok) {
                if (existing_rule != null) {
                    // Replace already existing rule.
                    p_board.rules.via_rules.remove(existing_rule)
                }
                p_board.rules.via_rules.add(curr_rule)
            }
            return rule_ok
        }

        @JvmStatic
        private fun insert_net_classes(p_net_classes: Collection<NetClass>, p_par: ReadScopeParameter) {
            val routing_board = p_par.board_handling.get_routing_board() ?: return
            val coordinate_transform = p_par.coordinate_transform ?: return
            for (curr_class in p_net_classes) {
                insert_net_class(curr_class, p_par.layer_structure, routing_board, coordinate_transform, p_par.via_at_smd_allowed)
            }
        }

        @JvmStatic
        fun insert_net_class(
            p_class: NetClass,
            p_layer_structure: LayerStructure?,
            p_board: BasicBoard,
            p_coordinate_transform: CoordinateTransform,
            p_via_at_smd_allowed: Boolean
        ) {
            val board_net_class = p_board.rules.append_net_class(p_class.name)
            if (p_class.trace_clearance_class != null) {
                val trace_clearance_class = p_board.rules.clearance_matrix.get_no(p_class.trace_clearance_class)
                if (trace_clearance_class >= 0) {
                    board_net_class.set_trace_clearance_class(trace_clearance_class)
                } else {
                    FRLogger.warn("Network.insert_net_class: clearance class not found at '${board_net_class.get_name()}'")
                }
            }
            if (p_class.via_rule != null) {
                val via_rule = p_board.rules.get_via_rule(p_class.via_rule)
                if (via_rule != null) {
                    board_net_class.set_via_rule(via_rule)
                } else {
                    FRLogger.warn("Network.insert_net_class: via rule not found at '${board_net_class.get_name()}'")
                }
            }
            if (p_class.max_trace_length > 0) {
                board_net_class.set_maximum_trace_length(p_coordinate_transform.dsn_to_board(p_class.max_trace_length))
            }
            if (p_class.min_trace_length > 0) {
                board_net_class.set_minimum_trace_length(p_coordinate_transform.dsn_to_board(p_class.min_trace_length))
            }
            for (curr_net_name in p_class.net_list) {
                val curr_net_list = p_board.rules.nets.get(curr_net_name)
                for (curr_net in curr_net_list) {
                    curr_net.set_class(board_net_class)
                }
            }

            // read the trace width and clearance rules.
            var clearance_rule_found = false

            for (curr_rule in p_class.rules) {
                if (curr_rule is Rule.WidthRule) {
                    val trace_halfwidth = Math.round(p_coordinate_transform.dsn_to_board(curr_rule.value / 2)).toInt()
                    board_net_class.set_trace_half_width(trace_halfwidth)
                } else if (curr_rule is Rule.ClearanceRule) {
                    add_clearance_rule(p_board.rules.clearance_matrix, board_net_class, curr_rule, -1, p_coordinate_transform)
                    clearance_rule_found = true
                } else {
                    FRLogger.warn("Network.insert_net_class: rule type not yet implemented at '${board_net_class.get_name()}'")
                }
            }

            // read the layer dependent rules.
            for (curr_layer_rule in p_class.layer_rules) {
                for (curr_layer_name in curr_layer_rule.layer_names) {
                    val layer_no = p_board.layer_structure.get_no(curr_layer_name)
                    if (layer_no < 0) {
                        FRLogger.warn("Network.insert_net_class: layer not found at '${board_net_class.get_name()}'")
                        continue
                    }
                    for (curr_rule in curr_layer_rule.rules) {
                        if (curr_rule is Rule.WidthRule) {
                            val trace_halfwidth = Math.round(p_coordinate_transform.dsn_to_board(curr_rule.value / 2)).toInt()
                            board_net_class.set_trace_half_width(layer_no, trace_halfwidth)
                        } else if (curr_rule is Rule.ClearanceRule) {
                            add_clearance_rule(p_board.rules.clearance_matrix, board_net_class, curr_rule, layer_no, p_coordinate_transform)
                            clearance_rule_found = true
                        } else {
                            FRLogger.warn("Network.insert_net_class: layer rule type not yet implemented at '${board_net_class.get_name()}'")
                        }
                    }
                }
            }

            board_net_class.set_pull_tight(p_class.pull_tight)
            board_net_class.set_shove_fixed(p_class.shove_fixed)
            var via_infos_created = false

            if (clearance_rule_found && board_net_class !== p_board.rules.get_default_net_class()) {
                create_default_via_infos(p_board, board_net_class, p_via_at_smd_allowed)
                via_infos_created = true
            }

            if (p_class.use_via.isNotEmpty()) {
                create_via_rule(p_class.use_via, board_net_class, p_board, p_via_at_smd_allowed)
            } else if (via_infos_created) {
                p_board.rules.create_default_via_rule(board_net_class, board_net_class.get_name())
            }
            if (p_class.use_layer.isNotEmpty() && p_layer_structure != null) {
                create_active_trace_layers(p_class.use_layer, p_layer_structure, board_net_class)
            }
        }

        @JvmStatic
        private fun insert_class_pairs(p_class_classes: Collection<NetClass.ClassClass>, p_par: ReadScopeParameter) {
            val routing_board = p_par.board_handling.get_routing_board() ?: return
            val coordinate_transform = p_par.coordinate_transform ?: return
            for (curr_class_class in p_class_classes) {
                val it1 = curr_class_class.class_names.iterator()
                while (it1.hasNext()) {
                    val first_name = it1.next()
                    val first_class = routing_board.rules.net_classes.get(first_name)
                    if (first_class == null) {
                        FRLogger.warn("Network.insert_class_pairs: first class not found")
                    } else {
                        val it2 = it1
                        while (it2.hasNext()) {
                            val second_name = it2.next()
                            val second_class = routing_board.rules.net_classes.get(second_name)
                            if (second_class == null) {
                                FRLogger.warn("Network.insert_class_pairs: second class not found")
                            } else {
                                insert_class_pair_info(curr_class_class, first_class, second_class, routing_board, coordinate_transform)
                            }
                        }
                    }
                }
            }
        }

        @JvmStatic
        private fun insert_class_pair_info(
            p_class_class: NetClass.ClassClass,
            p_first_class: app.freerouting.rules.NetClass,
            p_second_class: app.freerouting.rules.NetClass,
            p_board: BasicBoard,
            p_coordinate_transform: CoordinateTransform
        ) {
            for (curr_rule in p_class_class.rules) {
                if (curr_rule is Rule.ClearanceRule) {
                    add_mixed_clearance_rule(p_board.rules.clearance_matrix, p_first_class, p_second_class, curr_rule, -1, p_coordinate_transform)
                } else {
                    FRLogger.warn("Network.insert_class_pair_info: unexpected rule")
                }
            }
            for (curr_layer_rule in p_class_class.layer_rules) {
                for (curr_layer_name in curr_layer_rule.layer_names) {
                    val layer_no = p_board.layer_structure.get_no(curr_layer_name)
                    if (layer_no < 0) {
                        FRLogger.warn("Network.insert_class_pair_info: layer not found at '$curr_layer_name'")
                        continue
                    }
                    for (curr_rule in curr_layer_rule.rules) {
                        if (curr_rule is Rule.ClearanceRule) {
                            add_mixed_clearance_rule(p_board.rules.clearance_matrix, p_first_class, p_second_class, curr_rule, layer_no, p_coordinate_transform)
                        } else {
                            FRLogger.warn("Network.insert_class_pair_info: unexpected layer rule type")
                        }
                    }
                }
            }
        }

        @JvmStatic
        private fun add_mixed_clearance_rule(
            p_clearance_matrix: ClearanceMatrix,
            p_first_class: app.freerouting.rules.NetClass,
            p_second_class: app.freerouting.rules.NetClass,
            p_clearance_rule: Rule.ClearanceRule,
            p_layer_no: Int,
            p_coordinate_transform: CoordinateTransform
        ) {
            val curr_clearance = Math.round(p_coordinate_transform.dsn_to_board(p_clearance_rule.value)).toInt()
            val first_class_name = p_first_class.get_name()
            var first_class_no = p_clearance_matrix.get_no(first_class_name)
            if (first_class_no < 0) {
                p_clearance_matrix.append_class(first_class_name)
                first_class_no = p_clearance_matrix.get_no(first_class_name)
            }
            val second_class_name = p_second_class.get_name()
            var second_class_no = p_clearance_matrix.get_no(second_class_name)
            if (second_class_no < 0) {
                p_clearance_matrix.append_class(second_class_name)
                second_class_no = p_clearance_matrix.get_no(second_class_name)
            }
            if (p_clearance_rule.clearance_class_pairs.isEmpty()) {
                if (p_layer_no < 0) {
                    p_clearance_matrix.set_value(first_class_no, second_class_no, curr_clearance)
                    p_clearance_matrix.set_value(second_class_no, first_class_no, curr_clearance)
                } else {
                    p_clearance_matrix.set_value(first_class_no, second_class_no, p_layer_no, curr_clearance)
                    p_clearance_matrix.set_value(second_class_no, first_class_no, p_layer_no, curr_clearance)
                }
            } else {
                for (curr_string in p_clearance_rule.clearance_class_pairs) {
                    val curr_pair = curr_string.split("_").toTypedArray()
                    if (curr_pair.size != 2) {
                        continue
                    }

                    var curr_first_class_no: Int
                    var curr_second_class_no: Int
                    for (i in 0..1) {
                        if (i == 0) {
                            curr_first_class_no = get_clearance_class(p_clearance_matrix, p_first_class, curr_pair[0])
                            curr_second_class_no = get_clearance_class(p_clearance_matrix, p_second_class, curr_pair[1])
                        } else {
                            curr_first_class_no = get_clearance_class(p_clearance_matrix, p_second_class, curr_pair[0])
                            curr_second_class_no = get_clearance_class(p_clearance_matrix, p_first_class, curr_pair[1])
                        }
                        if (p_layer_no < 0) {
                            p_clearance_matrix.set_value(curr_first_class_no, curr_second_class_no, curr_clearance)
                            p_clearance_matrix.set_value(curr_second_class_no, curr_first_class_no, curr_clearance)
                        } else {
                            p_clearance_matrix.set_value(curr_first_class_no, curr_second_class_no, p_layer_no, curr_clearance)
                            p_clearance_matrix.set_value(curr_second_class_no, curr_first_class_no, p_layer_no, curr_clearance)
                        }
                    }
                }
            }
        }

        @JvmStatic
        private fun create_default_clearance_classes(p_net_class: app.freerouting.rules.NetClass, p_clearance_matrix: ClearanceMatrix) {
            get_clearance_class(p_clearance_matrix, p_net_class, "via")
            get_clearance_class(p_clearance_matrix, p_net_class, "smd")
            get_clearance_class(p_clearance_matrix, p_net_class, "pin")
            get_clearance_class(p_clearance_matrix, p_net_class, "area")
        }

        @JvmStatic
        private fun create_via_rule(
            p_use_via: Collection<String>,
            p_net_class: app.freerouting.rules.NetClass,
            p_board: BasicBoard,
            p_attach_allowed: Boolean
        ) {
            val new_via_rule = ViaRule(p_net_class.get_name())
            val default_via_cl_class = p_net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.VIA)
            for (curr_via_name in p_use_via) {
                for (i in 0 until p_board.rules.via_infos.count()) {
                    val curr_via_info = p_board.rules.via_infos.get(i)
                    if (curr_via_info.get_clearance_class() == default_via_cl_class) {
                        if (curr_via_info.get_padstack().name == curr_via_name) {
                            new_via_rule.append_via(curr_via_info)
                        }
                    }
                }
            }
            p_board.rules.via_rules.add(new_via_rule)
            p_net_class.set_via_rule(new_via_rule)
        }

        @JvmStatic
        private fun create_active_trace_layers(p_use_layer: Collection<String>, p_layer_structure: LayerStructure, p_net_class: app.freerouting.rules.NetClass) {
            for (i in 0 until p_layer_structure.arr.size) {
                p_net_class.set_active_routing_layer(i, false)
            }
            for (cur_layer_name in p_use_layer) {
                val curr_no = p_layer_structure.get_no(cur_layer_name)
                p_net_class.set_active_routing_layer(curr_no, true)
            }
            // currently all inactive layers have tracewidth 0.
            for (i in 0 until p_layer_structure.arr.size) {
                if (!p_net_class.is_active_routing_layer(i)) {
                    p_net_class.set_trace_half_width(i, 0)
                }
            }
        }

        @JvmStatic
        private fun add_clearance_rule(
            p_clearance_matrix: ClearanceMatrix,
            p_net_class: app.freerouting.rules.NetClass,
            p_rule: Rule.ClearanceRule,
            p_layer_no: Int,
            p_coordinate_transform: CoordinateTransform
        ) {
            val curr_clearance = Math.round(p_coordinate_transform.dsn_to_board(p_rule.value)).toInt()
            val class_name = p_net_class.get_name()
            var class_no = p_clearance_matrix.get_no(class_name)
            if (class_no < 0) {
                // class not yet existing, create a new class
                p_clearance_matrix.append_class(class_name)
                class_no = p_clearance_matrix.get_no(class_name)
                // set the clearance values of the new class to the maximum of curr_clearance and the existing values.
                for (i in 1 until p_clearance_matrix.get_class_count()) {
                    for (j in 0 until p_clearance_matrix.get_layer_count()) {
                        val curr_value = Math.max(p_clearance_matrix.get_value(class_no, i, j, false), curr_clearance)
                        p_clearance_matrix.set_value(class_no, i, j, curr_value)
                        p_clearance_matrix.set_value(i, class_no, j, curr_value)
                    }
                }
                p_net_class.default_item_clearance_classes.set_all(class_no)
            }
            p_net_class.set_trace_clearance_class(class_no)
            if (p_rule.clearance_class_pairs.isEmpty()) {
                if (p_layer_no < 0) {
                    p_clearance_matrix.set_value(class_no, class_no, curr_clearance)
                } else {
                    p_clearance_matrix.set_value(class_no, class_no, p_layer_no, curr_clearance)
                }
                return
            }
            if (Structure.contains_wire_clearance_pair(p_rule.clearance_class_pairs)) {
                create_default_clearance_classes(p_net_class, p_clearance_matrix)
            }
            for (curr_string in p_rule.clearance_class_pairs) {
                val curr_pair = curr_string.split("_").toTypedArray()
                if (curr_pair.size != 2) {
                    continue
                }

                val first_class_no = get_clearance_class(p_clearance_matrix, p_net_class, curr_pair[0])
                val second_class_no = get_clearance_class(p_clearance_matrix, p_net_class, curr_pair[1])

                if (p_layer_no < 0) {
                    p_clearance_matrix.set_value(first_class_no, second_class_no, curr_clearance)
                    p_clearance_matrix.set_value(second_class_no, first_class_no, curr_clearance)
                } else {
                    p_clearance_matrix.set_value(first_class_no, second_class_no, p_layer_no, curr_clearance)
                    p_clearance_matrix.set_value(second_class_no, first_class_no, p_layer_no, curr_clearance)
                }
            }
        }

        @JvmStatic
        private fun get_clearance_class(p_clearance_matrix: ClearanceMatrix, p_net_class: app.freerouting.rules.NetClass, p_item_class_name: String): Int {
            val net_class_name = p_net_class.get_name()
            var new_class_name = net_class_name
            if ("wire" != p_item_class_name) {
                new_class_name = new_class_name + DsnFile.CLASS_CLEARANCE_SEPARATOR + p_item_class_name
            }
            val found_class_no = p_clearance_matrix.get_no(new_class_name)
            if (found_class_no >= 0) {
                return found_class_no
            }
            p_clearance_matrix.append_class(new_class_name)
            val result = p_clearance_matrix.get_no(new_class_name)
            val net_class_no = p_clearance_matrix.get_no(net_class_name)
            if (net_class_no < 0 || result < 0) {
                FRLogger.warn("Network.get_clearance_class: clearance class not found at '$net_class_name'")
                return result
            }
            // initialize the clearance values of p_new_class_name from p_net_class_name
            for (i in 1 until p_clearance_matrix.get_class_count()) {
                for (j in 0 until p_clearance_matrix.get_layer_count()) {
                    val curr_value = p_clearance_matrix.get_value(net_class_no, i, j, false)
                    p_clearance_matrix.set_value(result, i, j, curr_value)
                    p_clearance_matrix.set_value(i, result, j, curr_value)
                }
            }
            when (p_item_class_name) {
                "via" -> p_net_class.default_item_clearance_classes.set(ItemClass.VIA, result)
                "pin" -> p_net_class.default_item_clearance_classes.set(ItemClass.PIN, result)
                "smd" -> p_net_class.default_item_clearance_classes.set(ItemClass.SMD, result)
                "area" -> p_net_class.default_item_clearance_classes.set(ItemClass.AREA, result)
            }
            return result
        }

        @JvmStatic
        private fun insert_components(p_par: ReadScopeParameter) {
            for (next_lib_component in p_par.placement_list) {
                for (next_component in next_lib_component.locations) {
                    insert_component(next_component, next_lib_component.lib_name, p_par)
                }
            }
        }

        @JvmStatic
        private fun insert_logical_parts(p_par: ReadScopeParameter): Boolean {
            val routing_board = p_par.board_handling.get_routing_board() ?: return false
            for (next_part in p_par.logical_parts) {
                val lib_package = search_lib_package(next_part.name, p_par.logical_part_mappings, routing_board)
                    ?: return false
                val board_part_pins = arrayOfNulls<LogicalPart.PartPin>(next_part.part_pins.size)
                var curr_index = 0
                for (curr_part_pin in next_part.part_pins) {
                    val pin_no = lib_package.get_pin_no(curr_part_pin.pin_name)
                    if (pin_no < 0) {
                        FRLogger.warn("Network.insert_logical_parts: package pin not found at '${curr_part_pin.pin_name}'")
                        return false
                    }
                    board_part_pins[curr_index] = LogicalPart.PartPin(
                        pin_no,
                        curr_part_pin.pin_name,
                        curr_part_pin.gate_name,
                        curr_part_pin.gate_swap_code,
                        curr_part_pin.gate_pin_name,
                        curr_part_pin.gate_pin_swap_code
                    )
                    ++curr_index
                }
                routing_board.library.logical_parts.add(next_part.name, board_part_pins.requireNoNulls())
            }

            for (next_mapping in p_par.logical_part_mappings) {
                val curr_logical_part = routing_board.library.logical_parts.get(next_mapping.name)
                if (curr_logical_part == null) {
                    FRLogger.warn("Network.insert_logical_parts: logical part not found at '${next_mapping.name}'")
                }
                for (curr_cmp_name in next_mapping.components) {
                    val curr_component = routing_board.components.get(curr_cmp_name)
                    if (curr_component != null) {
                        curr_component.set_logical_part(curr_logical_part)
                    } else {
                        FRLogger.warn("Network.insert_logical_parts: board component not found at '$curr_cmp_name'")
                    }
                }
            }
            return true
        }

        @JvmStatic
        private fun search_lib_package(
            p_part_name: String,
            p_logical_part_mappings: Collection<PartLibrary.LogicalPartMapping>,
            p_board: BasicBoard
        ): Package? {
            for (curr_mapping in p_logical_part_mappings) {
                if (curr_mapping.name == p_part_name) {
                    if (curr_mapping.components.isEmpty()) {
                        FRLogger.warn("Network.search_lib_package: component list empty at '$p_part_name'")
                        return null
                    }
                    val component_name = curr_mapping.components.firstOrNull()
                    if (component_name == null) {
                        FRLogger.warn("Network.search_lib_package: component list empty at '$p_part_name'")
                        return null
                    }
                    val curr_component = p_board.components.get(component_name)
                    if (curr_component == null) {
                        FRLogger.warn("Network.search_lib_package: component not found at '$component_name'")
                        return null
                    }
                    return curr_component.get_package()
                }
            }
            FRLogger.warn("Network.search_lib_package: library package '$p_part_name' not found")
            return null
        }

        @JvmStatic
        private fun insert_component(p_location: ComponentPlacement.ComponentLocation, p_lib_key: String, p_par: ReadScopeParameter) {
            val routing_board = p_par.board_handling.get_routing_board() ?: return
            val coordinate_transform = p_par.coordinate_transform ?: return

            val curr_front_package = routing_board.library.packages?.get(p_lib_key, true)
            val curr_back_package = routing_board.library.packages?.get(p_lib_key, false)
            if (curr_front_package == null || curr_back_package == null) {
                FRLogger.warn("Network.insert_component: component package not found at '${p_par.scanner.get_scope_identifier()}'")
                return
            }

            val component_location = p_location.coor?.let { coordinate_transform.dsn_to_board(it).round() }
            val rotation_in_degree = p_location.rotation

            val new_component = routing_board.components.add(
                p_location.name,
                component_location,
                rotation_in_degree,
                p_location.is_front,
                curr_front_package,
                curr_back_package,
                p_location.position_fixed
            ) ?: return

            if (component_location == null) {
                return // component is not yet placed.
            }
            val component_translation = component_location.difference_by(Point.ZERO)
            val fixed_state = if (p_location.position_fixed) {
                FixedState.SYSTEM_FIXED
            } else {
                FixedState.UNFIXED
            }
            val curr_package = new_component.get_package()
            for (i in 0 until curr_package.pin_count()) {
                val curr_pin = curr_package.get_pin(i) ?: continue
                val curr_padstack = routing_board.library.padstacks?.get(curr_pin.padstack_no)
                if (curr_padstack == null) {
                    FRLogger.warn("Network.insert_component: pin padstack not found at '${p_par.scanner.get_scope_identifier()}'")
                    return
                }
                val pin_nets = p_par.netlist.get_nets(p_location.name, curr_pin.name)
                val net_numbers = LinkedList<Int>()
                for (curr_pin_net in pin_nets) {
                    val curr_board_net = routing_board.rules.nets.get(curr_pin_net.id.name, curr_pin_net.id.subnet_number)
                    if (curr_board_net == null) {
                        FRLogger.warn("Network.insert_component: board net not found at '${p_par.scanner.get_scope_identifier()}'")
                    } else {
                        net_numbers.add(curr_board_net.net_number)
                    }
                }
                val net_no_arr = net_numbers.toIntArray()
                val board_net = if (net_no_arr.isNotEmpty()) {
                    routing_board.rules.nets.get(net_no_arr[0])
                } else {
                    null
                }
                val net_class = board_net?.get_class() ?: routing_board.rules.get_default_net_class()
                var clearance_class = -1
                val pin_info = p_location.pin_infos.get(curr_pin.name)
                if (pin_info != null) {
                    clearance_class = routing_board.rules.clearance_matrix.get_no(pin_info.clearance_class)
                }
                if (clearance_class < 0) {
                    clearance_class = if (curr_padstack.from_layer() == curr_padstack.to_layer()) {
                        net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.SMD)
                    } else {
                        net_class.default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.PIN)
                    }
                }
                routing_board.insert_pin(new_component.no, i, net_no_arr, clearance_class, fixed_state)
            }

            // insert the keepouts belonging to the package (k = 1 for via keepouts)
            for (k in 0..2) {
                val keepout_arr: Array<Package.Keepout>
                val curr_keepout_infos: Map<String, ComponentPlacement.ItemClearanceInfo>
                when (k) {
                    0 -> {
                        keepout_arr = curr_package.keepout_arr
                        curr_keepout_infos = p_location.keepout_infos
                    }
                    1 -> {
                        keepout_arr = curr_package.via_keepout_arr
                        curr_keepout_infos = p_location.via_keepout_infos
                    }
                    else -> {
                        keepout_arr = curr_package.place_keepout_arr
                        curr_keepout_infos = p_location.place_keepout_infos
                    }
                }
                for (i in keepout_arr.indices) {
                    val curr_keepout = keepout_arr[i]
                    var layer = curr_keepout.layer
                    if (layer >= routing_board.get_layer_count()) {
                        FRLogger.warn("Network.insert_component: keepout layer is to big at '${p_par.scanner.get_scope_identifier()}'")
                        continue
                    }
                    if (layer >= 0 && !p_location.is_front) {
                        layer = routing_board.get_layer_count() - curr_keepout.layer - 1
                    }
                    var clearance_class = routing_board.rules.get_default_net_class().default_item_clearance_classes.get(DefaultItemClearanceClasses.ItemClass.AREA)
                    val keepout_info = curr_keepout_infos.get(curr_keepout.name)
                    if (keepout_info != null) {
                        val curr_clearance_class = routing_board.rules.clearance_matrix.get_no(keepout_info.clearance_class)
                        if (curr_clearance_class > 0) {
                            clearance_class = curr_clearance_class
                        }
                    }
                    if (layer >= 0) {
                        when (k) {
                            0 -> routing_board.insert_obstacle(curr_keepout.area, layer, component_translation, rotation_in_degree, !p_location.is_front, clearance_class, new_component.no, curr_keepout.name, fixed_state)
                            1 -> routing_board.insert_via_obstacle(curr_keepout.area, layer, component_translation, rotation_in_degree, !p_location.is_front, clearance_class, new_component.no, curr_keepout.name, fixed_state)
                            else -> routing_board.insert_component_obstacle(curr_keepout.area, layer, component_translation, rotation_in_degree, !p_location.is_front, clearance_class, new_component.no, curr_keepout.name, fixed_state)
                        }
                    } else {
                        // insert the obstacle on all signal layers
                        for (j in routing_board.layer_structure.arr.indices) {
                            if (routing_board.layer_structure.arr[j].is_signal) {
                                when (k) {
                                    0 -> routing_board.insert_obstacle(curr_keepout.area, j, component_translation, rotation_in_degree, !p_location.is_front, clearance_class, new_component.no, curr_keepout.name, fixed_state)
                                    1 -> routing_board.insert_via_obstacle(curr_keepout.area, j, component_translation, rotation_in_degree, !p_location.is_front, clearance_class, new_component.no, curr_keepout.name, fixed_state)
                                    else -> routing_board.insert_component_obstacle(curr_keepout.area, j, component_translation, rotation_in_degree, !p_location.is_front, clearance_class, new_component.no, curr_keepout.name, fixed_state)
                                }
                            }
                        }
                    }
                }
            }
            // insert the outline as component keepout
            val outline = curr_package.outline
            if (outline != null) {
                for (i in outline.indices) {
                    routing_board.insert_component_outline(outline[i], p_location.is_front, component_translation, rotation_in_degree, new_component.no, fixed_state)
                }
            }
        }
    }
}
