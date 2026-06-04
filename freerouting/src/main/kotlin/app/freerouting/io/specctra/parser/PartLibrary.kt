package app.freerouting.io.specctra.parser

import app.freerouting.board.Component
import app.freerouting.core.LogicalParts
import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.LinkedList
import java.util.SortedSet
import java.util.TreeSet

class PartLibrary : ScopeKeyword("part_library") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            try {
                next_token = p_par.scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("PartLibrary.read_scope: IO error scanning file", e)
                return false
            }
            if (next_token == null) {
                FRLogger.warn("PartLibrary.read_scope: unexpected end of file at '" + p_par.scanner.get_scope_identifier() + "'")
                return false
            }
            if (next_token === CLOSED_BRACKET) {
                // end of scope
                break
            }
            if (prev_token === OPEN_BRACKET) {
                if (next_token === LOGICAL_PART_MAPPING) {
                    val next_mapping = read_logical_part_mapping(p_par.scanner) ?: return false
                    p_par.logical_part_mappings.add(next_mapping)
                } else if (next_token === LOGICAL_PART) {
                    val next_part = read_logical_part(p_par.scanner) ?: return false
                    p_par.logical_parts.add(next_part)
                } else {
                    skip_scope(p_par.scanner)
                }
            }
        }
        return true
    }

    class LogicalPartMapping(
        @JvmField val name: String,
        @JvmField val components: SortedSet<String>
    )

    class PartPin(
        @JvmField val pin_name: String,
        @JvmField val gate_name: String,
        @JvmField val gate_swap_code: Int,
        @JvmField val gate_pin_name: String,
        @JvmField val gate_pin_swap_code: Int
    )

    class LogicalPart(
        @JvmField val name: String,
        @JvmField val part_pins: Collection<PartPin>
    )

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_par: WriteScopeParameter) {
            val logical_parts = p_par.board.library.logical_parts ?: return
            if (logical_parts.count() <= 0) {
                return
            }
            p_par.file.start_scope()
            p_par.file.write("part_library")

            // write the logical part mappings

            for (i in 1..logical_parts.count()) {
                val curr_part = logical_parts.get(i) ?: continue
                p_par.file.start_scope()
                p_par.file.write("logical_part_mapping ")
                p_par.identifier_type.write(curr_part.name, p_par.file)
                p_par.file.new_line()
                p_par.file.write("(comp")
                for (j in 1..p_par.board.components.count()) {
                    val curr_component = p_par.board.components.get(j) ?: continue
                    if (curr_component.get_logical_part() == curr_part) {
                        p_par.file.write(" ")
                        p_par.file.write(curr_component.name)
                    }
                }
                p_par.file.write(")")
                p_par.file.end_scope()
            }

            // write the logical parts.

            for (i in 1..logical_parts.count()) {
                val curr_part = logical_parts.get(i) ?: continue

                p_par.file.start_scope()
                p_par.file.write("logical_part ")
                p_par.identifier_type.write(curr_part.name, p_par.file)
                p_par.file.new_line()
                for (j in 0 until curr_part.pin_count()) {
                    p_par.file.new_line()
                    val curr_pin = curr_part.get_pin(j) ?: continue
                    p_par.file.write("(pin ")
                    p_par.identifier_type.write(curr_pin.pin_name, p_par.file)
                    p_par.file.write(" 0 ")
                    p_par.identifier_type.write(curr_pin.gate_name, p_par.file)
                    p_par.file.write(" ")
                    val gate_swap_code = curr_pin.gate_swap_code
                    p_par.file.write(gate_swap_code.toString())
                    p_par.file.write(" ")
                    p_par.identifier_type.write(curr_pin.gate_pin_name, p_par.file)
                    p_par.file.write(" ")
                    val gate_pin_swap_code = curr_pin.gate_pin_swap_code
                    p_par.file.write(gate_pin_swap_code.toString())
                    p_par.file.write(")")
                }
                p_par.file.end_scope()
            }
            p_par.file.end_scope()
        }

        @JvmStatic
        private fun read_logical_part_mapping(p_scanner: IJFlexScanner): LogicalPartMapping? {
            try {
                var next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("PartLibrary.read_logical_part_mapping: string expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val name = next_token
                next_token = p_scanner.next_token()
                if (next_token !== OPEN_BRACKET) {
                    FRLogger.warn("PartLibrary.read_logical_part_mapping: open bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                next_token = p_scanner.next_token()
                if (next_token !== COMPONENT_SCOPE) {
                    FRLogger.warn("PartLibrary.read_logical_part_mapping: Keyword.COMPONENT_SCOPE expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val result = TreeSet<String>()
                while (true) {
                    p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                    next_token = p_scanner.next_token()
                    if (next_token === CLOSED_BRACKET) {
                        break
                    }
                    if (next_token !is String) {
                        FRLogger.warn("PartLibrary.read_logical_part_mapping: string expected at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                    result.add(next_token)
                }
                next_token = p_scanner.next_token()
                if (next_token !== CLOSED_BRACKET) {
                    FRLogger.warn("PartLibrary.read_logical_part_mapping: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                return LogicalPartMapping(name, result)
            } catch (e: IOException) {
                FRLogger.error("PartLibrary.read_logical_part_mapping: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        private fun read_logical_part(p_scanner: IJFlexScanner): LogicalPart? {
            val part_pins = LinkedList<PartPin>()
            var next_token: Any?
            try {
                next_token = p_scanner.next_token()
            } catch (e: IOException) {
                FRLogger.error("PartLibrary.read_logical_part: IO error scanning file", e)
                return null
            }
            if (next_token !is String) {
                FRLogger.warn("PartLibrary.read_logical_part: string expected at '" + p_scanner.get_scope_identifier() + "'")
                return null
            }
            val part_name = next_token
            p_scanner.set_scope_identifier(part_name)
            while (true) {
                val prev_token = next_token
                try {
                    next_token = p_scanner.next_token()
                } catch (e: IOException) {
                    FRLogger.error("PartLibrary.read_logical_part: IO error scanning file", e)
                    return null
                }
                if (next_token == null) {
                    FRLogger.warn("PartLibrary.read_logical_part: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                if (next_token === CLOSED_BRACKET) {
                    // end of scope
                    break
                }
                val read_ok = true
                if (prev_token === OPEN_BRACKET) {
                    if (next_token === PIN) {
                        val curr_part_pin = read_part_pin(p_scanner) ?: return null
                        part_pins.add(curr_part_pin)
                    } else {
                        skip_scope(p_scanner)
                    }
                }
                if (!read_ok) {
                    return null
                }
            }
            return LogicalPart(part_name, part_pins)
        }

        @JvmStatic
        private fun read_part_pin(p_scanner: IJFlexScanner): PartPin? {
            try {
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                var next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("PartLibrary.read_part_pin: string expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val pin_name = next_token
                p_scanner.set_scope_identifier(pin_name)
                next_token = p_scanner.next_token()
                if (next_token !is Int) {
                    FRLogger.warn("PartLibrary.read_part_pin: integer expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("PartLibrary.read_part_pin: string expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val gate_name = next_token
                p_scanner.set_scope_identifier(gate_name)
                next_token = p_scanner.next_token()
                if (next_token !is Int) {
                    FRLogger.warn("PartLibrary.read_part_pin: integer expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val gate_swap_code = next_token
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("PartLibrary.read_part_pin: string expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val gate_pin_name = next_token
                p_scanner.set_scope_identifier(gate_pin_name)
                next_token = p_scanner.next_token()
                if (next_token !is Int) {
                    FRLogger.warn("PartLibrary.read_part_pin: integer expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val gate_pin_swap_code = next_token
                // overread subgates
                do {
                    next_token = p_scanner.next_token()
                } while (next_token !== CLOSED_BRACKET)
                return PartPin(pin_name, gate_name, gate_swap_code, gate_pin_name, gate_pin_swap_code)
            } catch (e: IOException) {
                FRLogger.error("PartLibrary.read_part_pin: IO error scanning file", e)
                return null
            }
        }
    }
}
