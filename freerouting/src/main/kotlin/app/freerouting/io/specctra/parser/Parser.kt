package app.freerouting.io.specctra.parser

import app.freerouting.board.Communication.SpecctraParserInfo
import app.freerouting.datastructures.IdentifierType
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.logger.FRLogger
import java.io.IOException

/**
 * Class for reading and writing parser scopes from dsn-files.
 */
class Parser : ScopeKeyword("parser") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        var next_token: Any? = null
        while (true) {
            val prev_token = next_token
            try {
                next_token = p_par.scanner.next_token()
            } catch (_: IOException) {
                FRLogger.warn("Parser.read_scope: IO error scanning file at '" + p_par.scanner.get_scope_identifier() + "'")
                return false
            }
            if (next_token == null) {
                FRLogger.warn("Parser.read_scope: unexpected end of file at '" + p_par.scanner.get_scope_identifier() + "'")
                return false
            }
            if (next_token === CLOSED_BRACKET) {
                // end of scope
                break
            }
            val read_ok = true
            if (prev_token === OPEN_BRACKET) {
                if (next_token === STRING_QUOTE) {
                    val quote_char = read_quote_char(p_par.scanner) ?: return false
                    p_par.string_quote = quote_char
                } else if (next_token === HOST_CAD) {
                    p_par.host_cad = DsnFile.read_string_scope(p_par.scanner)
                } else if (next_token === HOST_VERSION) {
                    p_par.host_version = DsnFile.read_string_scope(p_par.scanner)
                } else if (next_token === CONSTANT) {
                    val curr_constant = read_constant(p_par)
                    if (curr_constant != null) {
                        p_par.constants.add(curr_constant)
                    }
                } else if (next_token === WRITE_RESOLUTION) {
                    p_par.write_resolution = read_write_solution(p_par)
                } else if (next_token === GENERATED_BY_FREEROUTING) {
                    p_par.dsn_file_generated_by_host = false
                    // skip the closing bracket
                    skip_scope(p_par.scanner)
                } else {
                    skip_scope(p_par.scanner)
                }
            }
            if (!read_ok) {
                return false
            }
        }
        return true
    }

    companion object {
        @JvmStatic
        private fun read_write_solution(p_par: ReadScopeParameter): SpecctraParserInfo.WriteResolution? {
            try {
                var next_token = p_par.scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("Parser.read_write_solution: string expected at '" + p_par.scanner.get_scope_identifier() + "'")
                    return null
                }
                val resolution_string = next_token
                next_token = p_par.scanner.next_token()
                if (next_token !is Int) {
                    FRLogger.warn("Parser.read_write_solution: integer expected expected at '" + p_par.scanner.get_scope_identifier() + "'")
                    return null
                }
                val resolution_value = next_token
                next_token = p_par.scanner.next_token()
                if (next_token !== CLOSED_BRACKET) {
                    FRLogger.warn("Parser.read_write_solution: closing_bracket expected at '" + p_par.scanner.get_scope_identifier() + "'")
                    return null
                }
                return SpecctraParserInfo.WriteResolution(resolution_string, resolution_value)
            } catch (e: IOException) {
                FRLogger.error("Parser.read_write_solution: IO error scanning file", e)
                return null
            }
        }

        @JvmStatic
        private fun read_constant(p_par: ReadScopeParameter): Array<String>? {
            try {
                val result = arrayOfNulls<String>(2)
                p_par.scanner.yybegin(SpecctraDsnStreamReader.NAME)
                var next_token = p_par.scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("Parser.read_constant: string expected at '" + p_par.scanner.get_scope_identifier() + "'")
                    return null
                }
                result[0] = next_token
                p_par.scanner.yybegin(SpecctraDsnStreamReader.NAME)
                next_token = p_par.scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("Parser.read_constant: string expected at '" + p_par.scanner.get_scope_identifier() + "'")
                    return null
                }
                result[1] = next_token
                next_token = p_par.scanner.next_token()
                if (next_token !== CLOSED_BRACKET) {
                    FRLogger.warn("Parser.read_constant: closing_bracket expected at '" + p_par.scanner.get_scope_identifier() + "'")
                    return null
                }
                return Array(2) { i -> result[i]!! }
            } catch (e: IOException) {
                FRLogger.error("Parser.read_constant: IO error scanning file", e)
                return null
            }
        }

        /**
         * p_reduced is true if the scope is written to a session file.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(
            p_file: IndentFileWriter,
            p_parser_info: SpecctraParserInfo,
            p_identifier_type: IdentifierType,
            p_reduced: Boolean
        ) {
            p_file.start_scope()
            p_file.write("parser")
            if (!p_reduced) {
                p_file.new_line()
                p_file.write("(string_quote ")
                p_file.write(p_parser_info.string_quote)
                p_file.write(")")
                p_file.new_line()
                p_file.write("(space_in_quoted_tokens on)")
            }
            if (p_parser_info.host_cad != null) {
                p_file.new_line()
                p_file.write("(host_cad ")
                p_identifier_type.write(p_parser_info.host_cad, p_file)
                p_file.write(")")
            }
            if (p_parser_info.host_version != null) {
                p_file.new_line()
                p_file.write("(host_version ")
                p_identifier_type.write(p_parser_info.host_version, p_file)
                p_file.write(")")
            }
            if (p_parser_info.constants != null) {
                for (curr_constant in p_parser_info.constants) {
                    p_file.new_line()
                    p_file.write("(constant ")
                    for (i in curr_constant.indices) {
                        p_identifier_type.write(curr_constant[i], p_file)
                        p_file.write(" ")
                    }
                    p_file.write(")")
                }
            }
            if (p_parser_info.write_resolution != null) {
                p_file.new_line()
                p_file.write("(write_resolution ")
                p_file.write(p_parser_info.write_resolution.char_name.substring(0, 1))
                p_file.write(" ")
                val positive_int = p_parser_info.write_resolution.positive_int
                p_file.write(positive_int.toString())
                p_file.write(")")
            }
            if (!p_reduced) {
                p_file.new_line()
                p_file.write("(generated_by_freerouting)")
            }
            p_file.end_scope()
        }

        @JvmStatic
        private fun read_quote_char(p_scanner: IJFlexScanner): String? {
            try {
                var next_token = p_scanner.next_token()
                if (next_token !is String) {
                    FRLogger.warn("Parser.read_quote_char: string expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                val result = next_token
                next_token = p_scanner.next_token()
                if (next_token !== CLOSED_BRACKET) {
                    FRLogger.warn("Parser.read_quote_char: closing bracket expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                return result
            } catch (e: IOException) {
                FRLogger.error("Parser.read_quote_char: IO error scanning file", e)
                return null
            }
        }
    }
}
