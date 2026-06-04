package app.freerouting.io.specctra.parser

import app.freerouting.board.Communication
import app.freerouting.board.Unit
import app.freerouting.datastructures.IndentFileWriter
import app.freerouting.logger.FRLogger
import java.io.IOException

/**
 * Class for reading resolution scopes from dsn-files.
 */
class Resolution : ScopeKeyword("resolution") {

    override fun read_scope(p_par: ReadScopeParameter): Boolean {
        try {
            // read the unit
            var next_token: Any? = p_par.scanner.next_token()
            if (next_token !is String) {
                FRLogger.warn("Resolution.read_scope: string expected at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            p_par.unit = Unit.from_string(next_token)
            if (p_par.unit == null) {
                FRLogger.warn("Resolution.read_scope: unit mil, inch or mm expected at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            // read the scale factor
            next_token = p_par.scanner.next_token()
            if (next_token !is Int) {
                FRLogger.warn("Resolution.read_scope: integer expected at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            p_par.resolution = next_token
            // overread the closing bracket
            next_token = p_par.scanner.next_token()
            if (next_token !== Keyword.CLOSED_BRACKET) {
                FRLogger.warn("Resolution.read_scope: closing bracket expected at '${p_par.scanner.get_scope_identifier()}'")
                return false
            }
            return true
        } catch (e: IOException) {
            FRLogger.error("Resolution.read_scope: IO error scanning file", e)
            return false
        }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun write_scope(p_file: IndentFileWriter, p_board_communication: Communication) {
            p_file.new_line()
            p_file.write("(resolution ")
            p_file.write(p_board_communication.unit.toString())
            p_file.write(" ")
            p_file.write(p_board_communication.resolution.toString())
            p_file.write(")")
        }
    }
}
